package com.university.timetable.data.repository

import com.university.timetable.data.entity.Holiday
import java.time.LocalDate

/**
 * 「某一天」的完整状态快照。
 *
 * 页面（今日页 / 校园生活）与桌面小组件都从同一个快照读取，
 * 保证「今天有没有课 / 今天是不是放假」这类判断在任何地方都是同一个答案。
 *
 * 这里刻意同时保留**两套课表**：
 * - [baseBlocks]（基础课表）：只按排课规则算，「这一天原本应该上什么」。
 *   放假**不会**让它变空 —— 否则 AI 就查不到「10 月 7 日原本要上的课」，也就无法调课。
 * - [blocks]（实际课表）：在基础课表上叠加放假 / 停课 / 调课 / 补课 / 换教室后的最终结果。
 *
 * 界面、提醒、桌面组件用 [blocks]；AI 查询与调课以 [baseBlocks] 为准。
 */
data class DaySnapshot(
    val date: LocalDate,
    /** 该日期落在第几教学周；< 1 表示学期尚未开始 */
    val week: Int,
    val holiday: Holiday?,
    /** 该日期实际生效的星期（调休补课会改写，放假为 null） */
    val effectiveDayOfWeek: Int?,
    /** 当天实际要上的课（已按单双周 / 起止周过滤并按时间排序） */
    val blocks: List<ClassBlock>,
    /**
     * 基础课表：按学期起始周、星期、节次、单双周、课程有效周次算出的「原本应上的课」。
     *
     * 不受放假影响（调休补课仍会改写星期），也不含任何日期变更记录。
     */
    val baseBlocks: List<ClassBlock> = emptyList(),
    /** 当天生效的日期变更记录（调课 / 停课 / 补课 / 换教室），用于解释「为什么今天和规则不一样」。 */
    val overrides: List<com.university.timetable.data.entity.ScheduleOverride> = emptyList()
) {
    val isHoliday: Boolean get() = holiday?.isHoliday == true
    val isMakeupDay: Boolean get() = holiday?.isMakeupDay == true

    /** 当天是否有任何变更（停课 / 调课 / 补课 / 换教室）。 */
    val hasOverrides: Boolean get() = overrides.isNotEmpty()

    /** 基础课表里有课、但实际课表为空 —— 典型的「放假停课」或「整天调出」。 */
    val suppressedByOverride: Boolean get() = baseBlocks.isNotEmpty() && blocks.isEmpty()

    companion object {
        /** 学期尚未开始 / 数据还没加载时的空快照。 */
        fun empty(date: LocalDate) = DaySnapshot(
            date = date,
            week = 0,
            holiday = null,
            effectiveDayOfWeek = null,
            blocks = emptyList()
        )
    }
}

/**
 * 「某一天要上什么课」的纯逻辑。
 *
 * 抽成不依赖 Android / Room 的纯函数，是为了能用 JVM 单元测试直接覆盖
 * 「放假停课 / 调休补课 / 单双周 / 起止周」这几种最容易出错的组合。
 */
object DayResolver {

    /**
     * 该日期实际生效的星期。
     *
     * - 无节假日记录：按自然星期
     * - 调休补课（[Holiday.isMakeupDay]）：按 [Holiday.makeupDayOfWeek] 上课
     * - 放假（[Holiday.isHoliday]）：返回 null，表示当天不上课
     */
    fun effectiveDayOfWeek(date: LocalDate, holiday: Holiday?): Int? = when {
        holiday == null -> date.dayOfWeek.value
        holiday.isMakeupDay -> holiday.makeupDayOfWeek
        holiday.isHoliday -> null
        else -> date.dayOfWeek.value
    }

    /**
     * 基础课表用的星期：**忽略「放假」**，只看调休补课。
     *
     * 放假日（如国庆 10-07 周三）依然返回周三，
     * 这样 AI 才能查到「当天原本应该上的课」，进而执行调课。
     */
    fun baseDayOfWeek(date: LocalDate, holiday: Holiday?): Int = when {
        holiday != null && holiday.isMakeupDay -> holiday.makeupDayOfWeek ?: date.dayOfWeek.value
        else -> date.dayOfWeek.value
    }

    /** 从全量节假日里找出某一天的记录。 */
    fun holidayOn(date: LocalDate, holidays: List<Holiday>): Holiday? =
        holidays.firstOrNull { it.date == date.toString() }
}
