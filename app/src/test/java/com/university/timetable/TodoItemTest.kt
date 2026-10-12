package com.university.timetable

import com.university.timetable.data.entity.TodoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 待办事项的图片编码 / 解析（最多 3 张）。
 */
class TodoItemTest {

    @Test
    fun `encode keeps at most three images`() {
        val encoded = TodoItem.encodeImages(listOf("a", "b", "c", "d"))
        assertEquals("a|b|c", encoded)
    }

    @Test
    fun `encode skips blank entries`() {
        val encoded = TodoItem.encodeImages(listOf("a", "  ", "", "b"))
        assertEquals("a|b", encoded)
    }

    @Test
    fun `images getter decodes stored value`() {
        val item = TodoItem(date = "2026-09-25", content = "交作业", imageUris = "u1|u2")
        assertEquals(listOf("u1", "u2"), item.images)
    }

    @Test
    fun `images getter on empty value returns empty list`() {
        val item = TodoItem(date = "2026-09-25", content = "无图")
        assertTrue(item.images.isEmpty())
    }

    @Test
    fun `images getter caps at three even if stored longer`() {
        val item = TodoItem(
            date = "2026-09-25",
            content = "多图",
            imageUris = "u1|u2|u3|u4|u5"
        )
        assertEquals(3, item.images.size)
        assertEquals(listOf("u1", "u2", "u3"), item.images)
    }

    @Test
    fun `round trip image list`() {
        val uris = listOf("content://a/1", "content://a/2", "content://a/3")
        val item = TodoItem(
            date = "2026-09-25",
            content = "往返",
            imageUris = TodoItem.encodeImages(uris)
        )
        assertEquals(uris, item.images)
    }

    @Test
    fun `encode with no images yields empty string`() {
        assertEquals("", TodoItem.encodeImages(emptyList()))
    }

    @Test
    fun `default todo is not done and has no images`() {
        val item = TodoItem(date = "2026-09-25", content = "默认")
        assertTrue(!item.done)
        assertTrue(item.images.isEmpty())
        assertEquals(TodoItem.MAX_IMAGES, 3)
    }
}
