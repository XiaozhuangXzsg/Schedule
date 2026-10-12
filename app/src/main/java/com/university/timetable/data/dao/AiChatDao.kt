package com.university.timetable.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.university.timetable.data.entity.ChatMessage
import com.university.timetable.data.entity.ChatSession
import kotlinx.coroutines.flow.Flow

@Dao
interface AiChatDao {

    // ---------- 会话 ----------

    @Query("SELECT * FROM ai_sessions ORDER BY updatedAt DESC")
    fun observeSessions(): Flow<List<ChatSession>>

    @Query("SELECT * FROM ai_sessions WHERE id = :id")
    suspend fun getSession(id: Long): ChatSession?

    @Query("SELECT * FROM ai_sessions ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latestSession(): ChatSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSession): Long

    @Update
    suspend fun updateSession(session: ChatSession)

    @Query("DELETE FROM ai_sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("DELETE FROM ai_sessions")
    suspend fun clearSessions()

    @Query("UPDATE ai_sessions SET title = :title, updatedAt = :time WHERE id = :id")
    suspend fun renameSession(id: Long, title: String, time: Long)

    @Query("UPDATE ai_sessions SET updatedAt = :time WHERE id = :id")
    suspend fun touchSession(id: Long, time: Long)

    // ---------- 消息 ----------

    @Query("SELECT * FROM ai_messages WHERE sessionId = :sessionId ORDER BY id ASC")
    fun observeMessages(sessionId: Long): Flow<List<ChatMessage>>

    @Query("SELECT * FROM ai_messages WHERE sessionId = :sessionId ORDER BY id ASC")
    suspend fun getMessages(sessionId: Long): List<ChatMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage): Long

    @Update
    suspend fun updateMessage(message: ChatMessage)

    @Query("DELETE FROM ai_messages WHERE id = :id")
    suspend fun deleteMessage(id: Long)

    @Query("DELETE FROM ai_messages WHERE sessionId = :sessionId")
    suspend fun clearMessages(sessionId: Long)

    @Query("SELECT COUNT(*) FROM ai_messages WHERE sessionId = :sessionId")
    suspend fun countMessages(sessionId: Long): Int
}
