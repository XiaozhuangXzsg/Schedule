package com.university.timetable.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「已结束课程置灰」用的去饱和函数。
 *
 * 这里锁的是两条容易被后来的改动破坏的性质：
 * 1. `amount = 0` 必须原样返回（否则周视图/今日页的正常课程也会被改动）；
 * 2. 去饱和后三个通道要相等、且亮度与原色一致 —— 亮度一变，浅色主题下正文就会糊在底色上。
 */
class ColorExtTest {

    private val pastel = Color(0xFFF3D9E2)   // 某门课的浅色底
    private val dark = Color(0xFF2C3550)     // 夜间模式下的课程底

    @Test
    fun `zero amount leaves the color untouched`() {
        assertEquals(pastel, pastel.desaturated(0f))
        assertEquals(dark, dark.desaturated(0f))
    }

    @Test
    fun `full desaturation produces a true grey`() {
        val grey = pastel.desaturated(1f)
        assertEquals(grey.red, grey.green, 0.0001f)
        assertEquals(grey.green, grey.blue, 0.0001f)
    }

    @Test
    fun `luminance is preserved so contrast with the text survives`() {
        fun luminance(c: Color) = 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue
        // 容差不能取到 1e-4：Compose 的 Color 把分量打包成 **16 位半精度浮点**，
        // 0.88 附近的量化步长约 4.9e-4，重新构造一次就会引入这个量级的误差。
        val tolerance = 0.002f
        assertEquals(luminance(pastel), luminance(pastel.desaturated(0.9f)), tolerance)
        assertEquals(luminance(dark), luminance(dark.desaturated(0.9f)), tolerance)
    }

    @Test
    fun `alpha is never touched`() {
        val translucent = Color(red = 0.2f, green = 0.6f, blue = 0.9f, alpha = 0.4f)
        assertEquals(0.4f, translucent.desaturated(0.9f).alpha, 0.0001f)
    }

    @Test
    fun `partial desaturation pulls channels closer together`() {
        fun spread(c: Color) = maxOf(c.red, c.green, c.blue) - minOf(c.red, c.green, c.blue)
        assertTrue(spread(dark.desaturated(0.78f)) < spread(dark))
    }
}
