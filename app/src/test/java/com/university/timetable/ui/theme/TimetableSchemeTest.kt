package com.university.timetable.ui.theme

import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 配色来源的测试。
 *
 * 桌面小组件的卡片底色 / 文字色现在直接取自 [timetableScheme]，
 * 所以这里等价于在验证「小卡片的主题和软件内同步」这件事本身的逻辑：
 * 换主题色、切深浅色、开关哥伦比娅风格，算出来的颜色必须真的不一样。
 */
class TimetableSchemeTest {

    @Test
    fun `切换主题色会改变主色`() {
        val first = timetableScheme(dark = false, presetIndex = 0, columbina = false)
        val second = timetableScheme(dark = false, presetIndex = 2, columbina = false)

        assertNotEquals(
            "不同主题色预设必须给出不同主色",
            first.colorScheme.primary,
            second.colorScheme.primary
        )
    }

    @Test
    fun `切换深浅色会改变卡片底色`() {
        val light = timetableScheme(dark = false, presetIndex = 0, columbina = false)
        val dark = timetableScheme(dark = true, presetIndex = 0, columbina = false)

        assertNotEquals(
            "深浅色必须给出不同卡片底色",
            light.extras.surfaceCard,
            dark.extras.surfaceCard
        )
    }

    @Test
    fun `哥伦比娅风格优先于自选主题色`() {
        val plain = timetableScheme(dark = true, presetIndex = 0, columbina = false)
        val columbina = timetableScheme(dark = true, presetIndex = 0, columbina = true)

        assertNotEquals(
            "开启哥伦比娅风格后配色必须变化",
            plain.colorScheme.primary,
            columbina.colorScheme.primary
        )
        // 两种深色下的卡片底色也不同：这是小组件跟随风格的关键
        assertNotEquals(plain.extras.surfaceCard, columbina.extras.surfaceCard)
    }

    @Test
    fun `浅色下卡片底色为白 深色下为深色`() {
        val light = timetableScheme(dark = false, presetIndex = 0, columbina = false)
        val dark = timetableScheme(dark = true, presetIndex = 0, columbina = false)

        assertEquals("浅色卡片应为白色", 0xFFFFFFFF.toInt(), light.extras.surfaceCard.toArgb())
        assertTrue(
            "深色卡片亮度应明显更低",
            dark.extras.surfaceCard.red + dark.extras.surfaceCard.green +
                dark.extras.surfaceCard.blue < 1.0f
        )
    }

    @Test
    fun `正文字色与卡片底色在两种模式下都可区分`() {
        listOf(false, true).forEach { dark ->
            val scheme = timetableScheme(dark, presetIndex = 0, columbina = false)
            assertNotEquals(
                "深浅色=${dark} 时卡片底色与正文色不能相同",
                scheme.colorScheme.onSurface,
                scheme.extras.surfaceCard
            )
        }
    }
}
