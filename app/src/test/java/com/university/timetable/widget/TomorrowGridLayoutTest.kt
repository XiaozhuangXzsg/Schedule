package com.university.timetable.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「明日预告」版式选择的纯计算回归测试。
 *
 * 锁住用户报的那条：4×2 卡片原来只展示 `classes.take(4)`、剩下用「还有 N 门课」一句话带过，
 * 于是第 5 门之后的课在卡片上永远看不到。现在第 5 门起必须走可上下滑动的列表。
 */
class TomorrowGridLayoutTest {

    /** 4 门以内仍用静态网格：格子按剩余高度均分，卡片被铺满，观感最好。 */
    @Test
    fun `up to four classes keep the static grid`() {
        for (count in 0..4) {
            assertFalse("$count 门课不该需要滚动", TomorrowGridLayout.needsScroll(count))
        }
    }

    /** 第 5 门课开始必须能滑动 —— 这正是用户要求「课大于 4 要能上下滑」的那条边界。 */
    @Test
    fun `five classes and above switch to the scrollable grid`() {
        for (count in 5..20) {
            assertTrue("$count 门课必须可上下滑动", TomorrowGridLayout.needsScroll(count))
        }
    }

    /** 每行两门课：5 门课要排 3 行（最后一行只有一门，右侧留空而不是塞满）。 */
    @Test
    fun `rows hold two classes each`() {
        assertEquals(0, TomorrowGridLayout.rowCount(0))
        assertEquals(1, TomorrowGridLayout.rowCount(1))
        assertEquals(1, TomorrowGridLayout.rowCount(2))
        assertEquals(2, TomorrowGridLayout.rowCount(3))
        assertEquals(2, TomorrowGridLayout.rowCount(4))
        assertEquals(3, TomorrowGridLayout.rowCount(5))
        assertEquals(4, TomorrowGridLayout.rowCount(7))
    }

    /** 只有一行时的行数算法不能把奇数门课漏掉。 */
    @Test
    fun `a single odd class still occupies one row`() {
        assertEquals(TomorrowGridLayout.rowCount(1), TomorrowGridLayout.rowCount(2))
    }

    /** 留白必须真的存在：格贴格是用户点名要改掉的观感。 */
    @Test
    fun `cells are separated by a real gap`() {
        assertTrue("格间留白必须大于 0", TomorrowGridLayout.CellGap.value > 0f)
        assertTrue("格间留白不该大到把格子挤没", TomorrowGridLayout.CellGap.value <= 12f)
    }

    /** 固定行高要放得下「课名 + 时间地点」两行字，并留出上下呼吸空间。 */
    @Test
    fun `the fixed row height fits two text lines with room to breathe`() {
        // 用真实的字号常量估算：课名行 + 时间地点行，按 1.2 倍行高算
        val textHeight = (TomorrowGridLayout.CellNameSp * 1.2f) +
            (TomorrowGridLayout.CellDetailSp * 1.2f)
        val cellHeight = TomorrowGridLayout.CellRowHeight.value
        assertTrue(
            "行高 $cellHeight 放不下两行字（约 $textHeight）",
            cellHeight >= textHeight + 8f
        )
        assertTrue("行高不该高到在 4×2 卡片里只排得下一行", cellHeight <= 64f)
    }

    /** 字号不能小到看不清，也不能大到把格子撑爆（用户要求「大一点但不超出模块边界」）。 */
    @Test
    fun `cell font sizes stay readable and within the cell`() {
        assertTrue("课名字号不该小于 10sp", TomorrowGridLayout.CellNameSp >= 10f)
        assertTrue("时间地点字号不该小于 8sp", TomorrowGridLayout.CellDetailSp >= 8f)
        assertTrue(
            "课名不该比两行文字还占地方",
            TomorrowGridLayout.CellNameSp > TomorrowGridLayout.CellDetailSp
        )
    }

    /** 常量与卡片尺寸对齐：4×2 卡片正好 2 列 × 2 行。 */
    @Test
    fun `the static grid is exactly two by two`() {
        assertEquals(2, TomorrowGridLayout.Columns)
        assertEquals(TomorrowGridLayout.Columns * 2, TomorrowGridLayout.MaxStaticCells)
    }
}
