package com.university.timetable.importer

import com.university.timetable.data.entity.Course
import com.university.timetable.data.entity.CourseSession
import com.university.timetable.data.entity.PeriodTime
import com.university.timetable.data.entity.WeekParity
import com.university.timetable.data.entity.startMinutes

/**
 * 兰州大学教务系统课表解析器（jwxt / 清华教育技术研究所模板）。
 *
 * 适用页面：`https://jwk.lzu.edu.cn/academic/manager/coursearrange/showTimetable.do?...`
 * （以及「大节课表」`sectionType=COMBINE` 版本）
 *
 * 页面结构（实测）：
 * ```
 * <table id="timetable">
 *   <tr><th>&nbsp;</th><th>周一</th>...<th>周日</th></tr>
 *   <tr>
 *     <th>第1节<br>08:30<br>┆<br>09:15</th>
 *     <td id="1-1">&lt;&lt;高阶英语1&gt;&gt;;21<br>天山堂A212<br>史雄波<br>4-19周全周<br>讲课学时</td>
 *     ...
 *   </tr>
 *   ...
 * </table>
 * ```
 * 单元格格式：`<<课程名>>;课序号` / `地点` / `教师` / `4-19周全周` / `讲课学时`
 * （同一格内可能有多门课，用 `<br>` 分隔的重复组）
 *
 * 另外还有 `#noArrangement` 表格，列出「没有具体上课时间或地点」的课程。
 */
object LzuTimetableParser {

    /** 解析结果：课程 + 从表头解析出的作息时间。 */
    data class LzuResult(
        val result: ImportResult,
        /** 表头里的每节起止时间，可用于同步作息设置 */
        val periodTimes: List<PeriodTime>
    )

    // 单元格 id：`天-节`，如 1-1 表示周一第 1 节
    private val cellIdRegex = Regex("""id\s*=\s*["']?(\d+)-(\d+)["']?""", RegexOption.IGNORE_CASE)
    private val rowRegex = Regex("""<tr[\s\S]*?</tr>""", RegexOption.IGNORE_CASE)
    private val thRegex = Regex("""<th[\s\S]*?</th>""", RegexOption.IGNORE_CASE)
    private val tdRegex = Regex("""<t[dh][\s\S]*?</t[dh]>""", RegexOption.IGNORE_CASE)
    private val brRegex = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
    private val tagRegex = Regex("""<[^>]+>""")
    private val scriptRegex = Regex("""<(script|style)[\s\S]*?</\1>""", RegexOption.IGNORE_CASE)
    private val periodHeaderRegex = Regex("""第\s*(\d+)\s*节""")
    private val lunchHeaderRegex = Regex("""中午\s*(\d+)\s*节""")
    private val timeRegex = Regex("""(\d{1,2})\s*[:：]\s*(\d{2})""")
    private val courseNameRegex = Regex("""<<([^>]+)>>""")
    private val weekRegex = Regex("""(\d+)\s*[-~－—]\s*(\d+)\s*周\s*(全周|单周|双周)?""")
    private val singleWeekRegex = Regex("""(\d+)\s*周\s*(全周|单周|双周)?""")

    /** 判断是否是兰州大学教务课表页面。 */
    fun looksLikeLzuTimetable(html: String): Boolean {
        val hasTable = html.contains("id=\"timetable\"", ignoreCase = true) ||
            html.contains("id='timetable'", ignoreCase = true) ||
            html.contains("id=timetable", ignoreCase = true)
        val hasCell = cellIdRegex.containsMatchIn(html) && courseNameRegex.containsMatchIn(html)
        return hasTable || hasCell
    }

    /** 解析整页 HTML。 */
    fun parse(html: String, sourceLabel: String = "兰州大学教务课表"): LzuResult {
        val warnings = mutableListOf<String>()
        val clean = scriptRegex.replace(html, " ")

        val periods = parsePeriodTimes(clean)
        val courses = parseTimetableCells(clean, warnings)
        val unattended = parseNoArrangement(clean, warnings)

        // 先合并同一格内的连续节次，再按课程名合并成同一门课程
        val all = consolidateByName(mergeSameCourses(courses + unattended))

        if (all.isEmpty()) {
            warnings += "未在页面中识别到课表，请确认已打开「本学期课表」页面"
        }

        return LzuResult(
            result = ImportResult(
                courses = all,
                warnings = warnings,
                source = sourceLabel
            ),
            periodTimes = periods
        )
    }

    // ------------------------------------------------------------------
    // 节次时间
    // ------------------------------------------------------------------

    /** 从表头「第1节<br>08:30<br>┆<br>09:15」解析每节起止时间。 */
    private fun parsePeriodTimes(html: String): List<PeriodTime> {
        val table = extractTimetableTable(html) ?: return emptyList()
        val rows = rowRegex.findAll(table).toList()
        val periods = mutableListOf<PeriodTime>()
        var headerIndex = 0

        rows.forEach { row ->
            val th = thRegex.find(row.value)?.value ?: return@forEach
            val text = toPlainText(th)

            // 只有「第N节」或「中午N节」的表头才算节次行（避免把表头行/说明行算进来）
            val isPeriodHeader = periodHeaderRegex.containsMatchIn(text) ||
                lunchHeaderRegex.containsMatchIn(text)
            if (!isPeriodHeader) return@forEach

            val times = timeRegex.findAll(text).map {
                "%02d:%02d".format(
                    it.groupValues[1].toIntOrNull() ?: 0,
                    it.groupValues[2].toIntOrNull() ?: 0
                )
            }.toList()
            if (times.size < 2) return@forEach

            // 行序即节次序号（与单元格 id "天-节" 一致）
            headerIndex++
            val section = when {
                headerIndex >= 9 -> 3
                headerIndex >= 5 -> 2
                else -> 1
            }
            periods += PeriodTime(
                period = headerIndex,
                startTime = times.first(),
                endTime = times.last(),
                section = section
            )
        }
        return periods
    }

    /** 「第1节」→ 1；「中午1节」→ 5（兰大中午两节顺延为第 5、6 节）。 */
    private fun periodNumber(headerText: String): Int? {
        periodHeaderRegex.find(headerText)?.let {
            return it.groupValues[1].toIntOrNull()
        }
        lunchHeaderRegex.find(headerText)?.let {
            val n = it.groupValues[1].toIntOrNull() ?: return null
            // 中午 1 节 → 5，中午 2 节 → 6
            return 4 + n
        }
        return null
    }

    // ------------------------------------------------------------------
    // 课表格子
    // ------------------------------------------------------------------

    private fun parseTimetableCells(html: String, warnings: MutableList<String>): List<ImportedCourse> {
        val table = extractTimetableTable(html) ?: return emptyList()
        val out = mutableListOf<ImportedCourse>()

        rowRegex.findAll(table).forEach { row ->
            // 每行的节次由该行第一个 th 决定
            val headerText = thRegex.find(row.value)?.value?.let { toPlainText(it) } ?: return@forEach
            val period = periodNumber(headerText) ?: return@forEach

            tdRegex.findAll(row.value).forEach { cell ->
                val cellHtml = cell.value
                val idMatch = cellIdRegex.find(cellHtml) ?: return@forEach
                val day = idMatch.groupValues[1].toIntOrNull() ?: return@forEach
                val cellPeriod = idMatch.groupValues[2].toIntOrNull() ?: period
                if (day !in 1..7) return@forEach

                val text = toPlainText(cellHtml)
                if (text.isBlank()) return@forEach

                parseCellEntries(text).forEach { entry ->
                    out += ImportedCourse(
                        course = Course(name = entry.name, teacher = entry.teacher),
                        sessions = listOf(
                            CourseSession(
                                courseId = 0L,
                                dayOfWeek = day,
                                startPeriod = cellPeriod,
                                endPeriod = cellPeriod,
                                location = entry.location,
                                startWeek = entry.startWeek,
                                endWeek = entry.endWeek,
                                weekParity = entry.parity
                            )
                        )
                    )
                }
            }
        }

        if (out.isEmpty()) warnings += "课表表格中没有课程数据"
        return out
    }

    /**
     * 节次表头去重。
     *
     * 兰大课表里同一节课会出现两次表头：`中午1节 12:20-13:15` 与
     * `第5节 14:30-15:15` 都归到第 5 节（中午两节顺延为第 5、6 节）。
     * 解析时按「行序 = 节次序号」处理，因此这里只要保证每个节次唯一，
     * 并优先保留带「第N节」的表头时间。
     */
    private fun dedupePeriods(items: List<PeriodTime>): List<PeriodTime> {
        val byPeriod = linkedMapOf<Int, PeriodTime>()
        items.sortedBy { it.period }.forEach { p ->
            val existing = byPeriod[p.period]
            if (existing == null) {
                byPeriod[p.period] = p
            } else {
                // 已有则保留开始时间更早的那条（中午两节早于下午第 5 节）
                if (p.startMinutes < existing.startMinutes) byPeriod[p.period] = p
            }
        }
        return byPeriod.values.sortedBy { it.period }
    }

    /** 单元格内的一条课程信息。 */
    private data class CellEntry(
        val name: String,
        val teacher: String?,
        val location: String?,
        val startWeek: Int,
        val endWeek: Int,
        val parity: Int
    )

    /**
     * 解析单元格纯文本，例如：
     * ```
     * <<计算机基础与C语言>>;1
     * 秦岭堂A312
     * 杨志鹏
     * 4-8周全周
     * 讲课学时
     * <<计算机基础与C语言>>;1
     * 秦岭堂B106
     * 杨志鹏
     * 9-19周全周
     * 讲课学时
     * ```
     * 同一格内多门课时会重复「课程名/地点/教师/周次/类型」这个 5 行组合。
     */
    private fun parseCellEntries(text: String): List<CellEntry> {
        val lines = text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return emptyList()

        val entries = mutableListOf<CellEntry>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val nameMatch = courseNameRegex.find(line)
            if (nameMatch == null) {
                i++
                continue
            }
            val name = nameMatch.groupValues[1].trim()
            // 同一个课程名在同一格里可能因为不同周次重复出现，这里逐组读取
            val group = mutableListOf<String>()
            i++
            while (i < lines.size && courseNameRegex.find(lines[i]) == null) {
                group += lines[i]
                i++
            }

            val weekLine = group.firstOrNull { weekRegex.containsMatchIn(it) || singleWeekRegex.containsMatchIn(it) }
            val week = parseWeek(weekLine)

            // 地点：优先含地点关键词的行；否则取第一行「不像教师名」的
            val location = group.firstOrNull { ScheduleSemantics.isLocation(it) }
                ?: group.firstOrNull { it.length in 2..30 && !looksLikePersonName(it) }
            // 教师：去掉地点与周次后的第一行
            val teacher = group.firstOrNull {
                it != location && !(weekLine != null && it == weekLine) &&
                    looksLikePersonName(it)
            }

            entries += CellEntry(
                name = name,
                teacher = teacher,
                location = location,
                startWeek = week.first,
                endWeek = week.second,
                parity = week.third
            )
        }
        return entries
    }

    /** 教师名：2-4 个汉字，或含逗号的多个姓名。 */
    private fun looksLikePersonName(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty() || t.length > 24) return false
        if (ScheduleSemantics.isWeek(t)) return false
        if (t.contains("学时") || t.contains("讲课") || t.contains("实验")) return false
        // 多个教师用逗号分隔
        val parts = t.split(',', '，', '、', '/')
        return parts.all { part ->
            val p = part.trim()
            p.isNotEmpty() && p.length in 2..5 && p.all { ch -> ch.code in 0x4E00..0x9FFF }
        }
    }

    /** 「4-19周全周」/「4-18周双周」/「6-15周单周」/「4-19周」 */
    private fun parseWeek(text: String?): Triple<Int, Int, Int> {
        if (text.isNullOrBlank()) return Triple(1, 20, WeekParity.EVERY)
        val parity = when {
            text.contains("单") -> WeekParity.ODD
            text.contains("双") -> WeekParity.EVEN
            else -> WeekParity.EVERY
        }
        weekRegex.find(text)?.let {
            val a = it.groupValues[1].toIntOrNull() ?: 1
            val b = it.groupValues[2].toIntOrNull() ?: 20
            return Triple(minOf(a, b), maxOf(a, b), parity)
        }
        singleWeekRegex.find(text)?.let {
            val w = it.groupValues[1].toIntOrNull() ?: 1
            return Triple(w, w, parity)
        }
        return Triple(1, 20, parity)
    }

    // ------------------------------------------------------------------
    // 「没有具体上课时间或地点」的课程
    // ------------------------------------------------------------------

    private fun parseNoArrangement(html: String, warnings: MutableList<String>): List<ImportedCourse> {
        val start = html.indexOf("id=\"noArrangement\"", ignoreCase = true)
        if (start < 0) return emptyList()
        val chunk = html.substring(start, minOf(html.length, start + 20000))
        val end = chunk.indexOf("</table>", ignoreCase = true)
        val table = if (end > 0) chunk.substring(0, end) else chunk

        val out = mutableListOf<ImportedCourse>()
        var skipped = 0
        rowRegex.findAll(table).forEach { row ->
            val tds = tdRegex.findAll(row.value).map { toPlainText(it.value) }.toList()
            if (tds.size < 8) return@forEach
            // 课程号, 课程名称, 课序号, 任课教师, 合班, 上课周次, 星期, 上课地点
            val name = tds[1].trim()
            if (name.isBlank()) return@forEach
            val teacher = tds[3].trim().ifBlank { null }
            val week = parseWeek(tds[5])
            val dayText = tds[6].trim()
            val location = tds[7].trim().ifBlank { null }
            val day = parseDay(dayText)
            if (day == null) {
                // 没有星期信息，无法排入课表
                skipped++
                return@forEach
            }
            out += ImportedCourse(
                course = Course(name = name, teacher = teacher),
                sessions = listOf(
                    CourseSession(
                        courseId = 0L,
                        dayOfWeek = day,
                        startPeriod = 1,
                        endPeriod = 2,
                        location = location,
                        startWeek = week.first,
                        endWeek = week.second,
                        weekParity = week.third
                    )
                )
            )
        }
        if (skipped > 0) {
            warnings += "有 $skipped 门课程没有上课时间，已跳过（可在「未排课」中查看）"
        }
        return out
    }

    private fun parseDay(text: String): Int? {
        if (text.isBlank()) return null
        return when {
            text.contains("一") -> 1
            text.contains("二") -> 2
            text.contains("三") -> 3
            text.contains("四") -> 4
            text.contains("五") -> 5
            text.contains("六") -> 6
            text.contains("日") || text.contains("天") || text.contains("七") -> 7
            else -> text.toIntOrNull()?.takeIf { it in 1..7 }
        }
    }

    // ------------------------------------------------------------------
    // 合并：同名同时段同周次的连续节次合成一条
    // ------------------------------------------------------------------

    /**
     * 把同名课程归并为同一条 [ImportedCourse]（一个 [Course] + 多个时段），
     * 避免同一门课在课表里出现多次时被拆成多个「课程」。
     */
    private fun consolidateByName(courses: List<ImportedCourse>): List<ImportedCourse> {
        class Merged(var teacher: String?) {
            val sessions = mutableListOf<CourseSession>()
        }

        val byName = linkedMapOf<String, Merged>()
        courses.forEach { imported ->
            val entry = byName.getOrPut(imported.course.name) {
                Merged(imported.course.teacher)
            }
            if (entry.teacher.isNullOrBlank()) entry.teacher = imported.course.teacher

            imported.sessions.forEach { s ->
                val duplicated = entry.sessions.any {
                    it.dayOfWeek == s.dayOfWeek &&
                        it.startPeriod == s.startPeriod &&
                        it.endPeriod == s.endPeriod &&
                        it.location == s.location &&
                        it.startWeek == s.startWeek &&
                        it.endWeek == s.endWeek &&
                        it.weekParity == s.weekParity
                }
                if (!duplicated) entry.sessions += s
            }
        }

        return byName.map { (name, merged) ->
            ImportedCourse(
                course = Course(name = name, teacher = merged.teacher),
                sessions = merged.sessions.sortedWith(
                    compareBy({ it.dayOfWeek }, { it.startPeriod }, { it.startWeek })
                )
            )
        }
    }

    private fun mergeSameCourses(courses: List<ImportedCourse>): List<ImportedCourse> {
        data class Key(
            val name: String,
            val day: Int,
            val location: String?,
            val startWeek: Int,
            val endWeek: Int,
            val parity: Int
        )

        val merged = linkedMapOf<Key, MutableList<CourseSession>>()
        val meta = mutableMapOf<Key, Course>()

        courses.forEach { imported ->
            imported.sessions.forEach { s ->
                val key = Key(
                    name = imported.course.name,
                    day = s.dayOfWeek,
                    location = s.location,
                    startWeek = s.startWeek,
                    endWeek = s.endWeek,
                    parity = s.weekParity
                )
                merged.getOrPut(key) { mutableListOf() } += s
                meta.putIfAbsent(key, imported.course)
            }
        }

        return merged.map { (key, sessions) ->
            // 把连续节次合并为区间
            val sorted = sessions.sortedBy { it.startPeriod }
            val ranges = mutableListOf<Pair<Int, Int>>()
            sorted.forEach { s ->
                val last = ranges.lastOrNull()
                if (last != null && s.startPeriod == last.second + 1) {
                    ranges[ranges.size - 1] = last.first to s.endPeriod
                } else {
                    ranges += s.startPeriod to s.endPeriod
                }
            }

            ImportedCourse(
                course = meta.getValue(key),
                sessions = ranges.map { (start, end) ->
                    CourseSession(
                        courseId = 0L,
                        dayOfWeek = key.day,
                        startPeriod = start,
                        endPeriod = end,
                        location = key.location,
                        startWeek = key.startWeek,
                        endWeek = key.endWeek,
                        weekParity = key.parity
                    )
                }
            )
        }
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    /** 取出 <table id="timetable"> 的片段。 */
    private fun extractTimetableTable(html: String): String? {
        val match = Regex("""<table[^>]*id\s*=\s*["']?timetable["']?[\s\S]*?</table>""", RegexOption.IGNORE_CASE)
            .find(html)
        if (match != null) return match.value

        // 退路：没有 id 时，取包含课表格子的第一个表格
        val tables = Regex("""<table[\s\S]*?</table>""", RegexOption.IGNORE_CASE)
            .findAll(html)
        return tables.firstOrNull { cellIdRegex.containsMatchIn(it.value) }?.value
    }

    /** HTML 片段 → 纯文本（保留换行）。 */
    private fun toPlainText(fragment: String): String = fragment
        .let { brRegex.replace(it, "\n") }
        .let { tagRegex.replace(it, "") }
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace(Regex("""[ \t\u00A0]+"""), " ")
        .lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")
}
