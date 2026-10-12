package com.university.timetable.importer

import com.university.timetable.data.entity.Course
import com.university.timetable.data.entity.CourseSession
import com.university.timetable.util.PeriodParser
import com.university.timetable.util.SemesterWeeks
import com.university.timetable.util.WeekPatternParser

/**
 * CSV 课表解析器。
 *
 * 支持两种形态：
 *
 * 1) 带表头的明细格式（推荐）
 *    课程名称,授课教师,星期,节次,周次,上课地点
 *    高等代数（一）,林艳,星期三,1-2节,1-16周,天山堂A212
 *
 * 2) 无表头的网格格式（每行一天）
 *    星期一,高等代数（一）,1-2,1-16周,天山堂A212
 */
object ScheduleCsvParser {

    private val headerKeywords = listOf(
        "课程", "名称", "教师", "老师", "星期", "周几", "节次", "节", "周次", "地点", "教室", "course", "name"
    )

    fun parse(csv: String, sourceLabel: String = "CSV"): ImportResult {
        val warnings = mutableListOf<String>()
        val lines = csv.replace("\r\n", "\n").replace("\r", "\n")
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (lines.isEmpty()) return ImportResult(emptyList(), listOf("文件为空"), sourceLabel)

        val delimiter = detectDelimiter(lines.first())
        val rows = lines.map { splitLine(it, delimiter) }

        val firstRowIsHeader = rows.first().count { cell ->
            headerKeywords.any { keyword -> cell.contains(keyword, ignoreCase = true) }
        } >= 2

        val courses = if (firstRowIsHeader) {
            parseWithHeader(rows.drop(1), warnings)
        } else {
            parseWithoutHeader(rows, warnings)
        }

        if (courses.isEmpty()) warnings += "未解析出课程，请检查列顺序或表头"
        return ImportResult(courses, warnings, sourceLabel)
    }

    private fun parseWithHeader(
        rows: List<List<String>>,
        warnings: MutableList<String>
    ): List<ImportedCourse> {
        val result = mutableListOf<ImportedCourse>()
        rows.forEachIndexed { index, row ->
            if (row.all { it.isBlank() }) return@forEachIndexed
            // 列顺序：名称, 教师, 星期, 节次, 周次, 地点
            val name = row.getOrNull(0)?.trim().orEmpty()
            if (name.isBlank()) return@forEachIndexed
            val teacher = row.getOrNull(1)?.trim()?.ifBlank { null }
            val day = parseDay(row.getOrNull(2)) ?: run {
                warnings += "第 ${index + 1} 行缺少星期信息，已跳过"
                return@forEachIndexed
            }
            val period = PeriodParser.parse(row.getOrNull(3)) ?: PeriodParser.Result(1, 2)
            val week = WeekPatternParser.parse(row.getOrNull(4))
            val place = row.getOrNull(5)?.trim()?.ifBlank { null }

            result += ImportedCourse(
                course = Course(name = name, teacher = teacher),
                sessions = listOf(
                    CourseSession(
                        courseId = 0L,
                        dayOfWeek = day,
                        startPeriod = period.start,
                        endPeriod = period.end,
                        location = place,
                        startWeek = week.startWeek,
                        endWeek = week.endWeek,
                        weekParity = week.parity
                    )
                )
            )
        }
        return result
    }

    private fun parseWithoutHeader(
        rows: List<List<String>>,
        warnings: MutableList<String>
    ): List<ImportedCourse> {
        val result = mutableListOf<ImportedCourse>()
        rows.forEachIndexed { index, row ->
            val day = parseDay(row.getOrNull(0))
            if (day == null) {
                warnings += "第 ${index + 1} 行不是以星期开头，已跳过"
                return@forEachIndexed
            }
            val name = row.getOrNull(1)?.trim().orEmpty().ifBlank { return@forEachIndexed }
            val period = PeriodParser.parse(row.getOrNull(2)) ?: PeriodParser.Result(1, 2)
            val week = WeekPatternParser.parse(row.getOrNull(3))
            val place = row.getOrNull(4)?.trim()?.ifBlank { null }
            val teacher = row.getOrNull(5)?.trim()?.ifBlank { null }

            result += ImportedCourse(
                course = Course(name = name, teacher = teacher),
                sessions = listOf(
                    CourseSession(
                        courseId = 0L,
                        dayOfWeek = day,
                        startPeriod = period.start,
                        endPeriod = period.end,
                        location = place,
                        startWeek = week.startWeek,
                        endWeek = week.endWeek,
                        weekParity = week.parity
                    )
                )
            )
        }
        return result
    }

    private fun detectDelimiter(line: String): Char = when {
        line.count { it == '\t' } > 0 -> '\t'
        line.count { it == ';' } > line.count { it == ',' } -> ';'
        line.count { it == '，' } > line.count { it == ',' } -> '，'
        else -> ','
    }

    /** 支持双引号包裹的字段。 */
    private fun splitLine(line: String, delimiter: Char): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        line.forEach { ch ->
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == delimiter && !inQuotes -> {
                    out += sb.toString().trim()
                    sb.clear()
                }
                else -> sb.append(ch)
            }
        }
        out += sb.toString().trim()
        return out
    }

    private fun parseDay(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        val t = text.trim()
        val numeric = t.toIntOrNull()
        if (numeric != null) return numeric.coerceIn(1, 7)

        return when {
            t.contains("一") || t.contains("1") -> 1
            t.contains("二") || t.contains("2") -> 2
            t.contains("三") || t.contains("3") -> 3
            t.contains("四") || t.contains("4") -> 4
            t.contains("五") || t.contains("5") -> 5
            t.contains("六") || t.contains("6") -> 6
            t.contains("日") || t.contains("天") || t.contains("7") -> 7
            else -> null
        }
    }

    @Suppress("unused")
    private fun dayLabel(day: Int) = SemesterWeeks.dayLabel(day)
}
