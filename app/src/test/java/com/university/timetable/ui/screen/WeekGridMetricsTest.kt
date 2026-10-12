package com.university.timetable.ui.screen

import androidx.compose.ui.unit.dp
import com.university.timetable.data.entity.DefaultPeriodTimes
import com.university.timetable.data.entity.PeriodTime
import com.university.timetable.data.entity.endMinutes
import com.university.timetable.data.entity.startMinutes
import com.university.timetable.ui.component.CourseCardMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 周视图行高与时间轴条的纯计算回归测试。
 *
 * 重点锁住用户报的那个 bug：第 10 节（17:25-18:10，45 分钟）曾经因为
 * 「量到下一节开始」而被算成 95 分钟，行高接近其它行的两倍。
 */
class WeekGridMetricsTest {

    private val base = 64.dp
    private val all = DefaultPeriodTimes.ALL
    private val heights = all.map { rowHeightFor(it, base) }

    /** 45 分钟的节次必须等高 —— 这是「相同时间间隔高度一样」的直接表述。 */
    @Test
    fun `all 45 minute periods share the same height`() {
        val fortyFive = all.filter { it.endMinutes - it.startMinutes == 45 }
        assertTrue("默认作息里应该有多节 45 分钟的课", fortyFive.size >= 10)
        val distinct = fortyFive.map { rowHeightFor(it, base).value }.distinct()
        assertEquals("45 分钟的节次高度必须完全一致，实际出现 ${distinct.size} 种", 1, distinct.size)
    }

    /** 第 10 节是用户点名的那一行，单独钉死：它必须和第 1 节一样高。 */
    @Test
    fun `period 10 is exactly as tall as period 1`() {
        val p1 = all.first { it.period == 1 }
        val p10 = all.first { it.period == 10 }
        assertEquals(45, p10.endMinutes - p10.startMinutes)
        assertEquals(rowHeightFor(p1, base), rowHeightFor(p10, base))
    }

    /** 55 分钟的 5、6 节按 55/45 等比放大，而不是被夹到同一个值。 */
    @Test
    fun `longer periods scale proportionally`() {
        val p1 = all.first { it.period == 1 }
        val p5 = all.first { it.period == 5 }
        assertEquals(55, p5.endMinutes - p5.startMinutes)
        val expected = base.value * (55f / 45f)
        assertEquals(expected, rowHeightFor(p5, base).value, 0.01f)
        assertTrue(rowHeightFor(p5, base) > rowHeightFor(p1, base))
    }

    /** 行高只由该节自身时长决定，与它在作息表里排第几、后面隔多久无关。 */
    @Test
    fun `height depends only on the period own duration`() {
        val p1 = all.first { it.period == 1 }
        val synthetic = PeriodTime(period = 99, startTime = p1.startTime, endTime = p1.endTime)
        assertEquals(rowHeightFor(p1, base), rowHeightFor(synthetic, base))

        // 旧实现下第 10 节量到第 11 节开始是 95 分钟，会算出接近 MaxRowHeight 的值；
        // 现在它必须严格等于 45 分钟那一档（也就是 base）。
        val p10 = all.first { it.period == 10 }
        assertEquals(base, rowHeightFor(p10, base))
    }

    /** 极端时长被夹在上下界之间，不会撑破网格。 */
    @Test
    fun `height is clamped to sane bounds`() {
        val tiny = PeriodTime(period = 90, startTime = "08:00", endTime = "08:05")
        val huge = PeriodTime(period = 91, startTime = "08:00", endTime = "12:00")
        assertEquals(MinRowHeight, rowHeightFor(tiny, base))
        assertEquals(MaxRowHeight, rowHeightFor(huge, base))
    }

    /** 第一节之前 → 线停在网格顶端。 */
    @Test
    fun `now line sits at the top before the first period`() {
        assertEquals(0.dp, nowLineOffset(all, heights, 6 * 60))
    }

    /** 落在某节中间 → 按已过去的比例插值。 */
    @Test
    fun `now line interpolates inside a period`() {
        val p1 = all.first { it.period == 1 }
        val middle = (p1.startMinutes + p1.endMinutes) / 2
        val expected = rowHeightFor(p1, base) * 0.5f
        assertEquals(expected.value, nowLineOffset(all, heights, middle).value, 1.5f)
    }

    /** 落在大课间（第 10 节结束、晚课还没开始）→ 停在上一节底部，不留空洞。 */
    @Test
    fun `now line rests on the previous period during a long break`() {
        val p10 = all.first { it.period == 10 }
        val p11 = all.first { it.period == 11 }
        val inBreak = (p10.endMinutes + p11.startMinutes) / 2
        val expected = heights.take(10).fold(0.dp) { acc, h -> acc + h }
        assertEquals(expected.value, nowLineOffset(all, heights, inBreak).value, 0.01f)
    }

    /** 全天结束 → 停在最后一节底部。 */
    @Test
    fun `now line rests at the bottom after the last period`() {
        val total = heights.fold(0.dp) { acc, h -> acc + h }
        assertEquals(total.value, nowLineOffset(all, heights, 23 * 60 + 59).value, 0.01f)
    }

    /** 纵坐标随时间是单调不减的，避免线上下乱跳。 */
    @Test
    fun `now line offset is monotonic across the day`() {
        var previous = -1f
        var minute = 0
        while (minute <= 24 * 60) {
            val offset = nowLineOffset(all, heights, minute).value
            assertTrue("第 $minute 分钟出现回落：$offset < $previous", offset >= previous - 0.01f)
            previous = offset
            minute += 5
        }
    }

    @Test
    fun `current minutes of day converts clock time`() {
        assertEquals(0, currentMinutesOfDay(java.time.LocalTime.of(0, 0)))
        assertEquals(8 * 60 + 30, currentMinutesOfDay(java.time.LocalTime.of(8, 30)))
        assertEquals(23 * 60 + 59, currentMinutesOfDay(java.time.LocalTime.of(23, 59)))
    }

    // ---------------- 当前时间轴条的水平几何 ----------------

    /**
     * 圆球要比色条**略宽**（色条 3dp → 圆球 5dp）。
     *
     * 这是用户第二轮点名的：「圆球大小稍微超过彩条宽度，改到 5dp」。
     * 直接读 [CourseCardMetrics] 而不是硬编码色条宽 —— 以后色条变宽，这里会跟着失败提醒一并复核。
     */
    @Test
    fun `now line ball is slightly wider than the course colour bar`() {
        assertEquals(5f, WeekNowLineMetrics.BallSize.value, 0.001f)
        assertTrue(
            "圆球要稍微超过色条，而不是同宽或更窄",
            WeekNowLineMetrics.BallSize > CourseCardMetrics.barWidth(compact = true)
        )
    }

    /** 圆球中心 = 日列左边 + 卡片留白 + 色条起始 + 半个色条宽，即正好压在色条中线上。 */
    @Test
    fun `now line ball is centred on the course colour bar`() {
        val expected = WeekNowLineMetrics.CardInset.value +
            CourseCardMetrics.barStart(compact = true).value +
            CourseCardMetrics.barWidth(compact = true).value / 2f
        assertEquals(expected, WeekNowLineMetrics.BarCenterFromDayStart.value, 0.001f)
    }

    /**
     * 圆球比色条宽之后，球心若误用「半个球」来算就会整体右偏 —— 这条把两者钉开。
     *
     * (5dp − 3dp) / 2 = 1dp，肉眼看得出圆球没压在色条正中。
     */
    @Test
    fun `bar centre uses the colour bar half width and not the ball half width`() {
        val barHalf = CourseCardMetrics.barWidth(compact = true) / 2
        val ballHalf = WeekNowLineMetrics.BallSize / 2
        assertTrue("前提：圆球确实比色条宽", ballHalf > barHalf)
        assertEquals(
            (WeekNowLineMetrics.CardInset + CourseCardMetrics.barStart(compact = true) + barHalf).value,
            WeekNowLineMetrics.BarCenterFromDayStart.value,
            0.001f
        )
        assertTrue(
            "球心不能右偏到用半个球来算的位置",
            WeekNowLineMetrics.BarCenterFromDayStart <
                WeekNowLineMetrics.CardInset + CourseCardMetrics.barStart(compact = true) + ballHalf
        )
    }

    /**
     * 线的左端必须与圆球左边缘重合 —— 用户要求「圆球左边不要再有线条」。
     *
     * 只要左端小于「球心 − 半个球」，圆球左边就会露出一小截线。
     */
    @Test
    fun `now line starts at the ball so nothing is drawn to its left`() {
        val ball = WeekNowLineMetrics.BallSize
        val ballLeft = WeekNowLineMetrics.BarCenterFromDayStart - ball / 2
        assertEquals(
            ballLeft.value,
            WeekNowLineMetrics.lineStartFromDayStart(ball).value,
            0.001f
        )
        assertTrue(
            "左端不能退回日列最左边，否则球左边又会出现线",
            WeekNowLineMetrics.lineStartFromDayStart(ball) > 0.dp
        )
    }

    /** 线右端仍然停在日列右边界：左端右移后长度相应变短，不会越过今天这一列。 */
    @Test
    fun `now line ends at the right edge of the day column`() {
        val dayWidth = WeekNowLineMetrics.dayWidth(350.dp, 7)
        val ball = WeekNowLineMetrics.BallSize
        val start = WeekNowLineMetrics.lineStartFromDayStart(ball)
        val width = WeekNowLineMetrics.lineWidthFromDayStart(dayWidth, ball)
        assertEquals(dayWidth.value, (start + width).value, 0.001f)
        assertTrue("线长必须为正，否则整条线会消失", width > 0.dp)
    }

    /** 没有圆球的细线样式没有可对齐的球，左端退回色条中线；列太窄时兜底为 0 而不是负宽度。 */
    @Test
    fun `plain style line starts at the colour bar centre`() {
        assertEquals(
            WeekNowLineMetrics.BarCenterFromDayStart.value,
            WeekNowLineMetrics.lineStartFromDayStart(0.dp).value,
            0.001f
        )
        assertEquals(0f, WeekNowLineMetrics.lineWidthFromDayStart(2.dp, 0.dp).value, 0.001f)
    }

    /** 红条只在今天那一列里：第 0 列贴左，最后一列右边界正好落在网格区右边缘。 */
    @Test
    fun `day columns tile the grid exactly`() {
        val gridWidth = 350.dp
        val columns = 7
        val width = WeekNowLineMetrics.dayWidth(gridWidth, columns)
        assertEquals(50f, width.value, 0.001f)
        assertEquals(0f, WeekNowLineMetrics.dayStart(0, gridWidth, columns).value, 0.001f)
        assertEquals(300f, WeekNowLineMetrics.dayStart(6, gridWidth, columns).value, 0.001f)
        // 最后一列的右边界不能超出网格区
        val lastRight = WeekNowLineMetrics.dayStart(6, gridWidth, columns) + width
        assertEquals(gridWidth.value, lastRight.value, 0.001f)
    }

    /** 隐藏周末时列数变 5，列宽随之变宽 —— 时间轴条必须跟着变，否则会画到隔壁列。 */
    @Test
    fun `hiding the weekend widens each day column`() {
        assertEquals(70f, WeekNowLineMetrics.dayWidth(350.dp, 5).value, 0.001f)
        assertEquals(50f, WeekNowLineMetrics.dayWidth(350.dp, 7).value, 0.001f)
    }

    /** 列数兜底：传 0 不能除以零崩掉；下标越界也不能把红条画到网格区外面。 */
    @Test
    fun `degenerate column counts and indices stay inside the grid`() {
        assertEquals(0f, WeekNowLineMetrics.dayWidth(350.dp, 0).value, 0.001f)
        assertEquals(0f, WeekNowLineMetrics.dayStart(3, 350.dp, 0).value, 0.001f)
        // 下标越界 → 夹到最后一列，右边界仍在网格区内
        val clamped = WeekNowLineMetrics.dayStart(99, 350.dp, 7)
        assertEquals(300f, clamped.value, 0.001f)
        assertTrue(
            "越界下标不能把红条推到网格区外",
            clamped + WeekNowLineMetrics.dayWidth(350.dp, 7) <= 350.dp
        )
        // 负下标 → 夹到第 0 列
        assertEquals(0f, WeekNowLineMetrics.dayStart(-1, 350.dp, 7).value, 0.001f)
    }
}
