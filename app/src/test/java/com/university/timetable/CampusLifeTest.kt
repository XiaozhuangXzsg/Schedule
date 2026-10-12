package com.university.timetable

import com.university.timetable.data.entity.CampusFacility
import com.university.timetable.data.entity.FacilityWindow
import com.university.timetable.data.entity.TimeWindowCodec
import com.university.timetable.data.repository.FacilityScheduleResolver
import com.university.timetable.data.seed.DefaultFacilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 校园生活作息规则的单元测试。
 * 覆盖：时间编解码、按星期覆盖、节假日覆盖、开放状态判定。
 */
class CampusLifeTest {

    // ---------------- 编解码 ----------------

    @Test
    fun `decode single window`() {
        val w = TimeWindowCodec.decode("08:00-22:00")
        assertEquals(1, w.size)
        assertEquals("08:00", w[0].start)
        assertEquals("22:00", w[0].end)
    }

    @Test
    fun `decode multiple windows`() {
        val w = TimeWindowCodec.decode("11:00-14:00;16:30-23:00")
        assertEquals(2, w.size)
        assertEquals(660, w[0].startMinutes)   // 11:00
        assertEquals(990, w[1].startMinutes)   // 16:30
    }

    @Test
    fun `decode treats 24 00 as end of day`() {
        val w = TimeWindowCodec.decode("06:00-24:00")
        assertEquals(1, w.size)
        assertEquals(1440, w[0].endMinutes)
    }

    @Test
    fun `invalid times are rejected`() {
        assertTrue(TimeWindowCodec.decode("25:00-26:00").isEmpty())
        assertTrue(TimeWindowCodec.decode("08:00").isEmpty())
        assertTrue(TimeWindowCodec.decode("").isEmpty())
        // 结束早于开始视为无效
        assertTrue(TimeWindowCodec.decode("22:00-08:00").isEmpty())
    }

    @Test
    fun `encode then decode round trips`() {
        val original = listOf(FacilityWindow("07:30", "12:00"), FacilityWindow("14:00", "23:00"))
        val encoded = TimeWindowCodec.encode(original)
        assertEquals(original, TimeWindowCodec.decode(encoded))
    }

    @Test
    fun `day overrides round trip and always yield seven entries`() {
        val days = MutableList(7) { "" }
        days[0] = "16:30-23:00"
        val encoded = TimeWindowCodec.encodeDays(days)
        val decoded = TimeWindowCodec.decodeDays(encoded)
        assertEquals(7, decoded.size)
        assertEquals("16:30-23:00", decoded[0])
        assertEquals("", decoded[5])
    }

    @Test
    fun `decodeDays on blank input yields seven blanks`() {
        assertEquals(7, TimeWindowCodec.decodeDays(null).size)
        assertEquals(7, TimeWindowCodec.decodeDays("").size)
    }

    // ---------------- 浴室：周一上午停开 ----------------

    private fun findFacility(name: String): CampusFacility =
        DefaultFacilities.ALL.first { it.name == name }

    @Test
    fun `bathroom on monday opens only in the afternoon`() {
        val bath = findFacility("浴室开放")
        val monday = LocalDate.parse("2026-09-21") // 周一
        assertEquals(1, monday.dayOfWeek.value)

        val status = FacilityScheduleResolver.resolve(bath, monday, 12 * 60, holidayToday = false)
        assertEquals(1, status.windows.size)
        assertEquals("16:30-23:00", status.windows[0].label())
        // 中午 12:00 应当未开放
        assertFalse(status.isOpen)
        assertEquals("周一 安排", status.sourceLabel)
    }

    @Test
    fun `bathroom on tuesday has both windows`() {
        val bath = findFacility("浴室开放")
        val tuesday = LocalDate.parse("2026-09-22")
        assertEquals(2, tuesday.dayOfWeek.value)

        val noon = FacilityScheduleResolver.resolve(bath, tuesday, 12 * 60, holidayToday = false)
        assertTrue(noon.isOpen)
        assertEquals("11:00-14:00", noon.currentWindow?.label())

        val evening = FacilityScheduleResolver.resolve(bath, tuesday, 20 * 60, holidayToday = false)
        assertTrue(evening.isOpen)
        assertEquals("16:30-23:00", evening.currentWindow?.label())

        // 14:30-16:30 之间是关闭的
        val gap = FacilityScheduleResolver.resolve(bath, tuesday, 15 * 60, holidayToday = false)
        assertFalse(gap.isOpen)
        assertNotNull(gap.nextWindow)
        assertEquals("16:30-23:00", gap.nextWindow?.label())
    }

    // ---------------- 宿舍供电：周五周六延长 ----------------

    @Test
    fun `power closes at 23 15 on weekdays`() {
        val power = findFacility("宿舍供电")
        val wednesday = LocalDate.parse("2026-09-23")
        assertEquals(3, wednesday.dayOfWeek.value)

        val status = FacilityScheduleResolver.resolve(power, wednesday, 23 * 60 + 20, holidayToday = false)
        assertFalse("23:20 应当已断电", status.isOpen)

        val stillOn = FacilityScheduleResolver.resolve(power, wednesday, 23 * 60 + 10, holidayToday = false)
        assertTrue("23:10 仍应供电", stillOn.isOpen)
    }

    @Test
    fun `power extends to midnight on friday and saturday`() {
        val power = findFacility("宿舍供电")
        val friday = LocalDate.parse("2026-09-25")
        val saturday = LocalDate.parse("2026-09-26")
        assertEquals(5, friday.dayOfWeek.value)
        assertEquals(6, saturday.dayOfWeek.value)

        listOf(friday, saturday).forEach { date ->
            val status = FacilityScheduleResolver.resolve(power, date, 23 * 60 + 40, holidayToday = false)
            assertTrue("$date 23:40 应当仍供电", status.isOpen)
            assertEquals(1440, status.currentWindow?.endMinutes)
        }
    }

    @Test
    fun `power is off between midnight and 6am`() {
        val power = findFacility("宿舍供电")
        val monday = LocalDate.parse("2026-09-21")
        val status = FacilityScheduleResolver.resolve(power, monday, 3 * 60, holidayToday = false)
        assertFalse(status.isOpen)
        assertNotNull(status.nextWindow)
        assertEquals(6 * 60, status.nextWindow?.startMinutes)
    }

    // ---------------- 换桶机 / 吹风机 双段 ----------------

    @Test
    fun `change machine has a midday gap`() {
        val change = findFacility("换桶机")
        val monday = LocalDate.parse("2026-09-21")

        assertTrue(FacilityScheduleResolver.resolve(change, monday, 9 * 60, false).isOpen)
        assertFalse(FacilityScheduleResolver.resolve(change, monday, 13 * 60, false).isOpen)
        assertTrue(FacilityScheduleResolver.resolve(change, monday, 15 * 60, false).isOpen)
    }

    @Test
    fun `hair dryer runs late`() {
        val dryer = findFacility("吹风机")
        val monday = LocalDate.parse("2026-09-21")
        val status = FacilityScheduleResolver.resolve(dryer, monday, 23 * 60 + 30, false)
        assertTrue(status.isOpen)
    }

    // ---------------- 节假日覆盖 ----------------

    @Test
    fun `holiday windows take precedence when defined`() {
        val facility = CampusFacility(
            name = "测试设施",
            windows = "08:00-22:00",
            holidayWindows = "10:00-12:00"
        )
        val monday = LocalDate.parse("2026-09-21")

        val normal = FacilityScheduleResolver.resolve(facility, monday, 9 * 60, holidayToday = false)
        assertTrue(normal.isOpen)
        assertEquals("默认安排", normal.sourceLabel)

        val holiday = FacilityScheduleResolver.resolve(facility, monday, 9 * 60, holidayToday = true)
        assertFalse("节假日 9:00 不在 10:00-12:00 内", holiday.isOpen)
        assertEquals("节假日安排", holiday.sourceLabel)
        assertTrue(holiday.isHoliday)
    }

    @Test
    fun `holiday without override falls back to day schedule`() {
        val facility = CampusFacility(name = "测试", windows = "08:00-22:00")
        val monday = LocalDate.parse("2026-09-21")
        val status = FacilityScheduleResolver.resolve(facility, monday, 9 * 60, holidayToday = true)
        assertTrue(status.isOpen)
        assertEquals("默认安排", status.sourceLabel)
    }

    @Test
    fun `closed all day is reported`() {
        val facility = CampusFacility(name = "停用", windows = "", enabled = true)
        val monday = LocalDate.parse("2026-09-21")
        val status = FacilityScheduleResolver.resolve(facility, monday, 9 * 60, false)
        assertTrue(status.closedAllDay)
        assertFalse(status.isOpen)
        assertNull(status.nextWindow)
    }

    // ---------------- 时间提示 ----------------

    @Test
    fun `minutes until close and open are computed`() {
        val facility = CampusFacility(name = "测试", windows = "08:00-12:00;14:00-22:00")
        val monday = LocalDate.parse("2026-09-21")

        val open = FacilityScheduleResolver.resolve(facility, monday, 11 * 60, false)
        assertEquals(60, open.minutesUntilClose(11 * 60))

        val closed = FacilityScheduleResolver.resolve(facility, monday, 13 * 60, false)
        assertEquals(60, closed.minutesUntilOpen(13 * 60))
    }

    // ---------------- 默认数据完整性 ----------------

    @Test
    fun `all default facilities have valid windows`() {
        assertTrue(DefaultFacilities.ALL.isNotEmpty())
        DefaultFacilities.ALL.forEach { f ->
            val windows = TimeWindowCodec.decode(f.windows)
            assertTrue("${f.name} 应有有效时间", windows.isNotEmpty())
            windows.forEach { assertTrue("${f.name} 时间非法", it.isValid()) }
            // 按星期覆盖若有内容，也必须合法
            TimeWindowCodec.decodeDays(f.dayWindows).forEach { encoded ->
                TimeWindowCodec.decode(encoded).forEach { assertTrue(it.isValid()) }
            }
        }
    }

    @Test
    fun `expected default facilities are present`() {
        val names = DefaultFacilities.ALL.map { it.name }
        listOf(
            "公寓开关门", "宿舍供电", "浴室开放",
            "滚筒洗衣机", "烘干机", "换桶机", "洗鞋机", "吹风机", "饮水机", "自习室"
        ).forEach { expected ->
            assertTrue("缺少默认条目：$expected", names.contains(expected))
        }
    }

    @Test
    fun `machine floors match the dorm layout`() {
        fun locationOf(name: String) = DefaultFacilities.ALL.first { it.name == name }.location
        assertEquals("1-6 层", locationOf("滚筒洗衣机"))
        assertEquals("1 层", locationOf("烘干机"))
        assertEquals("2 层、5 层", locationOf("换桶机"))
        assertEquals("3 层", locationOf("洗鞋机"))
    }
}
