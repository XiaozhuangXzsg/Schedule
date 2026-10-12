package com.university.timetable.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.university.timetable.data.prefs.NowProgressStyle
import kotlin.math.PI
import kotlin.math.sin

/**
 * 正在上的那节课的「波浪进度」。
 *
 * 做成 **Modifier** 而不是包裹容器：课程行的布局（Row + 权重列）已经被调得很紧，
 * 包一层 Box 就要把整段内容重新缩进、还会改变测量顺序。
 * 用 `drawBehind` 直接画在这一行的背景之上、内容之下，接入成本最低、也不动布局。
 *
 * 视觉目标：淡、但要看得见 —— 填充用低透明度，只在波峰处描一条略亮的边，
 * 不抢课程名与地点的可读性。
 *
 * 两个方向：
 * - [NowProgressStyle.LEFT_TO_RIGHT]：从左往右涨，波峰在右侧竖直边上；
 * - [NowProgressStyle.BOTTOM_TO_TOP]：从下往上漫，波峰在顶部水平边上。
 *
 * 波峰会缓慢平移（无限循环动画）形成「水在动」的观感；
 * 但进度本身来自真实时间，不用动画伪造，所以切页面回来时位置一定是对的。
 * `progress <= 0` 时不画任何东西，调用方无需自己判断要不要套用。
 */
@Composable
fun Modifier.waveProgress(
    progress: Float,
    color: Color,
    style: Int = NowProgressStyle.LEFT_TO_RIGHT,
    alpha: Float = 0.20f
): Modifier {
    val clamped = progress.coerceIn(0f, 1f)
    if (clamped <= 0f) return this

    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    return drawBehind {
        val amplitude = (size.height * 0.10f).coerceIn(2f, 7f)
        val path = if (style == NowProgressStyle.BOTTOM_TO_TOP) {
            waveFromBottom(clamped, amplitude, phase)
        } else {
            waveFromLeft(clamped, amplitude, phase)
        }
        drawPath(path, color = color, alpha = alpha)
        // 波峰描边：让「水面」在浅色底上也能被看见
        drawPath(
            path = path,
            color = color,
            alpha = (alpha * 2.4f).coerceAtMost(1f),
            style = Stroke(width = 1.2f)
        )
    }
}

/** 从左往右涨：右侧是波动的竖边。 */
private fun DrawScope.waveFromLeft(progress: Float, amplitude: Float, phase: Float): Path {
    val edge = (size.width * progress).coerceIn(0f, size.width)
    val twoPi = (2 * PI).toFloat()
    val path = Path()
    path.moveTo(0f, 0f)
    path.lineTo(edge, 0f)
    val steps = 28
    for (i in 0..steps) {
        val t = i / steps.toFloat()
        val y = t * size.height
        val x = edge + sin(t * twoPi * 1.5f + phase) * amplitude
        path.lineTo(x.coerceIn(0f, size.width), y)
    }
    path.lineTo(0f, size.height)
    path.close()
    return path
}

/** 从下往上漫：顶部是波动的横边。 */
private fun DrawScope.waveFromBottom(progress: Float, amplitude: Float, phase: Float): Path {
    val top = (size.height * (1f - progress)).coerceIn(0f, size.height)
    val twoPi = (2 * PI).toFloat()
    val path = Path()
    path.moveTo(0f, size.height)
    path.lineTo(0f, top)
    val steps = 28
    for (i in 0..steps) {
        val t = i / steps.toFloat()
        val x = t * size.width
        val y = top + sin(t * twoPi * 1.5f + phase) * amplitude
        path.lineTo(x, y.coerceIn(0f, size.height))
    }
    path.lineTo(size.width, size.height)
    path.close()
    return path
}

/** 供预览/测试使用的尺寸占位，避免误用未约束的 [Size]。 */
internal fun wavePreviewSize(): Size = Size(100f, 40f)
