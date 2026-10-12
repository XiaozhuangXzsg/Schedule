package com.university.timetable.widget

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 「明日预告」课程格的排版参数与版式选择规则。
 *
 * 抽成不依赖 Compose 运行时的纯声明，是为了让「课多于几门才改成可滑动」这类
 * 曾经出过错的规则能被单元测试钉住 —— 早先是 `classes.take(4)` 加一句
 * 「还有 N 门课」，第 5 门之后的课在卡片上永远看不到。
 */
internal object TomorrowGridLayout {

    /** 课程格之间的横向 / 纵向留白。太小格子会「边贴着边」，整块糊成一片。 */
    val CellGap: Dp = 8.dp

    /** 可滚动网格里每行课程格的固定高度：两行文字之外还留得出上下呼吸空间。 */
    val CellRowHeight: Dp = 50.dp

    /**
     * 课程格里「课名」那一行的字号。
     *
     * 和 [CellRowHeight] 一起受单元测试约束：字号调大必须同时确认行高还放得下，
     * 否则文字会被卡片裁掉（用户明确要求「字体大一点，但不能超出课模块边界」）。
     */
    const val CellNameSp: Float = 11f

    /** 课程格里「时间 + 地点」那一行的字号。 */
    const val CellDetailSp: Float = 9f

    /** 静态网格最多摆得下的课程数：4×2 卡片正好 2 列 × 2 行。 */
    const val MaxStaticCells: Int = 4

    /** 一行放几门课。 */
    const val Columns: Int = 2

    /** 课多到静态网格摆不下时，改用可上下滑动的列表。 */
    fun needsScroll(classCount: Int): Boolean = classCount > MaxStaticCells

    /** 课程格要分成几行（每行 [Columns] 门）。 */
    fun rowCount(classCount: Int): Int =
        if (classCount <= 0) 0 else (classCount + Columns - 1) / Columns
}
