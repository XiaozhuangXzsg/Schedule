package com.university.timetable.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 抽掉一部分色相、保留亮度（Rec.601 亮度权重），得到「变灰」后的颜色。
 *
 * 用于「已结束」这类要弱化、但仍得看得清的状态。三种做法里挑了这一种：
 * - 只降 [Color.copy] 的 alpha：卡片只是变淡，色相还在，看着像渲染出了 bug；
 * - 直接换成固定的灰：浅色主题下课程卡本来就是浅底色 + 深色字，换成同一个灰会丢掉明暗对比，
 *   正文糊在底色上；
 * - 抽色相留亮度：对比度不变，颜色身份消失 —— 正是「上完了」该有的样子。
 *
 * [amount] 取 0 时原样返回，取 1 时变成纯灰。
 */
fun Color.desaturated(amount: Float): Color {
    if (amount <= 0f) return this
    val t = amount.coerceAtMost(1f)
    val grey = 0.299f * red + 0.587f * green + 0.114f * blue
    return Color(
        red = red + (grey - red) * t,
        green = green + (grey - green) * t,
        blue = blue + (grey - blue) * t,
        alpha = alpha
    )
}
