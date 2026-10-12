package com.university.timetable.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.university.timetable.data.entity.CampusFacility
import kotlinx.coroutines.flow.Flow

@Dao
interface CampusFacilityDao {

    @Query("SELECT * FROM campus_facilities ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<CampusFacility>>

    @Query("SELECT * FROM campus_facilities WHERE enabled = 1 ORDER BY sortOrder ASC, id ASC")
    fun observeEnabled(): Flow<List<CampusFacility>>

    @Query("SELECT * FROM campus_facilities ORDER BY sortOrder ASC, id ASC")
    suspend fun getAll(): List<CampusFacility>

    @Query("SELECT * FROM campus_facilities WHERE id = :id")
    suspend fun getById(id: Long): CampusFacility?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CampusFacility): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CampusFacility>)

    @Update
    suspend fun update(item: CampusFacility)

    @Delete
    suspend fun delete(item: CampusFacility)

    @Query("DELETE FROM campus_facilities WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM campus_facilities")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM campus_facilities")
    suspend fun count(): Int
}
