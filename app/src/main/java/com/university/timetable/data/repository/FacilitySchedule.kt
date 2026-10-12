package com.university.timetable.data.repository

import com.university.timetable.data.entity.CampusFacility
import com.university.timetable.data.entity.FacilityWindow
import com.university.timetable.data.entity.TimeWindowCodec
import java.time.LocalDate

/**
 * 设施的实时状态。所有判断都基于「今天」的日期与节假日情况，
 * 因此会随着系统日期自动变化。
 */
data class FacilityStatus(
    val facility: CampusFacility,
    /** 今天实际生效的开放区间（已应用星期 / 节假日覆盖） */
    val windows: List<FacilityWindow>,
    val isOpen: Boolean,
    /** 当前正在进行的区间 */
    val currentWindow: FacilityWindow?,
    /** 下一段即将开始的区间；今天已全部结束则为 null */
    val nextWindow: FacilityWindow?,
    /** 数据来源说明：「节假日安排」/「周X 安排」/「默认安排」 */
    val sourceLabel: String,
    val isHoliday: Boolean
) {
    /** 距离关闭还有多少分钟（正在开放时有效）。 */
    fun minutesUntilClose(nowMinutes: Int): Int? =
        currentWindow?.let { (it.endMinutes - nowMinutes).coerceAtLeast(0) }

    /** 距离下一次开放还有多少分钟。 */
    fun minutesUntilOpen(nowMinutes: Int): Int? =
        nextWindow?.let { (it.startMinutes - nowMinutes).coerceAtLeast(0) }

    /** 今天是否完全不开放。 */
    val closedAllDay: Boolean get() = windows.isEmpty()
}

/**
 * 依据「星期 + 节假日」解析设施今天的时间表并给出状态。
 */
object FacilityScheduleResolver {

    /**
     * @param holidayToday 今天是否为节假日（Holiday.isHoliday == true）
     */
    fun resolve(
        facility: CampusFacility,
        date: LocalDate,
        nowMinutes: Int,
        holidayToday: Boolean
    ): FacilityStatus {
        val dayIndex = date.dayOfWeek.value - 1 // 0=周一

        val dayOverrides = TimeWindowCodec.decodeDays(facility.dayWindows)
        val holidayWindows = TimeWindowCodec.decode(facility.holidayWindows)
        val dayWindows = TimeWindowCodec.decode(dayOverrides.getOrNull(dayIndex))

        val (windows, source) = when {
            holidayToday && holidayWindows.isNotEmpty() ->
                holidayWindows to "节假日安排"

            dayWindows.isNotEmpty() ->
                dayWindows to "${dayLabel(date.dayOfWeek.value)} 安排"

            else -> TimeWindowCodec.decode(facility.windows) to "默认安排"
        }

        val sorted = windows.sortedBy { it.startMinutes }
        val current = sorted.firstOrNull { it.contains(nowMinutes) }
        val next = sorted.firstOrNull { it.startMinutes > nowMinutes }

        return FacilityStatus(
            facility = facility,
            windows = sorted,
            isOpen = current != null,
            currentWindow = current,
            nextWindow = next,
            sourceLabel = source,
            isHoliday = holidayToday
        )
    }

    fun dayLabel(dayOfWeek: Int): String = when (dayOfWeek) {
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        else -> "周日"
    }
}
