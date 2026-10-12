package com.university.timetable.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * 学期周次计算：以「第 1 周的周一」为基准，向前/向后推算 ISO 周。
 */
object SemesterWeeks {

    /** 把任意日期对齐到所在周的周一。 */
    fun mondayOf(date: LocalDate): LocalDate =
        date.minusDays((date.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())

    /**
     * 计算 [date] 属于第几周（可能 <= 0，表示学期尚未开始）。
     */
    fun weekOf(date: LocalDate, semesterStartMonday: LocalDate): Int {
        val days = ChronoUnit.DAYS.between(mondayOf(semesterStartMonday), mondayOf(date))
        return Math.floorDiv(days, 7L).toInt() + 1
    }

    /** 第 [week] 周的周一日期。 */
    fun mondayOfWeek(week: Int, semesterStartMonday: LocalDate): LocalDate =
        mondayOf(semesterStartMonday).plusWeeks((week - 1).toLong())

    /** 第 [week] 周第 [dayOfWeek] 天（1=周一）的日期。 */
    fun dateOf(week: Int, dayOfWeek: Int, semesterStartMonday: LocalDate): LocalDate =
        mondayOfWeek(week, semesterStartMonday).plusDays((dayOfWeek - 1).toLong())

    /** 周几的中文短名。 */
    fun dayLabel(dayOfWeek: Int): String = when (dayOfWeek) {
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        7 -> "周日"
        else -> "周?"
    }

    /** 周几的极简单字。 */
    fun dayChar(dayOfWeek: Int): String = when (dayOfWeek) {
        1 -> "一"
        2 -> "二"
        3 -> "三"
        4 -> "四"
        5 -> "五"
        6 -> "六"
        7 -> "日"
        else -> "?"
    }

    /** 按周一起始的日期列表（用于周视图表头）。 */
    fun weekDates(week: Int, semesterStartMonday: LocalDate): List<LocalDate> =
        (0..6).map { mondayOfWeek(week, semesterStartMonday).plusDays(it.toLong()) }

    /** 判断某周是否包含今天的所在周。 */
    fun isCurrentWeek(week: Int, today: LocalDate, semesterStartMonday: LocalDate): Boolean =
        weekOf(today, semesterStartMonday) == week

    fun nowMinutes(now: LocalTime = LocalTime.now()): Int = now.hour * 60 + now.minute

    /** 把分钟数格式化为 mm:ss 或 hh:mm。 */
    fun formatMinutes(total: Int): String {
        val safe = total.coerceAtLeast(0)
        val h = safe / 60
        val m = safe % 60
        return if (h > 0) "%d:%02d".format(h, m) else "%d 分钟".format(m)
    }
}
