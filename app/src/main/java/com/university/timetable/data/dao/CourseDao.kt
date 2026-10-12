package com.university.timetable.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Transaction
import androidx.room.Update
import com.university.timetable.data.entity.ClassOccurrence
import com.university.timetable.data.entity.Course
import com.university.timetable.data.entity.CourseSession
import com.university.timetable.data.entity.CourseWithSessions
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {

    // ---------- 课程 ----------

    @Query("SELECT * FROM courses ORDER BY sortOrder ASC, id ASC")
    fun observeCourses(): Flow<List<Course>>

    @Query("SELECT * FROM courses ORDER BY sortOrder ASC, id ASC")
    suspend fun getCourses(): List<Course>

    @Query("SELECT * FROM courses WHERE id = :id")
    suspend fun getCourse(id: Long): Course?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourse(course: Course): Long

    @Update
    suspend fun updateCourse(course: Course)

    @Delete
    suspend fun deleteCourse(course: Course)

    @Query("DELETE FROM courses")
    suspend fun clearCourses()

    // ---------- 上课时段 ----------

    @Query("SELECT * FROM course_sessions ORDER BY dayOfWeek ASC, startPeriod ASC")
    fun observeSessions(): Flow<List<CourseSession>>

    @Query("SELECT * FROM course_sessions ORDER BY dayOfWeek ASC, startPeriod ASC")
    suspend fun getSessions(): List<CourseSession>

    @Query("SELECT * FROM course_sessions WHERE courseId = :courseId")
    suspend fun getSessionsOfCourse(courseId: Long): List<CourseSession>

    @Query("SELECT * FROM course_sessions WHERE dayOfWeek = :dayOfWeek AND enabled = 1")
    suspend fun getSessionsOfDay(dayOfWeek: Int): List<CourseSession>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: CourseSession): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<CourseSession>)

    @Update
    suspend fun updateSession(session: CourseSession)

    @Delete
    suspend fun deleteSession(session: CourseSession)

    @Query("DELETE FROM course_sessions WHERE courseId = :courseId")
    suspend fun deleteSessionsOfCourse(courseId: Long)

    @Query("DELETE FROM course_sessions WHERE dayOfWeek = :dayOfWeek")
    suspend fun deleteSessionsOfDay(dayOfWeek: Int)

    @Query("DELETE FROM course_sessions")
    suspend fun clearSessions()

    /** 写入课程及其所有时段（同一事务）。 */
    @Transaction
    suspend fun insertCourseWithSessions(course: Course, sessions: List<CourseSession>): Long {
        val id = insertCourse(course)
        insertSessions(sessions.map { it.copy(courseId = id) })
        return id
    }

    /** 覆盖式更新：替换课程信息与全部时段。 */
    @Transaction
    suspend fun replaceCourseWithSessions(course: Course, sessions: List<CourseSession>) {
        updateCourse(course)
        deleteSessionsOfCourse(course.id)
        insertSessions(sessions.map { it.copy(courseId = course.id) })
    }

    // ---------- 联表查询 ----------

    @Transaction
    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM courses ORDER BY sortOrder ASC, id ASC")
    fun observeCoursesWithSessions(): Flow<List<CourseWithSessions>>

    @Transaction
    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM courses ORDER BY sortOrder ASC, id ASC")
    suspend fun getCoursesWithSessions(): List<CourseWithSessions>

    /**
     * 展平为可直接渲染的上课条目（课程 + 时段）。
     * 时间由 UI 依据 period_times 表换算，这里只取节次。
     */
    @Query(
        """
        SELECT s.id AS sessionId,
               c.id AS courseId,
               c.name AS name,
               c.teacher AS teacher,
               s.location AS location,
               c.category AS category,
               c.color AS color,
               s.dayOfWeek AS dayOfWeek,
               s.startPeriod AS startPeriod,
               s.endPeriod AS endPeriod,
               0 AS startMinutes,
               0 AS endMinutes,
               s.weekParity AS weekParity,
               s.startWeek AS startWeek,
               s.endWeek AS endWeek
        FROM course_sessions s
        INNER JOIN courses c ON c.id = s.courseId
        WHERE s.enabled = 1
        ORDER BY s.dayOfWeek ASC, s.startPeriod ASC
        """
    )
    fun observeOccurrences(): Flow<List<ClassOccurrence>>

    @Query(
        """
        SELECT s.id AS sessionId,
               c.id AS courseId,
               c.name AS name,
               c.teacher AS teacher,
               s.location AS location,
               c.category AS category,
               c.color AS color,
               s.dayOfWeek AS dayOfWeek,
               s.startPeriod AS startPeriod,
               s.endPeriod AS endPeriod,
               0 AS startMinutes,
               0 AS endMinutes,
               s.weekParity AS weekParity,
               s.startWeek AS startWeek,
               s.endWeek AS endWeek
        FROM course_sessions s
        INNER JOIN courses c ON c.id = s.courseId
        WHERE s.enabled = 1
        ORDER BY s.dayOfWeek ASC, s.startPeriod ASC
        """
    )
    suspend fun getOccurrences(): List<ClassOccurrence>
}
