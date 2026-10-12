package com.university.timetable.ui.screen

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.university.timetable.data.entity.PeriodTime
import com.university.timetable.data.entity.endMinutes
import com.university.timetable.data.entity.startMinutes
import com.university.timetable.ui.component.CourseCardMetrics
import java.time.LocalTime

/**
 * 周视图网格的纯计算部分。
 *
 * 单独成文件（而不是留在 `WeekScreen.kt` 里）是为了**可单元测试**：
 * 第 10 节行高异常、时间轴条位置错位这类问题都是纯算术 bug，
 * 放在可组合函数里只能靠肉眼看截图，放到这里就能用单测直接锁住。
 */

/** 每行最小 / 最大高度：按该节自身时长等比缩放后的兜底边界。 */
internal val MinRowHeight = 52.dp
internal val MaxRowHeight = 140.dp

/**
 * 等高基准时长：默认作息里 14 节有 12 节是 45 分钟，
 * 只有中午 5、6 节（12:20-13:15 / 13:25-14:20）是 55 分钟。
 * 以 45 分钟为基准，保证「时间间隔相同 ⇒ 行高必然相同」。
 */
internal const val BasePeriodMinutes = 45f

/**
 * 单行高度：按该节**自身时长**与 [BasePeriodMinutes] 等比缩放。
 *
 * 这里曾经用的是「本节开始到下一节开始」的间隔，等于把课间与晚饭时间也算进了行高：
 * 第 10 节（17:25-18:10，之后 19:00 才上晚课）因此拿到 95 分钟 → 近百 dp，
 * 比其它 45 分钟的行高出近一倍，看上去「上下太高了」。
 *
 * 现在只看 `endMinutes - startMinutes`：45 分钟的节次高度严格一致，
 * 时长更长的节次才按比例放大，符合「相同间隔等高、不同间隔等比」的预期。
 */
internal fun rowHeightFor(period: PeriodTime, base: Dp): Dp {
    val minutes = (period.endMinutes - period.startMinutes).coerceAtLeast(1)
    val scaled = base * (minutes / BasePeriodMinutes)
    return scaled.coerceIn(MinRowHeight, MaxRowHeight)
}

/** 当前时刻的「当天第几分钟」。 */
internal fun currentMinutesOfDay(now: LocalTime = LocalTime.now()): Int = now.hour * 60 + now.minute

/**
 * 周视图「当前时间轴条」的**水平**几何。
 *
 * 三条需求（都曾经出过错，所以抽成纯算术以便单测钉住）：
 * 1. 红条只在「今天」那一列里显示，不再横贯整个星期 —— 所以需要今天这一列的左右边界；
 * 2. 左端圆球要和课程卡左侧色条**对齐**（压在色条中线上），看起来像色条那一列的顶端封口 ——
 *    色条位置 = 日列左边 + 卡片左右留白 [CardInset] + 卡片内色条起始 [barStart]；
 * 3. 圆球左边**不再有线**：整条线的左端就是圆球的左边缘。
 *
 * 色条的宽度与起始位置直接取自 [CourseCardMetrics]（compact 版式，即周视图用的那套），
 * 这样改卡片的色条不会再让圆球错位。
 */
internal object WeekNowLineMetrics {

    /** 日列里课程卡左右各留的边距（`DayColumn` 给 `CourseCard` 的 `padding(horizontal = …)`）。 */
    val CardInset: Dp = 1.5.dp

    /**
     * 课程卡左侧色条的**中心**相对「日列左边」的偏移 —— 圆球要压在这条中线上。
     *
     * 注意这里用的是**色条**半宽而不是圆球半宽：两者现在不一样宽了（见 [BallSize]），
     * 用错就会让圆球整体往右偏 (BallSize - barWidth) / 2。
     */
    val BarCenterFromDayStart: Dp =
        CardInset + CourseCardMetrics.barStart(compact = true) +
            CourseCardMetrics.barWidth(compact = true) / 2

    /**
     * 圆球直径：**比色条略宽**（色条 3dp → 圆球 5dp）。
     *
     * 一开始取的是「与色条同宽」，但同宽时圆球看着只是色条截面的一小截、没有封口的实心感；
     * 稍微胖一圈、并且沿色条中线两边**对称**探出，才像那一列的顶端帽。
     */
    val BallSize: Dp = 5.dp

    /**
     * 线（连同圆球）左端相对「日列左边」的偏移。
     *
     * **圆球左边不再有线**：整条线的左端就是圆球的左边缘，圆球正好压在线的起点上。
     * 圆心在 [BarCenterFromDayStart]（色条中线），所以左边缘 = 中线 − 半个球。
     * 没有圆球的细线样式（[ballSize] 传 0）没有可对齐的球，退回色条中线本身 ——
     * 仍然不是从日列最左边起画，否则换样式时线头会突然多出一截。
     */
    fun lineStartFromDayStart(ballSize: Dp): Dp = BarCenterFromDayStart - ballSize / 2

    /**
     * 线从 [lineStartFromDayStart] 一直拉到日列的右边缘。
     *
     * 左端右移以后长度相应变短，右端不动 —— 所以红条依然严格落在「今天」这一列里。
     * 列窄到放不下左端偏移时兜底为 0，不会画成负宽度。
     */
    fun lineWidthFromDayStart(dayWidth: Dp, ballSize: Dp): Dp =
        (dayWidth - lineStartFromDayStart(ballSize)).coerceAtLeast(0.dp)

    /**
     * 第 [dayIndex] 天这一列在**网格区**里的左边界。
     *
     * [gridWidth] 是网格区宽度（已扣掉左侧固定时间轴与右侧留白），[columns] 是当前显示的列数
     * （周末隐藏时是 5）。列宽由两者相除得到，与 `DayColumn` 的算法完全一致。
     *
     * 下标会被夹在 `0 until columns` 内：即使调用方算错了，红条也只会画到网格区里，
     * 不会跑到左侧时间轴或右边留白上。
     */
    fun dayStart(dayIndex: Int, gridWidth: Dp, columns: Int): Dp =
        if (columns <= 0) 0.dp else dayWidth(gridWidth, columns) * dayIndex.coerceIn(0, columns - 1)

    /** 单列宽度，与 [dayStart] 同源，供调用方复用。 */
    fun dayWidth(gridWidth: Dp, columns: Int): Dp = if (columns <= 0) 0.dp else gridWidth / columns
}

/**
 * 把当前时间折算成网格内容坐标系里的纵坐标（**未**扣除滚动偏移）。
 *
 * 与课程块用的是同一份 [rowHeights] 累加，因此指示线必然压在正确的时间位置上：
 * - 落在某节内 → 按已过去的比例插值；
 * - 落在大课间（两节之间）→ 停在上一节底部；
 * - 全天课都上完了 → 停在最后一节底部。
 */
internal fun nowLineOffset(periods: List<PeriodTime>, rowHeights: List<Dp>, minutes: Int): Dp {
    var top = 0.dp
    periods.forEachIndexed { index, period ->
        val height = rowHeights.getOrElse(index) { MinRowHeight }
        if (minutes < period.startMinutes) return top
        if (minutes < period.endMinutes) {
            val span = (period.endMinutes - period.startMinutes).coerceAtLeast(1)
            val fraction = (minutes - period.startMinutes).toFloat() / span
            return top + height * fraction
        }
        top += height
    }
    return top
}
