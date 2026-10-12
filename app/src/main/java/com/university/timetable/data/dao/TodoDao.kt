package com.university.timetable.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.university.timetable.data.entity.TodoItem
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {

    /** 全部待办，按日期与创建时间排序。 */
    @Query("SELECT * FROM todo_items ORDER BY date ASC, sortOrder ASC, createdAt ASC")
    fun observeAll(): Flow<List<TodoItem>>

    /** 某一天的待办。 */
    @Query("SELECT * FROM todo_items WHERE date = :date ORDER BY sortOrder ASC, createdAt ASC")
    fun observeByDate(date: String): Flow<List<TodoItem>>

    @Query("SELECT * FROM todo_items WHERE date = :date ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun getByDate(date: String): List<TodoItem>

    /** 从某天开始（含）的待办，用于「今日及后面几天」。 */
    @Query("SELECT * FROM todo_items WHERE date >= :fromDate ORDER BY date ASC, sortOrder ASC, createdAt ASC")
    suspend fun getFromDate(fromDate: String): List<TodoItem>

    @Query("SELECT * FROM todo_items WHERE id = :id")
    suspend fun getById(id: Long): TodoItem?

    /** 未来若干天内有待办的日期（用于日历标记）。 */
    @Query("SELECT DISTINCT date FROM todo_items WHERE date >= :fromDate ORDER BY date ASC")
    suspend fun datesWithTodo(fromDate: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: TodoItem): Long

    @Update
    suspend fun update(item: TodoItem)

    @Delete
    suspend fun delete(item: TodoItem)

    @Query("DELETE FROM todo_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM todo_items WHERE date = :date")
    suspend fun deleteByDate(date: String)

    @Query("SELECT COUNT(*) FROM todo_items")
    suspend fun count(): Int
}
