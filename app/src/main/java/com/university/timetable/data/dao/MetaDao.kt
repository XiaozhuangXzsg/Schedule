package com.university.timetable.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.university.timetable.data.entity.DormService
import com.university.timetable.data.entity.Holiday
import com.university.timetable.data.entity.PeriodTime
import com.university.timetable.data.entity.ScheduleOverride
import kotlinx.coroutines.flow.Flow

@Dao
interface PeriodTimeDao {

    @Query("SELECT * FROM period_times ORDER BY period ASC")
    fun observeAll(): Flow<List<PeriodTime>>

    @Query("SELECT * FROM period_times ORDER BY period ASC")
    suspend fun getAll(): List<PeriodTime>

    @Query("SELECT * FROM period_times WHERE period = :period")
    suspend fun getByPeriod(period: Int): PeriodTime?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<PeriodTime>)

    @Update
    suspend fun update(item: PeriodTime)

    /** 删除指定节次及之后的全部作息项（用于减少节数）。 */
    @Query("DELETE FROM period_times WHERE period >= :fromPeriod")
    suspend fun deleteFrom(fromPeriod: Int)

    @Query("SELECT COUNT(*) FROM period_times")
    suspend fun count(): Int
}

@Dao
interface HolidayDao {

    @Query("SELECT * FROM holidays ORDER BY date ASC")
    fun observeAll(): Flow<List<Holiday>>

    @Query("SELECT * FROM holidays ORDER BY date ASC")
    suspend fun getAll(): List<Holiday>

    @Query("SELECT * FROM holidays WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): Holiday?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<Holiday>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: Holiday): Long

    @Query("DELETE FROM holidays WHERE date = :date")
    suspend fun deleteByDate(date: String)

    @Query("DELETE FROM holidays")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM holidays")
    suspend fun count(): Int
}

@Dao
interface DormServiceDao {

    @Query("SELECT * FROM dorm_services ORDER BY id ASC")
    fun observeAll(): Flow<List<DormService>>

    @Query("SELECT * FROM dorm_services ORDER BY id ASC")
    suspend fun getAll(): List<DormService>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: DormService): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<DormService>)

    @Query("DELETE FROM dorm_services WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM dorm_services")
    suspend fun count(): Int
}

/**
 * 日期变更记录（调课 / 停课 / 补课 / 换教室）的读写入口。
 *
 * 这些记录与 [com.university.timetable.data.entity.CourseSession] 里的排课规则是**两套数据**：
 * 规则表保存「每周重复上什么」，本表保存「某一天额外发生什么」。
 */
@Dao
interface ScheduleOverrideDao {

    @Query("SELECT * FROM schedule_overrides ORDER BY date ASC, startPeriod ASC")
    fun observeAll(): Flow<List<ScheduleOverride>>

    @Query("SELECT * FROM schedule_overrides ORDER BY date ASC, startPeriod ASC")
    suspend fun getAll(): List<ScheduleOverride>

    @Query("SELECT * FROM schedule_overrides WHERE date = :date ORDER BY startPeriod ASC")
    suspend fun getByDate(date: String): List<ScheduleOverride>

    @Query("SELECT * FROM schedule_overrides WHERE date BETWEEN :from AND :to ORDER BY date ASC, startPeriod ASC")
    suspend fun getBetween(from: String, to: String): List<ScheduleOverride>

    /** 幂等查询：同一天、同一类型、同一来源时段是否已有记录。 */
    @Query("SELECT * FROM schedule_overrides WHERE date = :date AND type = :type AND sourceSessionId IS :sessionId LIMIT 1")
    suspend fun find(date: String, type: String, sessionId: Long?): ScheduleOverride?

    /** 临时加课的去重：同一天同名同节次只保留一条，避免重复执行同一指令产生重复课程。 */
    @Query(
        "SELECT * FROM schedule_overrides WHERE date = :date AND type = 'ADD' " +
            "AND name = :name AND startPeriod = :startPeriod AND endPeriod = :endPeriod LIMIT 1"
    )
    suspend fun findAdded(date: String, name: String, startPeriod: Int, endPeriod: Int): ScheduleOverride?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ScheduleOverride): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ScheduleOverride>)

    @Query("DELETE FROM schedule_overrides WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM schedule_overrides WHERE date = :date")
    suspend fun deleteByDate(date: String)

    @Query("DELETE FROM schedule_overrides WHERE sourceSessionId = :sessionId")
    suspend fun deleteBySource(sessionId: Long)

    @Query("DELETE FROM schedule_overrides")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM schedule_overrides")
    suspend fun count(): Int
}
