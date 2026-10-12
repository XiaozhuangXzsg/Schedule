package com.university.timetable.util

import com.university.timetable.data.entity.WeekParity

/**
 * 解析课表文本里的「周次」表达，例如：
 *  - 1-16周
 *  - 1-16周(单)
 *  - 2-18周(双)
 *  - 1,3,5,7周
 *  - 5-16周
 *  - 1-16周(单),2-16周(双)
 */
object WeekPatternParser {

    data class Result(
        val startWeek: Int,
        val endWeek: Int,
        val parity: Int
    )

    private val rangeRegex = Regex("""(\d+)\s*[-~－—]\s*(\d+)\s*周""")
    private val singleRegex = Regex("""(\d+)\s*周""")
    private val listRegex = Regex("""((?:\d+\s*[,，、]\s*)+\d+)\s*周""")

    /**
     * 解析周次描述。若同时存在单双周两段，则取第一个区间并标记单双周；
     * 无法识别时回退为 1-20 周每周。
     */
    fun parse(text: String?): Result {
        if (text.isNullOrBlank()) return Result(1, 20, WeekParity.EVERY)
        val cleaned = text.replace(" ", "")
        val odd = cleaned.contains("单")
        val even = cleaned.contains("双")

        // 形如 "1,3,5,7周" 的离散周次：取最小/最大，非单双周
        listRegex.find(cleaned)?.let { m ->
            val nums = m.groupValues[1].split(",", "，", "、")
                .mapNotNull { it.trim().toIntOrNull() }
            if (nums.isNotEmpty()) {
                return Result(nums.min(), nums.max(), WeekParity.EVERY)
            }
        }

        rangeRegex.find(cleaned)?.let { m ->
            val s = m.groupValues[1].toIntOrNull() ?: 1
            val e = m.groupValues[2].toIntOrNull() ?: 20
            val parity = when {
                odd && !even -> WeekParity.ODD
                even && !odd -> WeekParity.EVEN
                else -> WeekParity.EVERY
            }
            return Result(minOf(s, e), maxOf(s, e), parity)
        }

        singleRegex.find(cleaned)?.let { m ->
            val w = m.groupValues[1].toIntOrNull() ?: 1
            val parity = when {
                odd && !even -> WeekParity.ODD
                even && !odd -> WeekParity.EVEN
                else -> WeekParity.EVERY
            }
            return Result(w, w, parity)
        }

        return Result(1, 20, WeekParity.EVERY)
    }

    /** 判断第 [week] 周是否命中该周次模式。 */
    fun matches(week: Int, startWeek: Int, endWeek: Int, parity: Int): Boolean {
        if (week < startWeek || week > endWeek) return false
        return when (parity) {
            WeekParity.ODD -> week % 2 == 1
            WeekParity.EVEN -> week % 2 == 0
            else -> true
        }
    }

    /** 生成人类可读的周次描述。 */
    fun describe(startWeek: Int, endWeek: Int, parity: Int): String {
        val base = if (startWeek == endWeek) "$startWeek 周" else "$startWeek-$endWeek 周"
        return when (parity) {
            WeekParity.ODD -> "$base（单）"
            WeekParity.EVEN -> "$base（双）"
            else -> base
        }
    }
}

/**
 * 解析作息时间文本，例如 "08:00-08:50"、"8：00~8：50"、"08:00 - 08:50"。
 */
object TimeRangeParser {

    private val timeRegex = Regex("""(\d{1,2})\s*[:：]\s*(\d{2})""")

    data class Range(val start: String, val end: String)

    fun parse(text: String?): Range? {
        if (text.isNullOrBlank()) return null
        val found = timeRegex.findAll(text).map { m ->
            "%02d:%02d".format(m.groupValues[1].toIntOrNull() ?: 0, m.groupValues[2].toIntOrNull() ?: 0)
        }.toList()
        if (found.size < 2) return null
        return Range(found[0], found[1])
    }

    /** 提取文本中的第一个时间点。 */
    fun firstTime(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val m = timeRegex.find(text) ?: return null
        return "%02d:%02d".format(m.groupValues[1].toIntOrNull() ?: 0, m.groupValues[2].toIntOrNull() ?: 0)
    }
}

/**
 * 解析节次文本，例如 "第1-2节"、"1-2节"、"3、4节"、"第5节"。
 */
object PeriodParser {

    data class Result(val start: Int, val end: Int)

    private val rangeRegex = Regex("""(\d+)\s*[-~－—]\s*(\d+)""")
    private val singleRegex = Regex("""(\d+)""")

    fun parse(text: String?): Result? {
        if (text.isNullOrBlank()) return null
        val cleaned = text.replace("第", "").replace("节", "").replace(" ", "")
        rangeRegex.find(cleaned)?.let { m ->
            val s = m.groupValues[1].toIntOrNull() ?: return null
            val e = m.groupValues[2].toIntOrNull() ?: return null
            return Result(minOf(s, e), maxOf(s, e))
        }
        singleRegex.find(cleaned)?.let { m ->
            val s = m.groupValues[1].toIntOrNull() ?: return null
            return Result(s, s)
        }
        return null
    }
}
