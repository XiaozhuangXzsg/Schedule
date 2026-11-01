package com.university.timetable.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.university.timetable.ui.theme.LocalIsDark
import com.university.timetable.ui.theme.LocalReduceMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** 轨道尺寸，与 Material3 `Switch` 的视觉尺寸一致（52 × 32 dp）。 */
private val TrackWidth = 52.dp
private val TrackHeight = 32.dp

/** 玻璃球与轨道边缘的留白，iOS 的开关是 31 点轨道配 27 点圆球。 */
private val KnobInset = 2.5.dp

/**
 * iOS 26 / 27「液态玻璃（Liquid Glass）」风格开关。
 *
 * 设计依据是 Apple 对 Liquid Glass 的三条描述——**半透明材质、镜面高光边、厚度与折射感**：
 *
 * 1. 轨道用**真半透明**底色（alpha 而非实色），所以下方卡片色、甚至用户自定义的页面背景图
 *    都能透上来，这是"玻璃"与"一块纯色圆角矩形"的根本区别；
 * 2. 轨道内部叠三层：纵向厚度渐变（上沿聚光、下沿压暗）、斜向高光扫过、1 dp 镜面描边；
 * 3. 玻璃球用偏左上的径向高光 + 边缘玻璃壳 + 柔和投影做出球面厚度，开启时球底还会
 *    透上一层主题色，模拟光线穿过有色玻璃。
 *
 * 拨动时球体沿运动方向**拉伸再回弹**——这是"液态"的关键特征。拉伸量取 `|sin(πt)|`，
 * 它在行程两端恒为 0、只在中间最大，因此**静止态与普通开关的外观完全一致**，只有动画过程中
 * 才有形变，不会出现静止时球体被拉扁的怪异效果。
 *
 * 已知取舍：Compose 的 `Modifier.blur` 只能模糊自身内容、`RenderEffect` 不跨窗口，
 * 所以这里**不做真正的背景模糊**，只用高光与半透明近似——好处是不引入任何第三方依赖。
 *
 * @param checked 当前状态
 * @param onCheckedChange 状态变化回调；传 `null` 表示只展示不可交互
 * @param enabled 是否可用（不可用时整体降到 45% 不透明度）
 */
@Composable
fun LiquidGlassSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val dark = LocalIsDark.current
    val reduceMotion = LocalReduceMotion.current
    val accent = MaterialTheme.colorScheme.primary

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    // 按下时玻璃球微微涨大，和 iOS 的手感一致。
    val pressScale = if (pressed && enabled) 1.06f else 1f

    // 0 = 关，1 = 开。用带一点回弹的弹簧，玻璃球会"甩"到位。
    val progress = remember { Animatable(if (checked) 1f else 0f) }
    // 运动方向：+1 向右（开启）、-1 向左（关闭）。球沿着这个方向拉伸。
    var direction by remember { mutableFloatStateOf(if (checked) 1f else -1f) }
    LaunchedEffect(checked) {
        direction = if (checked) 1f else -1f
        progress.animateTo(
            targetValue = if (checked) 1f else 0f,
            // 「减少动态效果」下直接跳到位：既没有回弹，也没有液体的拉伸形变
            animationSpec = if (reduceMotion) snap() else spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }

    val haptics = rememberSoftHaptics()

    val toggleModifier = if (onCheckedChange != null) {
        Modifier.toggleable(
            value = checked,
            enabled = enabled,
            interactionSource = interactionSource,
            indication = null,
            role = Role.Switch,
            onValueChange = { next ->
                // 柔性反馈：开关拨动是「中等一档」的触感。
                haptics.toggle()
                onCheckedChange(next)
            }
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            // 与 Material3 的 Switch 一样把触摸区撑到 48 dp，避免开关"点不中"。
            .minimumInteractiveComponentSize()
            .alpha(if (enabled) 1f else 0.45f)
            .then(toggleModifier),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(TrackWidth, TrackHeight)) {
            val t = progress.value.coerceIn(0f, 1f)
            val w = size.width
            val h = size.height
            val radius = h / 2f
            val corner = CornerRadius(radius)

            // 液体形变只在行程中间出现，两端归零。
            val liquid = abs(sin(PI * t)).toFloat()

            // ---- 1. 轨道底色：真半透明，能透出背后的卡片与自定义背景图 ----
            val offTint = if (dark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.09f)
            val onTint = accent.copy(alpha = if (dark) 0.94f else 0.90f)
            drawRoundRect(
                color = lerp(offTint, onTint, t),
                cornerRadius = corner
            )

            // ---- 2. 厚度：上沿聚光、下沿压暗，让它看起来是一块有厚度的玻璃 ----
            drawRoundRect(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.34f - 0.14f * t),
                    0.45f to Color.White.copy(alpha = 0.05f),
                    0.55f to Color.Transparent,
                    1f to Color.Black.copy(alpha = if (dark) 0.22f else 0.11f)
                ),
                cornerRadius = corner
            )

            // ---- 3. 斜向高光扫过：玻璃"反光"的来源 ----
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.30f),
                        Color.White.copy(alpha = 0.03f),
                        Color.Transparent
                    ),
                    start = Offset.Zero,
                    end = Offset(w * 0.74f, h)
                ),
                cornerRadius = corner
            )

            // ---- 4. 拨动瞬间的一层薄光，像玻璃被搅动 ----
            if (liquid > 0.01f) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.10f * liquid),
                    cornerRadius = corner
                )
            }

            // ---- 5. 镜面描边：上亮下暗，勾出玻璃的边缘 ----
            val rim = 1.dp.toPx()
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.62f),
                        Color.White.copy(alpha = 0.10f)
                    )
                ),
                topLeft = Offset(rim / 2f, rim / 2f),
                size = Size(w - rim, h - rim),
                cornerRadius = CornerRadius(radius - rim / 2f),
                style = Stroke(width = rim)
            )

            // ---- 6. 玻璃球 ----
            val inset = KnobInset.toPx()
            val ball = h - inset * 2f
            val travelStart = inset
            val travelEnd = w - inset - ball
            val restX = travelStart + (travelEnd - travelStart) * t

            // 拉伸量：总宽增加 stretch，球心朝运动方向前移 stretch/2，
            // 于是"后缘不动、前缘探出去"，这正是液体被甩出去的样子。
            val stretch = ball * 0.30f * liquid
            val squashH = ball * 0.09f * liquid
            val ballW = (ball + stretch) * pressScale
            val ballH = (ball - squashH) * pressScale
            val center = Offset(
                x = restX + ball / 2f + direction * stretch * 0.5f,
                y = h / 2f
            )

            // 6a. 柔和投影：用径向渐变代替模糊，任何 API 级别都能画。
            val shadowCenter = Offset(center.x, center.y + ballH * 0.44f)
            val shadowRadius = ballW * 0.62f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.28f), Color.Transparent),
                    center = shadowCenter,
                    radius = shadowRadius
                ),
                radius = shadowRadius,
                center = shadowCenter
            )

            val ballTopLeft = Offset(center.x - ballW / 2f, center.y - ballH / 2f)
            val ballSize = Size(ballW, ballH)

            // 6b. 球体本身：高光偏左上、右下略暗，做出球面。
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White,
                        Color(0xFFF6F7FB),
                        Color(0xFFE2E4EC)
                    ),
                    center = Offset(center.x - ballW * 0.24f, center.y - ballH * 0.30f),
                    radius = ballW * 1.12f
                ),
                topLeft = ballTopLeft,
                size = ballSize
            )

            // 6c. 开启时球底透上主题色，模拟光穿过有色玻璃。
            if (t > 0.01f) {
                drawOval(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, accent.copy(alpha = 0.24f * t)),
                        startY = center.y,
                        endY = center.y + ballH / 2f
                    ),
                    topLeft = ballTopLeft,
                    size = ballSize
                )
            }

            // 6d. 玻璃壳：一圈很淡的亮边。
            val shell = 0.8.dp.toPx()
            drawOval(
                color = Color.White.copy(alpha = 0.60f),
                topLeft = Offset(ballTopLeft.x + shell / 2f, ballTopLeft.y + shell / 2f),
                size = Size(ballW - shell, ballH - shell),
                style = Stroke(width = shell)
            )
        }
    }
}
