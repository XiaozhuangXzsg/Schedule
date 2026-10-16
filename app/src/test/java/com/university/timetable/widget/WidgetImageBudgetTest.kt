package com.university.timetable.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Android 17 卡片位图内存预算的回归测试。
 *
 * 锁住这条真实风险：targetSdk 37 起，系统对 RemoteViews 里 Bitmap/Icon 的**总内存**施加硬限制
 * `1.5 × 屏宽 × 屏高 × 4` 字节，超了会抛致命 `IllegalArgumentException` **直接杀进程**。
 * 待办卡片最多放 3 张图，若沿用应用内的 1440px 上限，在 1272×2772 的机器上就会超限。
 */
class WidgetImageBudgetTest {

    /** 本机（OnePlus 15，1272×2772）的 Android 17 上限，用来锁定量级。 */
    private val onePlusLimit = WidgetImageBudget.remoteViewsLimitBytes(1272, 2772)

    /** 上限公式本身：1.5 × 宽 × 高 × 4。 */
    @Test
    fun `android 17 limit follows the documented formula`() {
        assertEquals(21_155_904L, onePlusLimit)
        assertEquals(2_304_000L, WidgetImageBudget.remoteViewsLimitBytes(480, 800))
    }

    /**
     * 这条解释了为什么必须改：3 张 1440px 的图（旧行为）已经超过上限。
     * 一旦这个断言不再成立，说明上限公式或份额假设变了，需要重新评估。
     */
    @Test
    fun `three full size images used to blow the limit`() {
        val oldPerImage = 1440L * 1440L * 4
        assertTrue(
            "3 张 1440px 的图必须超过 Android 17 上限，否则这个修复就没有依据",
            oldPerImage * 3 > onePlusLimit
        )
    }

    /** 本机上算出的边长被绝对上限压住，且 3 张图的总内存远低于上限。 */
    @Test
    fun `oneplus budget stays well inside the limit`() {
        val side = WidgetImageBudget.maxDimension(onePlusLimit, 3)
        assertEquals(WidgetImageBudget.HARD_CAP_PX, side)
        assertTrue(
            "预算内 3 张图必须装得下",
            side.toLong() * side * 4 * 3 <= onePlusLimit
        )
    }

    /** 小屏机器上限更低，算出的边长必须自动变小，并且仍然装得下。 */
    @Test
    fun `small screens get a smaller side that still fits`() {
        val limit = WidgetImageBudget.remoteViewsLimitBytes(480, 800)
        val side = WidgetImageBudget.maxDimension(limit, 3)
        assertTrue("小屏必须算出比大屏更小的边长", side < WidgetImageBudget.HARD_CAP_PX)
        assertTrue("不能低于下限", side >= WidgetImageBudget.MIN_PX)
        assertTrue(side.toLong() * side * 4 * 3 <= limit)
    }

    /** 图片越多，单张允许的边长越小；张数为 0 时退回下限而不是 0。 */
    @Test
    fun `more images means a smaller per image side`() {
        val one = WidgetImageBudget.maxDimension(onePlusLimit, 1)
        val three = WidgetImageBudget.maxDimension(onePlusLimit, 3)
        assertTrue(one >= three)
        assertEquals(WidgetImageBudget.MIN_PX, WidgetImageBudget.maxDimension(onePlusLimit, 0))
    }

    /** 任何屏幕尺寸下都不能越界，也不能算出 0。 */
    @Test
    fun `side is always clamped into range`() {
        for (w in listOf(320, 480, 720, 1080, 1272, 1440, 2208)) {
            for (h in listOf(480, 800, 1280, 1920, 2772, 3200)) {
                val side = WidgetImageBudget.maxDimension(
                    WidgetImageBudget.remoteViewsLimitBytes(w, h), 3
                )
                assertTrue("${w}x$h 算出的边长越界：$side",
                    side in WidgetImageBudget.MIN_PX..WidgetImageBudget.HARD_CAP_PX)
            }
        }
    }
}
