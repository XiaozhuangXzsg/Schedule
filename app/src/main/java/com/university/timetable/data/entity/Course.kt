package com.university.timetable.data.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * 一门课程的主数据（名称 / 教师 / 备注 / 自定义颜色）。
 * 具体的上课时间与地点存放在 [CourseSession] 中，一门课可以有多个上课时段。
 */
@Entity(
    tableName = "courses",
    indices = [Index(value = ["name"], unique = false)]
)
data class Course(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    /** 课程名称，例如「高等代数（一）」 */
    val name: String,
    /** 授课教师，可为空 */
    val teacher: String? = null,
    /** 备注，可为空 */
    val note: String? = null,
    /** 自定义颜色（ARGB），为空时按课程名哈希自动取色 */
    val color: Int? = null,
    /** 课程类型标签，例如「课程」「实验」「通识」 */
    val category: String = "课程",
    /** 排序权重，越小越靠前 */
    val sortOrder: Int = 0
)

/**
 * 一次「上课安排」：某天的第几节到第几节、在哪些周重复、地点在哪里。
 *
 * 例如：高等代数（一）周一第 3-4 节 @天山堂A212，1-16 周（双周）。
 */
@Entity(
    tableName = "course_sessions",
    indices = [
        Index(value = ["courseId"]),
        Index(value = ["dayOfWeek"])
    ]
)
data class CourseSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    /** 关联的课程 id */
    val courseId: Long,
    /** 星期几：1=周一 ... 7=周日 */
    val dayOfWeek: Int,
    /** 开始节次，从 1 开始 */
    val startPeriod: Int,
    /** 结束节次（含） */
    val endPeriod: Int,
    /** 上课地点 */
    val location: String? = null,
    /** 起始周，默认第 1 周 */
    val startWeek: Int = 1,
    /** 结束周，默认第 20 周 */
    val endWeek: Int = 20,
    /** 单双周：0=每周 1=单周 2=双周 */
    val weekParity: Int = 0,
    /** 该时段是否启用（用于临时停课而不删除） */
    val enabled: Boolean = true
)

/** 单双周常量。 */
object WeekParity {
    const val EVERY = 0
    const val ODD = 1
    const val EVEN = 2

    fun label(parity: Int): String = when (parity) {
        ODD -> "单周"
        EVEN -> "双周"
        else -> "每周"
    }
}

/**
 * 课程记录的一体化视图（课程 + 时段），供 UI 直接消费。
 * 由 Room 的 `@Embedded` + `@Relation` 组装。
 */
data class CourseWithSessions(
    @Embedded val course: Course,
    @Relation(parentColumn = "id", entityColumn = "courseId")
    val sessions: List<CourseSession>
)

/** 展平后的单次上课条目，用于时间轴 / 周视图渲染。 */
data class ClassOccurrence(
    val sessionId: Long,
    val courseId: Long,
    val name: String,
    val teacher: String?,
    val location: String?,
    val category: String,
    val color: Int?,
    val dayOfWeek: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val startMinutes: Int,
    val endMinutes: Int,
    val weekParity: Int,
    /** 生效起始周 */
    val startWeek: Int = 1,
    /** 生效结束周 */
    val endWeek: Int = 20
)
