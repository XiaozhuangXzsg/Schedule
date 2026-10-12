package com.university.timetable.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 聊天消息角色。 */
object ChatRole {
    const val USER = "user"
    const val ASSISTANT = "assistant"
    const val SYSTEM = "system"
    /** 工具执行结果（回传给模型用） */
    const val TOOL = "tool"
}

/**
 * 一条聊天消息。
 *
 * - [images] / [files] 保存用户上传内容的本地 uri（以 `|` 分隔）
 * - [pendingAction] 保存「待用户确认的工具调用」JSON，确认后执行
 */
@Entity(
    tableName = "ai_messages",
    indices = [Index(value = ["sessionId"], unique = false)]
)
data class ChatMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    /** 所属会话 id */
    val sessionId: Long,
    /** 见 [ChatRole] */
    val role: String,
    /** 文本内容 */
    val content: String = "",
    /** 图片 uri，以 | 分隔 */
    val images: String = "",
    /** 文件 uri，以 | 分隔 */
    val files: String = "",
    /** 附件文件名（与 [files] 顺序对应，以 | 分隔） */
    val fileNames: String = "",
    /** 待确认的工具调用 JSON（仅 assistant 消息可能非空） */
    val pendingAction: String = "",
    /** 该动作是否已被用户处理（确认/拒绝） */
    val actionResolved: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    val imageList: List<String> get() = splitTo(images)
    val fileList: List<String> get() = splitTo(files)
    val fileNameList: List<String> get() = splitTo(fileNames)

    private fun splitTo(raw: String): List<String> =
        raw.split('|').map { it.trim() }.filter { it.isNotEmpty() }
}

/** 一个会话。 */
@Entity(tableName = "ai_sessions")
data class ChatSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
