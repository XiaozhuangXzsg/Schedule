package com.university.timetable

import com.university.timetable.data.entity.DefaultPeriodTimes
import com.university.timetable.data.entity.endMinutes
import com.university.timetable.data.entity.startMinutes
import com.university.timetable.util.PeriodParser
import com.university.timetable.util.SemesterWeeks
import com.university.timetable.util.WeekPatternParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 核心排课逻辑单元测试。
 * 运行：gradlew :app:testDebugUnitTest
 */
class ScheduleLogicTest {

    // ---------------- 周次换算 ----------------

    @Test
    fun `weekOf returns 1 for the semester start monday`() {
        val start = LocalDate.parse("2026-08-31") // 周一
        assertEquals(1, SemesterWeeks.weekOf(start, start))
    }

    @Test
    fun `weekOf handles midweek dates within the same week`() {
        val start = LocalDate.parse("2026-08-31")
        // 同周的周日仍属于第 1 周
        assertEquals(1, SemesterWeeks.weekOf(LocalDate.parse("2026-09-06"), start))
        // 下一周的周一进入第 2 周
        assertEquals(2, SemesterWeeks.weekOf(LocalDate.parse("2026-09-07"), start))
    }

    @Test
    fun `weekOf matches the reference timetable week 4`() {
        // 截图：2026/09/25 周五 属于第 4 周
        val start = LocalDate.parse("2026-08-31")
        assertEquals(4, SemesterWeeks.weekOf(LocalDate.parse("2026-09-25"), start))
    }

    @Test
    fun `weekOf returns zero or negative before semester start`() {
        val start = LocalDate.parse("2026-08-31")
        assertEquals(0, SemesterWeeks.weekOf(LocalDate.parse("2026-08-24"), start))
    }

    @Test
    fun `dateOf week resolves each weekday correctly`() {
        val start = LocalDate.parse("2026-08-31")
        assertEquals(LocalDate.parse("2026-09-25"), SemesterWeeks.dateOf(4, 5, start))
        assertEquals(LocalDate.parse("2026-09-21"), SemesterWeeks.dateOf(4, 1, start))
        assertEquals(LocalDate.parse("2026-09-27"), SemesterWeeks.dateOf(4, 7, start))
    }

    @Test
    fun `weekDates returns monday through sunday`() {
        val start = LocalDate.parse("2026-08-31")
        val dates = SemesterWeeks.weekDates(4, start)
        assertEquals(7, dates.size)
        assertEquals(LocalDate.parse("2026-09-21"), dates.first())
        assertEquals(LocalDate.parse("2026-09-27"), dates.last())
    }

    // ---------------- 单双周解析与匹配 ----------------

    @Test
    fun `parse plain week range`() {
        val r = WeekPatternParser.parse("1-16周")
        assertEquals(1, r.startWeek)
        assertEquals(16, r.endWeek)
        assertEquals(0, r.parity)
    }

    @Test
    fun `parse odd week range`() {
        val r = WeekPatternParser.parse("1-18周(单)")
        assertEquals(1, r.startWeek)
        assertEquals(18, r.endWeek)
        assertEquals(1, r.parity)
    }

    @Test
    fun `parse even week range with fullwidth parens`() {
        val r = WeekPatternParser.parse("2-18周（双）")
        assertEquals(2, r.startWeek)
        assertEquals(18, r.endWeek)
        assertEquals(2, r.parity)
    }

    @Test
    fun `parse single week`() {
        val r = WeekPatternParser.parse("5周")
        assertEquals(5, r.startWeek)
        assertEquals(5, r.endWeek)
    }

    @Test
    fun `parse discrete week list uses min and max`() {
        val r = WeekPatternParser.parse("1,3,5,7周")
        assertEquals(1, r.startWeek)
        assertEquals(7, r.endWeek)
    }

    @Test
    fun `parse empty falls back to full semester`() {
        val r = WeekPatternParser.parse(null)
        assertEquals(1, r.startWeek)
        assertEquals(20, r.endWeek)
    }

    @Test
    fun `matches respects parity filter`() {
        // 1-16 周（单）：第 3 周命中，第 4 周不命中
        assertTrue(WeekPatternParser.matches(3, 1, 16, 1))
        assertFalse(WeekPatternParser.matches(4, 1, 16, 1))
        // 1-16 周（双）：第 4 周命中，第 3 周不命中
        assertTrue(WeekPatternParser.matches(4, 1, 16, 2))
        assertFalse(WeekPatternParser.matches(3, 1, 16, 2))
    }

    @Test
    fun `matches excludes weeks outside the range`() {
        assertFalse(WeekPatternParser.matches(17, 1, 16, 0))
        assertFalse(WeekPatternParser.matches(0, 1, 16, 0))
        assertTrue(WeekPatternParser.matches(16, 1, 16, 0))
    }

    // ---------------- 节次解析 ----------------

    @Test
    fun `period parser handles range and single forms`() {
        assertEquals(1, PeriodParser.parse("第1-2节")?.start)
        assertEquals(2, PeriodParser.parse("第1-2节")?.end)
        assertEquals(3, PeriodParser.parse("3节")?.start)
        assertEquals(3, PeriodParser.parse("3节")?.end)
        assertEquals(5, PeriodParser.parse("5-6")?.start)
    }

    @Test
    fun `period parser normalises reversed ranges`() {
        val r = PeriodParser.parse("4-3节")
        assertEquals(3, r?.start)
        assertEquals(4, r?.end)
    }

    // ---------------- 当前节次判定（时间轴关键逻辑） ----------------

    @Test
    fun `period times map to expected minutes`() {
        val p3 = DefaultPeriodTimes.ALL.first { it.period == 3 }
        assertEquals(10 * 60 + 30, p3.startMinutes)
        assertEquals(11 * 60 + 15, p3.endMinutes)

        val p7 = DefaultPeriodTimes.ALL.first { it.period == 7 }
        assertEquals(14 * 60 + 30, p7.startMinutes)
    }

    @Test
    fun `default timetable is the lzu fourteen period schedule`() {
        val periods = DefaultPeriodTimes.ALL
        assertEquals(14, periods.size)
        assertEquals((1..14).toList(), periods.map { it.period })
        // 每节课的结束时间必须晚于开始时间
        periods.forEach { assertTrue("period ${it.period}", it.endMinutes > it.startMinutes) }
        // 相邻节次不应重叠：前一节结束 <= 后一节开始
        periods.zipWithNext().forEach { (a, b) ->
            assertTrue(
                "第${a.period}节结束 ${a.endTime} 不应晚于第${b.period}节开始 ${b.startTime}",
                a.endMinutes <= b.startMinutes
            )
        }
        // 抽查兰大作息
        assertEquals("08:30", periods.first { it.period == 1 }.startTime)
        assertEquals("12:20", periods.first { it.period == 5 }.startTime)
        assertEquals("19:00", periods.first { it.period == 11 }.startTime)
        assertEquals("22:30", periods.first { it.period == 14 }.endTime)
    }
}
