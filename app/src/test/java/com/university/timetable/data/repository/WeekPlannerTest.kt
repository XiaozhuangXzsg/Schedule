package com.university.timetable.data.repository

import com.university.timetable.data.entity.ClassOccurrence
import com.university.timetable.data.entity.DefaultPeriodTimes
import com.university.timetable.data.entity.Holiday
import com.university.timetable.data.entity.OverrideType
import com.university.timetable.data.entity.PeriodTime
import com.university.timetable.data.entity.ScheduleOverride
import com.university.timetable.data.entity.WeekParity
import com.university.timetable.util.SemesterWeeks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 周视图与「基础课表 / 实际课表」一致性的离线单元测试。
 *
 * 这里锁的是一个真实踩过的坑：AI（和快捷调休）把课调走之后，
 * 数据库写对了、结果提示也对了，**但周视图仍然看不见**——
 * 因为周视图当时走的是另一条「按星期几预先切片、只读两张表」的查询路径，
 * 既不认节假日，也不认日期变更记录。
 */
class WeekPlannerTest {

    private val periods: List<PeriodTime> = DefaultPeriodTimes.ALL

    /** 学期起始：2026-08-31（周一），与真机数据一致。 */
    private val semesterStart = LocalDate.parse("2026-08-31")

    private val totalWeeks = 20

    private fun week(date: LocalDate) = SemesterWeeks.weekOf(date, semesterStart)

    private fun occurrence(
        name: String,
        dayOfWeek: Int,
        startPeriod: Int = 1,
        endPeriod: Int = startPeriod,
        startWeek: Int = 1,
        endWeek: Int = 20,
        parity: Int = WeekParity.EVERY,
        sessionId: Long = 1L
    ) = ClassOccurrence(
        sessionId = sessionId,
        courseId = sessionId,
        name = name,
        teacher = "张老师",
        location = "天山堂 A101",
        category = "必修",
        color = 0xFF4C7DFF.toInt(),
        dayOfWeek = dayOfWeek,
        startPeriod = startPeriod,
        endPeriod = endPeriod,
        startMinutes = 0,
        endMinutes = 0,
        weekParity = parity,
        startWeek = startWeek,
        endWeek = endWeek
    )

    /** 某一天的课被整天调到另一天时，真实落库的那一对记录。 */
    private fun moveOutAndIn(
        from: LocalDate,
        to: LocalDate,
        occurrences: List<ClassOccurrence>
    ): List<ScheduleOverride> = occurrences.flatMap { o ->
        listOf(
            ScheduleOverride(
                id = o.sessionId * 10,
                date = from.toString(),
                type = OverrideType.MOVE_OUT.name,
                sourceSessionId = o.sessionId,
                name = o.name,
                startPeriod = o.startPeriod,
                endPeriod = o.endPeriod
            ),
            ScheduleOverride(
                id = o.sessionId * 10 + 1,
                date = to.toString(),
                type = OverrideType.MOVE_IN.name,
                sourceSessionId = o.sessionId,
                courseId = o.courseId,
                name = o.name,
                teacher = o.teacher,
                location = o.location,
                color = o.color,
                startPeriod = o.startPeriod,
                endPeriod = o.endPeriod,
                note = "由 $from 调至本日"
            )
        )
    }

    private fun blocksOf(
        week: Int,
        occurrences: List<ClassOccurrence>,
        holidays: List<Holiday> = emptyList(),
        overrides: List<ScheduleOverride> = emptyList()
    ) = weekBlocksOf(
        week = week,
        semesterStart = semesterStart,
        totalWeeks = totalWeeks,
        inputs = PlannerInputs(occurrences, periods, holidays, overrides)
    )

    // ---------------- 场景：AI / 快捷调休改完之后，周视图必须立刻看得见 ----------------

    @Test
    fun `moved-in course shows up on the target date in the week grid`() {
        // 2026-10-07（第 6 周周三）的课调到 2026-10-10（周六）
        val from = LocalDate.parse("2026-10-07")
        val to = LocalDate.parse("2026-10-10")
        val wednesday = listOf(occurrence("数学分析", dayOfWeek = 3, sessionId = 7L))

        val before = blocksOf(week(to), wednesday)
        assertEquals(1, before.size)
        assertEquals(from, before.first().date)

        val after = blocksOf(week(to), wednesday, overrides = moveOutAndIn(from, to, wednesday))
        assertEquals(1, after.size)
        assertEquals("数学分析", after.first().name)
        // 关键：课必须落在目标日期的列上，而不是原来星期三的列
        assertEquals(to, after.first().date)
        assertEquals(6, after.first().dayOfWeek)
    }

    @Test
    fun `moved-out course disappears from the source date in the week grid`() {
        val from = LocalDate.parse("2026-10-07")
        val to = LocalDate.parse("2026-10-10")
        val wednesday = listOf(occurrence("数学分析", dayOfWeek = 3, sessionId = 7L))

        val blocks = blocksOf(week(from), wednesday, overrides = moveOutAndIn(from, to, wednesday))
        assertTrue(blocks.none { it.date == from })
    }

    @Test
    fun `holiday hides the courses in the week grid but base schedule survives`() {
        // 2026-10-07 国庆放假：周视图不该画出来
        val day = LocalDate.parse("2026-10-07")
        val wednesday = listOf(occurrence("数学分析", dayOfWeek = 3))
        val holiday = Holiday(date = day.toString(), name = "国庆节", isHoliday = true)

        val blocks = blocksOf(week(day), wednesday, holidays = listOf(holiday))
        assertTrue(blocks.none { it.date == day })

        // 但基础课表仍在，AI 才能据它调课
        val snap = DayPlanner.snapshotOf(
            date = day,
            week = week(day),
            occurrences = wednesday,
            periods = periods,
            holidays = listOf(holiday)
        )
        assertEquals(1, snap.baseBlocks.size)
        assertTrue(snap.suppressedByOverride)
    }

    @Test
    fun `makeup day draws the borrowed courses on that date's own column`() {
        // 2026-10-11（周日）调休补 2026-10-09（周五）的课
        val sunday = LocalDate.parse("2026-10-11")
        val friday = listOf(occurrence("解析几何", dayOfWeek = 5, sessionId = 3L))
        val makeup = Holiday(
            date = sunday.toString(),
            name = "调休补课",
            isHoliday = false,
            makeupDayOfWeek = 5
        )

        val blocks = blocksOf(week(sunday), friday, holidays = listOf(makeup))
        // 同一周里 10-09（真周五）和 10-11（补课日）都会上这门课，各算一天
        assertEquals(2, blocks.size)

        val onSunday = blocks.filter { it.date == sunday }
        assertEquals(1, onSunday.size)
        // 补的课必须画在周日那一列（日期本身的星期），不能画进周五的格子
        assertEquals("解析几何", onSunday.first().name)
        assertEquals(7, onSunday.first().dayOfWeek)
    }

    @Test
    fun `repeating the same move does not duplicate courses in the week grid`() {
        val from = LocalDate.parse("2026-10-07")
        val to = LocalDate.parse("2026-10-10")
        val wednesday = listOf(
            occurrence("数学分析", dayOfWeek = 3, startPeriod = 1, endPeriod = 2, sessionId = 7L),
            occurrence("高等代数", dayOfWeek = 3, startPeriod = 3, endPeriod = 4, sessionId = 8L)
        )
        val once = moveOutAndIn(from, to, wednesday)

        // 幂等键相同 = 数据库里不会写入第二条；这里直接喂两份，模拟「已做过又做了一次」
        val deduped = once.distinctBy { it.idempotencyKey() }
        assertEquals(once.size, deduped.size)

        val blocks = blocksOf(week(to), wednesday, overrides = deduped)
        assertEquals(2, blocks.size)
        assertEquals(listOf(1, 3), blocks.map { it.startPeriod })
    }

    @Test
    fun `week grid keeps the target date's own courses`() {
        // 10-10 本来就有一节课，调入的课不能把它覆盖掉
        val from = LocalDate.parse("2026-10-07")
        val to = LocalDate.parse("2026-10-10")
        val wednesday = occurrence("数学分析", dayOfWeek = 3, startPeriod = 1, endPeriod = 2, sessionId = 7L)
        val occurrences = listOf(
            wednesday,
            occurrence("晚自习", dayOfWeek = 6, startPeriod = 11, endPeriod = 12, sessionId = 9L)
        )

        val blocks = blocksOf(week(to), occurrences, overrides = moveOutAndIn(from, to, listOf(wednesday)))
        assertTrue(blocks.any { it.name == "晚自习" && it.date == to })
        assertTrue(blocks.any { it.name == "数学分析" && it.date == to })
    }

    @Test
    fun `other dates in the same week are untouched`() {
        val from = LocalDate.parse("2026-10-07")
        val to = LocalDate.parse("2026-10-10")
        val occurrences = listOf(
            occurrence("数学分析", dayOfWeek = 3, sessionId = 7L),
            occurrence("解析几何", dayOfWeek = 4, sessionId = 3L)
        )

        val blocks = blocksOf(week(from), occurrences, overrides = moveOutAndIn(from, to, listOf(occurrences.first())))
        val thursday = blocks.filter { it.name == "解析几何" }
        assertEquals(1, thursday.size)
        assertEquals(LocalDate.parse("2026-10-08"), thursday.first().date)
    }
}
