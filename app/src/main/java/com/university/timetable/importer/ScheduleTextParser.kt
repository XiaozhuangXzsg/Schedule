package com.university.timetable.importer

import com.university.timetable.data.entity.Course
import com.university.timetable.data.entity.CourseSession
import com.university.timetable.util.PeriodParser
import com.university.timetable.util.WeekPatternParser

/**
 * 解析「表格型」课表文本（HTML 表格 / CSV / OCR 后的行文本都适用）。
 *
 * 支持两种常见网格结构：
 *
 * 结构 A —— 日程式（本周课表 / 今日课表截图）：
 *   星期一 | 星期二 | ... | 星期日
 *   高等代数（一） | 数学分析（一） | ...
 *   1-16周 | 1-16周 | ...
 *   天山堂A212 | 天山堂A303 | ...
 *   授课教师：林艳 | ... | ...
 *
 * 结构 B —— 节次列表式（教务系统导出的 HTML）：
 *   星期一
 *   第1-2节
 *   (高等代数（一）) : 1
 *   1-16周
 *   天山堂A212
 *   林艳
 *   5
 *   1-16周
 *   天山堂A808
 *   林艳
 */
object ScheduleTextParser {

    private val dayHeaderRegex = Regex("""(星期[一二三四五六日天]|周[一二三四五六日天])""")
    private val timeRegex = Regex("""\d{1,2}\s*[:：]\s*\d{2}""")
    private val periodHeaderRegex = Regex("""第?\s*(\d+)\s*[-~－—]\s*(\d+)\s*节""")
    private val weekRegex = Regex("""\d+\s*[-~－—]?\s*\d*\s*周""")
    private val teacherLabel = Regex("""^(授课教师|任课教师|教师|老师)\s*[:：]?""")

    fun parse(rawText: String, sourceLabel: String = "文本"): ImportResult {
        val lines = rawText
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (lines.isEmpty()) {
            return ImportResult(emptyList(), listOf("内容为空"), sourceLabel)
        }

        return if (looksLikeGrid(lines)) {
            parseGrid(lines, sourceLabel)
        } else {
            parseSequential(lines, sourceLabel)
        }
    }

    /** 是否包含「星期一 / 周二」等表头，用于判定网格结构。 */
    private fun looksLikeGrid(lines: List<String>): Boolean {
        val headerCount = lines.take(20).count { line ->
            dayHeaderRegex.containsMatchIn(line) &&
                line.length <= 24 &&
                !line.contains("周)")
        }
        return headerCount >= 3
    }

    // ------------------------------------------------------------------
    // 结构 A：表头 + 逐行属性
    // ------------------------------------------------------------------

    private fun parseGrid(lines: List<String>, sourceLabel: String): ImportResult {
        val warnings = mutableListOf<String>()

        // 找表头行及其所在列位置
        val headerIndex = lines.indexOfFirst { line ->
            Regex("""(星期|周)[一二三四五六日天]""").findAll(line).count() >= 3
        }
        if (headerIndex < 0) {
            warnings += "未找到星期表头，已尝试按顺序解析"
            return parseSequential(lines, sourceLabel)
        }

        val headerLine = lines[headerIndex]
        val dayMatches = dayHeaderRegex.findAll(headerLine).toList()
        if (dayMatches.isEmpty()) {
            return ImportResult(emptyList(), listOf("未识别到星期信息"), sourceLabel)
        }

        // 表头之后按行推进：每一行承载一个属性（课程名 / 周次 / 地点 / 教师）
        data class Cell(
            var name: String = "",
            var place: String = "",
            var teacher: String = "",
            var weekText: String = ""
        )

        val cells = List(dayMatches.size) { Cell() }
        var cursor = headerIndex + 1

        while (cursor < lines.size) {
            val line = lines[cursor]
            if (line.contains("午休") || line.contains("课间")) {
                cursor++
                continue
            }
            val parts = splitCells(line, dayMatches.size)
            if (parts.isEmpty()) {
                cursor++
                continue
            }

            // 教师行
            if (teacherLabel.containsMatchIn(line)) {
                parts.forEachIndexed { i, v ->
                    if (i < cells.size && v.isNotBlank()) {
                        cells[i].teacher = teacherLabel.replace(v, "").trim()
                    }
                }
                cursor++
                continue
            }

            // 周次行
            if (parts.any { weekRegex.containsMatchIn(it) }) {
                parts.forEachIndexed { i, v ->
                    if (i < cells.size && v.isNotBlank()) cells[i].weekText = v
                }
                cursor++
                continue
            }

            // 时间行（08:00-08:50）：跳过，作息由 period_times 管理
            if (timeRegex.containsMatchIn(line) && parts.none { it.length > 12 }) {
                cursor++
                continue
            }

            // 其余视作课程名 / 地点
            parts.forEachIndexed { i, v ->
                if (i >= cells.size || v.isBlank()) return@forEachIndexed
                val clean = cleanCourseName(v)
                val cell = cells[i]
                if (cell.name.isBlank()) {
                    cell.name = clean
                } else if (cell.place.isBlank() && looksLikeLocation(clean)) {
                    cell.place = clean
                }
            }
            cursor++
        }

        val result = cells.mapIndexedNotNull { index, cell ->
            if (cell.name.isBlank()) return@mapIndexedNotNull null
            val day = index + 1
            val week = WeekPatternParser.parse(cell.weekText)
            ImportedCourse(
                course = Course(
                    name = cell.name,
                    teacher = cell.teacher.ifBlank { null }
                ),
                sessions = listOf(
                    CourseSession(
                        courseId = 0L,
                        dayOfWeek = day,
                        startPeriod = 1,
                        endPeriod = 2,
                        location = cell.place.ifBlank { null },
                        startWeek = week.startWeek,
                        endWeek = week.endWeek,
                        weekParity = week.parity
                    )
                )
            )
        }

        if (result.isEmpty()) warnings += "未能从该文件中解析出课程"
        return ImportResult(result, warnings, sourceLabel)
    }

    // ------------------------------------------------------------------
    // 结构 B：按「星期 → 节次 → 课程」顺序排列
    // ------------------------------------------------------------------

    private fun parseSequential(lines: List<String>, sourceLabel: String): ImportResult {
        val warnings = mutableListOf<String>()
        val courses = mutableListOf<ImportedCourse>()

        var currentDay: Int? = null
        var currentPeriod: IntRange? = null

        var pendingName: String? = null
        var pendingWeek: String? = null
        var pendingPlace: String? = null
        var pendingTeacher: String? = null

        fun flush() {
            val name = pendingName ?: return
            val day = currentDay ?: return
            val period = currentPeriod ?: 1..2
            val week = WeekPatternParser.parse(pendingWeek)
            courses += ImportedCourse(
                course = Course(name = name, teacher = pendingTeacher),
                sessions = listOf(
                    CourseSession(
                        courseId = 0L,
                        dayOfWeek = day,
                        startPeriod = period.first,
                        endPeriod = period.last,
                        location = pendingPlace,
                        startWeek = week.startWeek,
                        endWeek = week.endWeek,
                        weekParity = week.parity
                    )
                )
            )
            pendingName = null
            pendingWeek = null
            pendingPlace = null
            pendingTeacher = null
        }

        lines.forEach { line ->
            dayHeaderRegex.find(line)?.let { m ->
                flush()
                currentDay = dayOfWeek(m.value)
                return@forEach
            }

            periodHeaderRegex.find(line)?.let { m ->
                flush()
                val a = m.groupValues[1].toIntOrNull() ?: 1
                val b = m.groupValues[2].toIntOrNull() ?: a
                currentPeriod = minOf(a, b)..maxOf(a, b)
                return@forEach
            }

            // 形如「(高等代数（一）) : 1」
            if (line.contains("(") || line.contains("（")) {
                val name = extractParenthesizedName(line)
                if (name != null) {
                    flush()
                    pendingName = name
                    return@forEach
                }
            }

            if (weekRegex.containsMatchIn(line) && line.length <= 20) {
                pendingWeek = line
                return@forEach
            }

            if (teacherLabel.containsMatchIn(line)) {
                pendingTeacher = teacherLabel.replace(line, "").trim().ifBlank { null }
                return@forEach
            }

            // 纯数字（学分）忽略
            if (line.toDoubleOrNull() != null) return@forEach

            // 地点：包含「楼 / 堂 / 馆 / 室 / 区 / 教」等关键字
            if (looksLikeLocation(line)) {
                pendingPlace = line
                return@forEach
            }

            // 其他情况：可能是课程名或教师名
            when {
                pendingName == null -> pendingName = cleanCourseName(line)

                // 已有课程名时，2–4 个纯汉字且尚未记录教师 → 视为授课教师
                pendingTeacher == null && looksLikeTeacherName(line) -> pendingTeacher = line
            }
        }
        flush()

        if (courses.isEmpty()) warnings += "未能识别出课程，请检查文件格式"
        return ImportResult(courses, warnings, sourceLabel)
    }

    /** 中文姓名判定：2–4 个汉字，且不是地点或周次。 */
    private fun looksLikeTeacherName(text: String): Boolean {
        val t = text.trim()
        if (t.length !in 2..4) return false
        if (!t.all { it.code in 0x4E00..0x9FFF }) return false
        if (ScheduleSemantics.isLocation(t)) return false
        if (ScheduleSemantics.isWeek(t)) return false
        if (teacherLabel.containsMatchIn(t)) return false
        return true
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    /** 把一行按制表符 / 多空格切分为单元格。 */
    private fun splitCells(line: String, expected: Int): List<String> {
        val byTab = line.split("\t")
        if (byTab.size >= 2) return byTab.map { it.trim() }

        val byMultiSpace = line.split(Regex("""\s{2,}"""))
        if (byMultiSpace.size >= 2) return byMultiSpace.map { it.trim() }

        // 单列文本：整体作为第一个单元格
        return listOf(line)
    }

    private fun cleanCourseName(raw: String): String = ScheduleSemantics.cleanCourseName(raw)

    private fun extractParenthesizedName(line: String): String? =
        ScheduleSemantics.extractParenthesizedName(line)

    private fun looksLikeLocation(text: String): Boolean = ScheduleSemantics.isLocation(text)

    private fun dayOfWeek(text: String): Int = when {
        text.contains("一") -> 1
        text.contains("二") -> 2
        text.contains("三") -> 3
        text.contains("四") -> 4
        text.contains("五") -> 5
        text.contains("六") -> 6
        else -> 7
    }
}
