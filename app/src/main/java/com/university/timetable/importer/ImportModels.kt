package com.university.timetable.importer

import com.university.timetable.data.entity.Course
import com.university.timetable.data.entity.CourseSession

/**
 * 解析结果：课程 + 其所有上课时段。
 * [course] 的 id 为 0，由数据库写入时分配。
 */
data class ImportedCourse(
    val course: Course,
    val sessions: List<CourseSession>
)

/**
 * 一次导入任务的汇总结果。
 */
data class ImportResult(
    val courses: List<ImportedCourse>,
    val warnings: List<String> = emptyList(),
    val source: String = ""
) {
    val courseCount: Int get() = courses.size
    val sessionCount: Int get() = courses.sumOf { it.sessions.size }
    val isEmpty: Boolean get() = courses.isEmpty()
}

/** 导入来源类型。 */
enum class ImportSource(val label: String) {
    HTML("HTML 课表文件"),
    CSV("CSV 课表文件"),
    IMAGE_OCR("课表截图（OCR 识别）"),
    TEXT("粘贴课表文本")
}
