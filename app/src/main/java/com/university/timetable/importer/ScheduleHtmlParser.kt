package com.university.timetable.importer

import com.university.timetable.data.entity.Course
import com.university.timetable.data.entity.CourseSession
import com.university.timetable.util.WeekPatternParser

/**
 * 轻量 HTML 课表解析器。
 *
 * 不依赖第三方库：先把 `<table>` 结构转成「制表符分隔的文本」，
 * 再按「网格语义」解析——每个星期列独立收集课程名 / 地点 / 周次 / 教师，
 * 最后按出现顺序配对成课程。
 *
 * 这样既能处理「一行一个属性」的教务系统表格，也能处理
 * 「单元格内用 <br> 同时写课程名与地点」的紧凑表格。
 */
object ScheduleHtmlParser {

    private val tableRegex = Regex("""<table[\s\S]*?</table>""", RegexOption.IGNORE_CASE)
    private val rowRegex = Regex("""<tr[\s\S]*?</tr>""", RegexOption.IGNORE_CASE)
    private val cellRegex = Regex("""<t[dh][\s\S]*?</t[dh]>""", RegexOption.IGNORE_CASE)
    private val tagRegex = Regex("""<[^>]+>""")
    private val brRegex = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
    private val scriptRegex = Regex("""<(script|style)[\s\S]*?</\1>""", RegexOption.IGNORE_CASE)
    private val dayHeaderRegex = Regex("""(星期[一二三四五六日天]|周[一二三四五六日天])""")

    fun parse(html: String, sourceLabel: String = "HTML"): ImportResult {
        val cleaned = scriptRegex.replace(html, " ")
        val tables = tableRegex.findAll(cleaned).map { it.value }.toList()

        if (tables.isEmpty()) {
            val result = ScheduleTextParser.parse(htmlToText(cleaned), sourceLabel)
            return result.copy(
                warnings = result.warnings + "文件中未发现 <table> 结构，已按纯文本解析"
            )
        }

        // 选择包含最多星期表头的表格
        val best = tables.maxByOrNull { table ->
            dayHeaderRegex.findAll(table).count()
        } ?: tables.first()

        val rows = rowRegex.findAll(best).map { row ->
            cellRegex.findAll(row.value).joinToString("\t") { cell -> htmlToText(cell.value) }
        }.filter { it.isNotBlank() }.toList()

        if (rows.isEmpty()) {
            return ImportResult(emptyList(), listOf("表格中没有可解析的行"), sourceLabel)
        }

        val headerIndex = rows.indexOfFirst { row ->
            dayHeaderRegex.findAll(row).count() >= 3
        }

        return if (headerIndex >= 0) {
            parseGridRows(rows, headerIndex, sourceLabel)
        } else {
            ScheduleTextParser.parse(rows.joinToString("\n").replace('\t', '\n'), sourceLabel)
        }
    }

    /**
     * 网格解析：表头给出星期列，之后每行按列收集片段。
     */
    private fun parseGridRows(
        rows: List<String>,
        headerIndex: Int,
        sourceLabel: String
    ): ImportResult {
        val warnings = mutableListOf<String>()

        val headerCells = rows[headerIndex].split("\t").map { it.trim() }
        val dayColumns = headerCells.mapIndexedNotNull { index, cell ->
            if (dayHeaderRegex.containsMatchIn(cell)) index to dayOfWeek(cell) else null
        }
        if (dayColumns.isEmpty()) {
            return ImportResult(emptyList(), listOf("表头中没有星期列"), sourceLabel)
        }

        // 每个星期列独立的片段池
        val nameFrags = dayColumns.map { mutableListOf<String>() }
        val placeFrags = dayColumns.map { mutableListOf<String>() }
        val teacherFrags = dayColumns.map { mutableListOf<String>() }
        val weekFrags = dayColumns.map { mutableListOf<String>() }

        for (r in (headerIndex + 1) until rows.size) {
            val cells = rows[r].split("\t").map { it.trim() }
            dayColumns.forEachIndexed { i, (colIndex, _) ->
                if (colIndex >= cells.size) return@forEachIndexed
                val cell = cells[colIndex]
                if (cell.isBlank()) return@forEachIndexed

                ScheduleSemantics.splitFragments(cell).forEach { fragment ->
                    when {
                        // 显式「授课教师：X」优先级最高
                        ScheduleSemantics.hasTeacherLabel(fragment) ->
                            teacherFrags[i] += ScheduleSemantics.stripTeacherLabel(fragment)

                        ScheduleSemantics.isWeek(fragment) -> weekFrags[i] += fragment

                        ScheduleSemantics.isLocation(fragment) ->
                            placeFrags[i] += ScheduleSemantics.stripLocationPrefix(fragment)

                        else -> nameFrags[i] += ScheduleSemantics.cleanCourseName(fragment)
                    }
                }
            }
        }

        val courses = mutableListOf<ImportedCourse>()
        dayColumns.forEachIndexed { i, (_, day) ->
            val rawNames = nameFrags[i].filter { it.isNotBlank() }
            val explicitTeachers = teacherFrags[i]

            // 拆分「课程名」与「教师名」：
            // 先剔除与显式教师重名的项，再按长度降序，长者为课程名；
            // 剩余较短的中文姓名按出现顺序视为授课教师。
            val sorted = rawNames.sortedByDescending { it.length }
            val courseNames = mutableListOf<String>()
            val inferredTeachers = mutableListOf<String>()

            sorted.forEach { candidate ->
                val isTeacherLike = looksLikeTeacherName(candidate) &&
                    explicitTeachers.none { it == candidate } &&
                    courseNames.isNotEmpty()
                if (isTeacherLike) {
                    inferredTeachers += candidate
                } else {
                    courseNames += candidate
                }
            }

            val teachers = (explicitTeachers + inferredTeachers)

            courseNames.forEachIndexed { index, name ->
                val week = WeekPatternParser.parse(weekFrags[i].getOrNull(index))
                courses += ImportedCourse(
                    course = Course(
                        name = name,
                        teacher = teachers.getOrNull(index)?.ifBlank { null }
                    ),
                    sessions = listOf(
                        CourseSession(
                            courseId = 0L,
                            dayOfWeek = day,
                            startPeriod = 1,
                            endPeriod = 2,
                            location = placeFrags[i].getOrNull(index)?.ifBlank { null },
                            startWeek = week.startWeek,
                            endWeek = week.endWeek,
                            weekParity = week.parity
                        )
                    )
                )
            }
        }

        if (courses.isEmpty()) warnings += "表格中未解析出课程"
        return ImportResult(courses, warnings, sourceLabel)
    }

    /** 中文姓名判定：2–4 个汉字且不含地点关键词。 */
    private fun looksLikeTeacherName(text: String): Boolean {
        val t = text.trim()
        if (t.length !in 2..4) return false
        if (!t.all { it.code in 0x4E00..0x9FFF }) return false
        if (ScheduleSemantics.isLocation(t)) return false
        if (ScheduleSemantics.isWeek(t)) return false
        return true
    }

    private fun htmlToText(fragment: String): String = fragment
        .let { brRegex.replace(it, "\n") }
        .let { tagRegex.replace(it, "") }
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace(Regex("""[ \u00A0]+"""), " ")
        .replace(Regex("""\n{2,}"""), "\n")
        .trim()

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
