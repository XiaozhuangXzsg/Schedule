package com.university.timetable.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 日期变更记录的种类。
 *
 * 关键设计：**基础课表（排课规则）与实际课表（某天最终上什么课）分开保存**。
 * [CourseSession] 保存的是「每周重复的排课规则」，本表保存的是「只影响某一天的变更」，
 * 因此调课、停课、补课永远不会删除或覆盖原始排课规则。
 */
enum class OverrideType {
    /** 整天停课（节假日之外的临时停课）。 */
    CANCEL,

    /** 调出：某天某个时段被移到别的日期，本日不再上。 */
    MOVE_OUT,

    /** 调入：从别的日期移过来的课，在本日上。 */
    MOVE_IN,

    /** 临时加课 / 补课：不属于任何排课规则，只在某一日出现。 */
    ADD,

    /** 换教室：只改某一天的教室，不改排课规则。 */
    ROOM;

    companion object {
        fun from(raw: String?): OverrideType? =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
    }
}

/**
 * 某一天的课表变更记录（实际课表的「补丁」）。
 *
 * - [MOVE_OUT] / [MOVE_IN] 成对出现：前者标记源日期不再上，后者把课程快照落到目标日期；
 * - [sourceSessionId] 指向 [CourseSession.id]，是幂等的关键 ——
 *   同一条「把 A 的课调到 B」指令重复执行时，靠 (date, type, sourceSessionId) 判断已经做过，不会重复添加；
 * - 课程名称 / 教师 / 教室 / 节次都做成**快照**存下来，
 *   这样即使之后改了排课规则，已经调过去的课也不会跟着变（符合「调课只影响指定日期」的预期）。
 */
@Entity(
    tableName = "schedule_overrides",
    indices = [
        Index(value = ["date"]),
        Index(value = ["type"]),
        Index(value = ["sourceSessionId"]),
        Index(value = ["date", "type", "sourceSessionId"])
    ]
)
data class ScheduleOverride(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** 生效日期，ISO 格式 yyyy-MM-dd */
    val date: String,

    /** [OverrideType] 的名字。存字符串，避免为了一个枚举引入 TypeConverter。 */
    val type: String,

    /** 来源上课时段（[CourseSession.id]）；[OverrideType.ADD] 没有来源，为 null */
    val sourceSessionId: Long? = null,

    /** 来源课程（[Course.id]）；仅用于追溯，可为 null */
    val courseId: Long? = null,

    val name: String? = null,
    val teacher: String? = null,
    val location: String? = null,
    val color: Int? = null,
    val startPeriod: Int? = null,
    val endPeriod: Int? = null,

    /** 说明文字，例如「由 2026-10-07 调至本日」。空状态与冲突提示会用到。 */
    val note: String? = null,

    val createdAt: Long = System.currentTimeMillis()
) {
    val overrideType: OverrideType? get() = OverrideType.from(type)

    /** 幂等键：同一天、同一类型、同一来源时段只应存在一条。 */
    fun idempotencyKey(): String = "$date|$type|${sourceSessionId ?: "-"}"
}
