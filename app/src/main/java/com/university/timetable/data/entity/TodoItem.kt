package com.university.timetable.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 今日待办 / 备忘事项。
 *
 * 一个事项属于某一天（[date]，ISO 格式 yyyy-MM-dd），
 * 可以附带 1-3 张图片（[imageUris] 以 `|` 分隔的 content uri）。
 */
@Entity(
    tableName = "todo_items",
    indices = [Index(value = ["date"], unique = false)]
)
data class TodoItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    /** 所属日期，ISO 格式 yyyy-MM-dd */
    val date: String,
    /** 事项内容 */
    val content: String,
    /** 图片 uri，最多 3 张，以 | 分隔 */
    val imageUris: String = "",
    /** 是否已完成 */
    val done: Boolean = false,
    /** 排序权重 */
    val sortOrder: Int = 0,
    /** 创建时间戳，用于稳定排序 */
    val createdAt: Long = System.currentTimeMillis()
) {
    /** 解析出图片列表（最多 3 张）。 */
    val images: List<String>
        get() = imageUris.split(IMAGE_SEPARATOR)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(MAX_IMAGES)

    companion object {
        const val IMAGE_SEPARATOR = "|"
        const val MAX_IMAGES = 3

        /** 把图片列表编码为存储字符串。 */
        fun encodeImages(uris: List<String>): String =
            uris.map { it.trim() }
                .filter { it.isNotEmpty() }
                .take(MAX_IMAGES)
                .joinToString(IMAGE_SEPARATOR)
    }
}
