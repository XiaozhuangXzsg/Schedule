package com.university.timetable.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一节「课次」的时间定义。默认按国内高校常见作息写入，
 * 用户可在设置中自由修改（开始/结束时间精确到分钟）。
 *
 * 节次编号 [period] 从 1 开始，与 [CourseSession.startPeriod] 对应。
 */
@Entity(tableName = "period_times")
data class PeriodTime(
    @PrimaryKey
    val period: Int,
    /** 开始时间，例如 "08:00" */
    val startTime: String,
    /** 结束时间，例如 "08:50" */
    val endTime: String,
    /** 所属半天分组：1=上午 2=下午 3=晚上 */
    val section: Int = 1
) {
    companion object {
        fun parseMinutes(text: String): Int {
            val parts = text.trim().split(":", "：")
            if (parts.size < 2) return 0
            val h = parts[0].toIntOrNull() ?: 0
            val m = parts[1].toIntOrNull() ?: 0
            return h * 60 + m
        }
    }
}

/**
 * 节次时间的便捷换算。
 * 定义为顶层扩展（而非实体内部的计算属性），避免 Room 的 KSP 处理器报错。
 */
val PeriodTime.startMinutes: Int get() = PeriodTime.parseMinutes(startTime)

val PeriodTime.endMinutes: Int get() = PeriodTime.parseMinutes(endTime)

/**
 * 节假日 / 调休安排。
 *
 * - [isHoliday] = true 表示这天放假（不上课）
 * - [isMakeupDay] = true 表示这天调休补课，此时 [makeupDayOfWeek] 指定按周几的课表上课
 */
@Entity(
    tableName = "holidays",
    indices = [Index(value = ["date"], unique = true)]
)
data class Holiday(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    /** ISO 日期，格式 yyyy-MM-dd */
    val date: String,
    /** 名称，例如「国庆节」 */
    val name: String,
    val isHoliday: Boolean = true,
    /** 调休补课时按周几上课（1=周一 ... 7=周日），非调休为 null */
    val makeupDayOfWeek: Int? = null
) {
    val isMakeupDay: Boolean get() = makeupDayOfWeek != null
}
