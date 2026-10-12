package com.university.timetable.data.repository

import com.university.timetable.data.entity.ClassOccurrence
import com.university.timetable.data.entity.DefaultPeriodTimes
import com.university.timetable.data.entity.Holiday
import com.university.timetable.data.entity.PeriodTime
import com.university.timetable.data.entity.ScheduleOverride
import com.university.timetable.data.entity.WeekParity
import com.university.timetable.util.SemesterWeeks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 「基础课表 / 实际课表」分离的离线单元测试。
 *
 * 覆盖的都是线上真实踩过的坑：
 * - 放假把原始排课一起抹掉 → AI 查不到「10 月 7 日原本要上的课」，无法调课；
 * - 调课套用了目标日期的周次 → 单双周 / 限定周次的课被漏掉或重复带上；
 * - 重复执行同一调课指令 → 目标日期出现重复课程。
 */
class DayPlannerTest {

    private val periods: List<PeriodTime> = DefaultPeriodTimes.ALL

    /** 学期起始：2026-08-31（周一），与真机数据一致。 */
    private val semesterStart = LocalDate.parse("2026-08-31")

    private fun week(date: LocalDate) = SemesterWeeks.weekOf(date, semesterStart)

    private fun occurrence(
        name: String,
        dayOfWeek: Int,
        startPeriod: Int = 1,
        endPeriod: Int = startPeriod,
        startWeek: Int = 1,
        endWeek: Int = 20,
        parity: Int = WeekParity.EVERY,
        sessionId: Long = 1L,
        location: String? = "天山堂 A101"
    ) = ClassOccurrence(
        sessionId = sessionId,
        courseId = sessionId,
        name = name,
        teacher = "张老师",
        location = location,
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

    private fun snapshot(
        date: String,
        occurrences: List<ClassOccurrence>,
        holidays: List<Holiday> = emptyList(),
        overrides: List<ScheduleOverride> = emptyList()
    ): DaySnapshot {
        val d = LocalDate.parse(date)
        return DayPlanner.snapshotOf(
            date = d,
            week = week(d),
            occurrences = occurrences,
            periods = periods,
            holidays = holidays,
            overrides = overrides
        )
    }

    // ---------------- 场景 1：任意日期 / 任意周次都能查到基础课表 ----------------

    @Test
    fun `base schedule is queryable for a week the UI is not showing`() {
        // 2026-09-23 是第 4 周周三；界面停留在第 1 周也应能查到
        val snap = snapshot("2026-09-23", listOf(occurrence("编译原理", dayOfWeek = 3)))

        assertEquals(4, snap.week)
        assertEquals(1, snap.baseBlocks.size)
        assertEquals(1, snap.blocks.size)
        assertEquals("编译原理", snap.baseBlocks.first().name)
    }

    @Test
    fun `base schedule is empty before the semester starts`() {
        val snap = snapshot("2026-08-24", listOf(occurrence("编译原理", dayOfWeek = 1)))
        assertEquals(0, snap.week)
        assertTrue(snap.baseBlocks.isEmpty())
        assertTrue(snap.blocks.isEmpty())
    }

    @Test
    fun `course limited to certain weeks only appears in those weeks`() {
        val occ = listOf(occurrence("短学期专题", dayOfWeek = 3, startWeek = 4, endWeek = 5))

        // 第 4 周（2026-09-23）在范围内
        assertEquals(1, snapshot("2026-09-23", occ).baseBlocks.size)
        // 第 6 周（2026-10-07）已超出 endWeek
        assertTrue(snapshot("2026-10-07", occ).baseBlocks.isEmpty())
    }

    @Test
    fun `odd parity course is filtered by the source week parity`() {
        val occ = listOf(occurrence("单周研讨", dayOfWeek = 3, parity = WeekParity.ODD))

        // 第 4 周（偶数）不上
        assertTrue(snapshot("2026-09-23", occ).baseBlocks.isEmpty())
        // 第 5 周（奇数）要上
        assertEquals(1, snapshot("2026-09-30", occ).baseBlocks.size)
    }

    // ---------------- 场景 2：放假保留基础课表，实际课表为空 ----------------

    @Test
    fun `holiday keeps base schedule and empties the actual one`() {
        val nationalDay = Holiday(date = "2026-10-07", name = "国庆节", isHoliday = true)
        val occ = listOf(occurrence("数据结构", dayOfWeek = 3, startPeriod = 3, endPeriod = 4))

        val snap = snapshot("2026-10-07", occ, holidays = listOf(nationalDay))

        // 实际课表：放假不上课
        assertTrue(snap.blocks.isEmpty())
        assertNull(snap.effectiveDayOfWeek)
        assertTrue(snap.isHoliday)

        // 基础课表：仍然保留「原本应该上的课」，AI 据此才能调课
        assertEquals(1, snap.baseBlocks.size)
        assertEquals("数据结构", snap.baseBlocks.first().name)
        assertEquals(3, snap.baseBlocks.first().startPeriod)
        assertTrue(snap.suppressedByOverride)
    }

    @Test
    fun `makeup day uses the rewritten weekday for both schedules`() {
        // 2026-10-10 是周六，调休按周三上课
        val makeup = Holiday(date = "2026-10-10", name = "调休补课", isHoliday = false, makeupDayOfWeek = 3)
        val occ = listOf(occurrence("数据结构", dayOfWeek = 3))

        val snap = snapshot("2026-10-10", occ, holidays = listOf(makeup))

        assertEquals(3, snap.effectiveDayOfWeek)
        assertEquals(1, snap.baseBlocks.size)
        assertEquals(1, snap.blocks.size)
        assertTrue(snap.isMakeupDay)
    }

    // ---------------- 场景 3：调课只影响指定日期 ----------------

    @Test
    fun `move out empties the source day and move in fills the target day`() {
        val source = "2026-10-07"
        val target = "2026-10-10"
        val occ = listOf(occurrence("数据结构", dayOfWeek = 3, startPeriod = 3, endPeriod = 4, sessionId = 7L))

        val overrides = listOf(
            ScheduleOverride(
                date = source,
                type = "MOVE_OUT",
                sourceSessionId = 7L,
                name = "数据结构",
                startPeriod = 3,
                endPeriod = 4
            ),
            ScheduleOverride(
                date = target,
                type = "MOVE_IN",
                sourceSessionId = 7L,
                name = "数据结构",
                teacher = "张老师",
                location = "天山堂 A101",
                startPeriod = 3,
                endPeriod = 4,
                note = "由 2026-10-07 调至本日"
            )
        )

        val src = snapshot(source, occ, overrides = overrides)
        val dst = snapshot(target, occ, overrides = overrides)

        // 源日期：基础课表还在（可用于解释与再次调课），实际课表已空
        assertEquals(1, src.baseBlocks.size)
        assertTrue(src.blocks.isEmpty())

        // 目标日期：调进来的课出现，且带上原课程信息
        assertEquals(1, dst.blocks.size)
        assertEquals("数据结构", dst.blocks.first().name)
        assertEquals("天山堂 A101", dst.blocks.first().location)
        assertEquals(3, dst.blocks.first().startPeriod)

        // 其它日期完全不受影响（其他周三照常上课）
        val otherWednesday = snapshot("2026-09-23", occ, overrides = overrides)
        assertEquals(1, otherWednesday.blocks.size)
    }

    @Test
    fun `other dates of the same weekday are untouched by a single day move`() {
        val occ = listOf(occurrence("数据结构", dayOfWeek = 3, sessionId = 7L))
        val overrides = listOf(
            ScheduleOverride(date = "2026-10-07", type = "CANCEL", note = "临时停课")
        )

        assertTrue(snapshot("2026-10-07", occ, overrides = overrides).blocks.isEmpty())
        assertEquals(1, snapshot("2026-09-23", occ, overrides = overrides).blocks.size)
        assertEquals(1, snapshot("2026-10-14", occ, overrides = overrides).blocks.size)
    }

    @Test
    fun `moved course keeps the source week eligibility and is not re-filtered by the target week`() {
        // 源日期第 4 周（偶数）有课；目标是第 6 周（偶数）的周六
        val occ = listOf(occurrence("编译原理", dayOfWeek = 3, parity = WeekParity.EVEN, sessionId = 11L))
        val overrides = listOf(
            ScheduleOverride(date = "2026-09-23", type = "MOVE_OUT", sourceSessionId = 11L, name = "编译原理", startPeriod = 1, endPeriod = 1),
            ScheduleOverride(date = "2026-10-10", type = "MOVE_IN", sourceSessionId = 11L, name = "编译原理", startPeriod = 1, endPeriod = 1)
        )

        val src = snapshot("2026-09-23", occ, overrides = overrides)
        val dst = snapshot("2026-10-10", occ, overrides = overrides)

        assertEquals(1, src.baseBlocks.size)
        assertTrue(src.blocks.isEmpty())
        // 调入的课带着快照信息出现，不因为目标周是第 6 周而被重新筛掉
        assertEquals(1, dst.blocks.size)
        assertEquals("编译原理", dst.blocks.first().name)
    }

    @Test
    fun `repeated move produces exactly one incoming block`() {
        val target = "2026-10-10"
        val occ = listOf(occurrence("数据结构", dayOfWeek = 3, sessionId = 7L))

        // 数据库靠 (date, type, sourceSessionId) 去重；这里模拟「同一批记录重复插入」后的样子：
        // 幂等保护生效时目标日只会有一条 MOVE_IN
        val overrides = listOf(
            ScheduleOverride(date = "2026-10-07", type = "MOVE_OUT", sourceSessionId = 7L, name = "数据结构", startPeriod = 1, endPeriod = 1),
            ScheduleOverride(date = "2026-10-10", type = "MOVE_IN", sourceSessionId = 7L, name = "数据结构", startPeriod = 1, endPeriod = 1)
        )

        val once = snapshot(target, occ, overrides = overrides)
        val twice = snapshot(target, occ, overrides = overrides)

        assertEquals(1, once.blocks.size)
        assertEquals(once.blocks.size, twice.blocks.size)
    }

    @Test
    fun `two different sessions moved to the same day both appear and conflict is detectable`() {
        val target = "2026-10-10"
        val occ = listOf(
            occurrence("数据结构", dayOfWeek = 3, sessionId = 7L),
            occurrence("编译原理", dayOfWeek = 3, sessionId = 8L)
        )
        val overrides = listOf(
            ScheduleOverride(date = "2026-10-10", type = "MOVE_IN", sourceSessionId = 7L, name = "数据结构", startPeriod = 1, endPeriod = 2),
            ScheduleOverride(date = "2026-10-10", type = "MOVE_IN", sourceSessionId = 8L, name = "编译原理", startPeriod = 2, endPeriod = 3)
        )

        val dst = snapshot(target, occ, overrides = overrides)
        assertEquals(2, dst.blocks.size)
        // 第 2 节重叠 → 上层用 startMinutes/endMinutes 判断冲突
        assertTrue(dst.blocks.any { it.name == "数据结构" })
        assertTrue(dst.blocks.any { it.name == "编译原理" })
    }

    // ---------------- 场景 4：换教室 / 加课 / 停课 ----------------

    @Test
    fun `room change only overrides the location of that single day`() {
        val occ = listOf(occurrence("数据结构", dayOfWeek = 3, sessionId = 7L, location = "天山堂 A101"))
        val overrides = listOf(
            ScheduleOverride(date = "2026-09-23", type = "ROOM", sourceSessionId = 7L, name = "数据结构", location = "天山堂 B202")
        )

        val changed = snapshot("2026-09-23", occ, overrides = overrides)
        val untouched = snapshot("2026-09-30", occ, overrides = overrides)

        assertEquals("天山堂 B202", changed.blocks.first().location)
        assertEquals("天山堂 A101", untouched.blocks.first().location)
    }

    @Test
    fun `one off added course appears only on its own date`() {
        val overrides = listOf(
            ScheduleOverride(
                date = "2026-10-10",
                type = "ADD",
                name = "补课：形势与政策",
                location = "秦岭堂 C301",
                startPeriod = 5,
                endPeriod = 6,
                note = "补课"
            )
        )

        val added = snapshot("2026-10-10", emptyList(), overrides = overrides)
        val other = snapshot("2026-10-11", emptyList(), overrides = overrides)

        assertEquals(1, added.blocks.size)
        assertEquals("补课：形势与政策", added.blocks.first().name)
        assertEquals(5, added.blocks.first().startPeriod)
        assertEquals(6, added.blocks.first().endPeriod)
        assertTrue(other.blocks.isEmpty())
    }

    @Test
    fun `cancel day empties the actual schedule but keeps the base one`() {
        val occ = listOf(occurrence("数据结构", dayOfWeek = 3, sessionId = 7L))
        val overrides = listOf(ScheduleOverride(date = "2026-09-23", type = "CANCEL", note = "临时停课"))

        val snap = snapshot("2026-09-23", occ, overrides = overrides)

        assertTrue(snap.blocks.isEmpty())
        assertEquals(1, snap.baseBlocks.size)
        assertTrue(snap.hasOverrides)
        assertFalse(snap.blocks.isNotEmpty())
    }

    @Test
    fun `base and actual schedules are identical when there is no override`() {
        val occ = listOf(occurrence("数据结构", dayOfWeek = 3, sessionId = 7L))
        val snap = snapshot("2026-09-23", occ)

        assertEquals(snap.baseBlocks.map { it.name }, snap.blocks.map { it.name })
        assertFalse(snap.hasOverrides)
    }
}
