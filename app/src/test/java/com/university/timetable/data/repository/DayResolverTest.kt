package com.university.timetable.data.repository

import com.university.timetable.data.entity.Holiday
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * 「某一天实际按周几上课」的规则测试。
 *
 * 今日页、周视图、桌面小组件现在都走 [DayResolver]，
 * 这里把最容易出错的三种情况（放假 / 调休补课 / 普通日）钉死。
 */
class DayResolverTest {

    private fun holiday(
        date: String,
        name: String = "测试",
        isHoliday: Boolean = true,
        makeup: Int? = null
    ) = Holiday(id = 0, date = date, name = name, isHoliday = isHoliday, makeupDayOfWeek = makeup)

    @Test
    fun `没有节假日记录时按自然星期`() {
        // 2026-10-07 是周三
        val wednesday = LocalDate.of(2026, 10, 7)
        assertEquals(3, DayResolver.effectiveDayOfWeek(wednesday, null))
    }

    @Test
    fun `放假当天不上课`() {
        val date = LocalDate.of(2026, 10, 1) // 周四
        val record = holiday("2026-10-01", "国庆节")
        assertNull(DayResolver.effectiveDayOfWeek(date, record))
    }

    @Test
    fun `调休补课时按指定的星期上课`() {
        // 2026-10-10 是周六，但调休按周五(5)上课
        val saturday = LocalDate.of(2026, 10, 10)
        val record = holiday("2026-10-10", "调休", isHoliday = false, makeup = 5)
        assertEquals(5, DayResolver.effectiveDayOfWeek(saturday, record))
    }

    @Test
    fun `既有调休标记又有放假标记时优先按调休上课`() {
        // 与旧实现保持一致的优先级：先判断 isMakeupDay，再判断 isHoliday
        val date = LocalDate.of(2026, 10, 10)
        val record = holiday("2026-10-10", "矛盾数据", isHoliday = true, makeup = 2)
        assertEquals(2, DayResolver.effectiveDayOfWeek(date, record))
    }

    @Test
    fun `holidayOn 只匹配同一天`() {
        val list = listOf(
            holiday("2026-10-01", "国庆节"),
            holiday("2026-10-10", "调休", isHoliday = false, makeup = 5)
        )
        assertEquals("调休", DayResolver.holidayOn(LocalDate.of(2026, 10, 10), list)?.name)
        assertNull(DayResolver.holidayOn(LocalDate.of(2026, 10, 2), list))
    }
}
