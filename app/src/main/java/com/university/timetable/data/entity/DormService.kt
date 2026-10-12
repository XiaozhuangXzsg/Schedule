package com.university.timetable.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 宿舍生活信息：供电、浴室、洗衣机房等。
 * 由用户在「校园生活助手」中自行维护，今日页会按当前时间提示是否开放。
 */
@Entity(tableName = "dorm_services")
data class DormService(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    /** 名称，例如「宿舍供电」「浴室开放」「洗衣机房」 */
    val name: String,
    /** 类型：POWER / BATH / LAUNDRY / CUSTOM */
    val type: String = "CUSTOM",
    /** 开放开始时间 "06:00" */
    val openTime: String,
    /** 开放结束时间 "23:00" */
    val closeTime: String,
    /** 补充说明 */
    val note: String? = null,
    /** 适用星期，逗号分隔，例如 "1,2,3,4,5"；空表示每天 */
    val days: String = "",
    val enabled: Boolean = true
)
