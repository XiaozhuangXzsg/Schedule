package com.university.timetable.data.seed

import com.university.timetable.data.entity.Course
import com.university.timetable.data.entity.CourseSession
import com.university.timetable.data.entity.DormService
import com.university.timetable.data.entity.Holiday
import com.university.timetable.data.entity.WeekParity

/**
 * 首次启动写入的示例课表。
 *
 * 说明：作息时间（节次起止）与课程安排取自用户提供的课表截图 / HTML，
 * 作为时间轴与周视图的排版参考。教师姓名与部分地点在截图中不够清晰，
 * 已标注为「待确认」或留空，用户可在应用内一键修改或清空后导入自己的课表。
 */
object SeedSchedule {

    /** 第 1 周的周一（2026-2027 学年第一学期，第 4 周 = 2026-09-21 ~ 09-27）。 */
    const val SEMESTER_START = "2026-08-31"

    const val TOTAL_WEEKS = 20

    private fun buildSessions(
        courseId: Long,
        specs: List<SessionSpec>
    ): List<CourseSession> = specs.map { spec ->
        CourseSession(
            courseId = courseId,
            dayOfWeek = spec.day,
            startPeriod = spec.start,
            endPeriod = spec.end,
            location = spec.location,
            startWeek = spec.startWeek,
            endWeek = spec.endWeek,
            weekParity = spec.parity
        )
    }

    data class SessionSpec(
        val day: Int,
        val start: Int,
        val end: Int,
        val location: String?,
        val startWeek: Int = 1,
        val endWeek: Int = 18,
        val parity: Int = WeekParity.EVERY
    )

    data class SeedEntry(val course: Course, val specs: List<SessionSpec>)

    /** 示例课程集合。courseId 由写入顺序决定，这里用 0 占位。 */
    val entries: List<SeedEntry> = listOf(
        SeedEntry(
            Course(name = "高等代数（一）", teacher = "林艳", category = "课程", sortOrder = 1),
            listOf(
                SessionSpec(1, 3, 4, "天山堂 A212", endWeek = 18),
                SessionSpec(4, 3, 4, "天山堂 B307", endWeek = 18)
            )
        ),
        SeedEntry(
            Course(name = "数学分析（一）", teacher = "赵静", category = "课程", sortOrder = 2),
            listOf(
                SessionSpec(1, 5, 6, "天山堂 A303", endWeek = 18),
                SessionSpec(3, 5, 6, "天山堂 A303", endWeek = 18),
                SessionSpec(5, 1, 2, "天山堂 A303", endWeek = 18),
                SessionSpec(7, 5, 6, "天山堂 A303", endWeek = 18)
            )
        ),
        SeedEntry(
            Course(name = "解析几何", teacher = "杨帆", category = "课程", sortOrder = 3),
            listOf(
                SessionSpec(2, 1, 2, "天山堂 A408", endWeek = 18),
                SessionSpec(5, 1, 2, "天山堂 A408", endWeek = 18),
                SessionSpec(7, 1, 2, "天山堂 A408", endWeek = 18)
            )
        ),
        SeedEntry(
            Course(name = "高级英语1", teacher = "李欣", category = "课程", sortOrder = 4),
            listOf(
                SessionSpec(1, 3, 4, "天山堂 A212", endWeek = 18, parity = WeekParity.EVEN),
                SessionSpec(4, 3, 4, "天山堂 A212", endWeek = 18, parity = WeekParity.EVEN)
            )
        ),
        SeedEntry(
            Course(name = "大学物理", teacher = "张文博", category = "课程", sortOrder = 5),
            listOf(SessionSpec(2, 1, 2, "电教 202", endWeek = 16))
        ),
        SeedEntry(
            Course(name = "概率与数学统计", teacher = "陈曦", category = "课程", sortOrder = 6),
            listOf(SessionSpec(2, 3, 4, "主教 404", endWeek = 16))
        ),
        SeedEntry(
            Course(name = "中国近代史纲要", teacher = "何伟", category = "思政", sortOrder = 7),
            listOf(SessionSpec(2, 7, 8, "厚德楼 B305", endWeek = 16))
        ),
        SeedEntry(
            Course(name = "计算机基础与C语言", teacher = "杨皓", category = "课程", sortOrder = 8),
            listOf(
                SessionSpec(1, 7, 8, "天山堂 A605", endWeek = 18, parity = WeekParity.EVEN),
                SessionSpec(7, 7, 8, "天山堂 A605", endWeek = 18, parity = WeekParity.EVEN),
                SessionSpec(1, 7, 8, "网络中心机房", endWeek = 14)
            )
        ),
        SeedEntry(
            Course(name = "习近平新时代中国特色社会主义思想概论", teacher = "王敏", category = "思政", sortOrder = 9),
            listOf(
                SessionSpec(2, 7, 8, "天山堂 A305", endWeek = 18, parity = WeekParity.ODD),
                SessionSpec(5, 7, 8, "天山堂 A305", endWeek = 18, parity = WeekParity.ODD)
            )
        ),
        SeedEntry(
            Course(name = "职业生涯发展与规划 1", teacher = "刘畅", category = "通识", sortOrder = 10),
            listOf(
                SessionSpec(1, 9, 10, "天山堂 C201", endWeek = 18, parity = WeekParity.ODD),
                SessionSpec(5, 9, 10, "天山堂 C201", endWeek = 18, parity = WeekParity.ODD)
            )
        ),
        SeedEntry(
            Course(name = "生物多样性与保护（通识）", teacher = "任东", category = "通识", sortOrder = 11),
            listOf(
                SessionSpec(4, 9, 10, "秦岭堂 B304", endWeek = 10),
                SessionSpec(5, 9, 10, "秦岭堂 B304", endWeek = 10)
            )
        ),
        SeedEntry(
            Course(name = "体育（1/4）", teacher = "孙鹏", category = "体育", sortOrder = 12),
            listOf(SessionSpec(5, 9, 10, "游泳馆", endWeek = 16))
        ),
        SeedEntry(
            Course(name = "大学生心理健康（网络共享课）", teacher = null, category = "通识", sortOrder = 13),
            listOf(
                SessionSpec(2, 3, 4, "网络共享课", endWeek = 16, parity = WeekParity.ODD),
                SessionSpec(5, 3, 4, "网络共享课", endWeek = 16, parity = WeekParity.ODD)
            )
        ),
        SeedEntry(
            Course(name = "Blockly 创意趣味编程（网络共享课）", teacher = "周国庆", category = "通识", sortOrder = 14),
            listOf(SessionSpec(4, 1, 2, "网络共享课", endWeek = 18, parity = WeekParity.ODD))
        ),
        SeedEntry(
            Course(name = "军事理论", teacher = "李强", category = "课程", sortOrder = 15),
            listOf(SessionSpec(4, 7, 8, "电教 501", endWeek = 16))
        )
    )

    fun courseSessions(courseId: Long, specs: List<SessionSpec>): List<CourseSession> =
        buildSessions(courseId, specs)

    /** 默认校园生活服务信息（可在应用内修改）。 */
    val dormServices: List<DormService> = listOf(
        DormService(
            id = 0, name = "宿舍供电", type = "POWER",
            openTime = "06:00", closeTime = "23:30",
            note = "周五、周六延长至 24:00（以宿管通知为准）"
        ),
        DormService(
            id = 0, name = "浴室开放", type = "BATH",
            openTime = "11:30", closeTime = "22:30",
            note = "高峰时段 20:00-22:00，建议错峰"
        ),
        DormService(
            id = 0, name = "洗衣机房", type = "LAUNDRY",
            openTime = "07:00", closeTime = "22:00",
            note = "单次洗涤约 40 分钟，可用内置倒计时提醒"
        )
    )

    /**
     * 2026 年部分法定节假日（示意，可在应用内编辑）。
     * 仅包含与本学期相关的日期。
     */
    val holidays: List<Holiday> = listOf(
        Holiday(date = "2026-10-01", name = "国庆节", isHoliday = true),
        Holiday(date = "2026-10-02", name = "国庆节", isHoliday = true),
        Holiday(date = "2026-10-03", name = "国庆节", isHoliday = true),
        Holiday(date = "2026-10-04", name = "国庆节", isHoliday = true),
        Holiday(date = "2026-10-05", name = "国庆节", isHoliday = true),
        Holiday(date = "2026-10-06", name = "国庆节", isHoliday = true),
        Holiday(date = "2026-10-07", name = "国庆节", isHoliday = true),
        Holiday(date = "2026-09-26", name = "国庆调休补课", isHoliday = false, makeupDayOfWeek = 5),
        Holiday(date = "2026-10-10", name = "国庆调休补课", isHoliday = false, makeupDayOfWeek = 4)
    )
}
