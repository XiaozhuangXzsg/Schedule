package com.university.timetable.ui.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.university.timetable.ui.theme.LocalIsDark
import com.university.timetable.ui.theme.LocalLiquidGlassStyle
import com.university.timetable.ui.theme.LocalTimetableColors

/**
 * ColorOS 17「流体设计」的统一圆角尺度。
 *
 * 官方三要素之一是「统一圆角」——同一层级的容器必须用同一个半径，
 * 否则卡片挨在一起时圆角对不齐，玻璃材质会立刻显假。
 */
object GlassRadius {
    /** 页面级大卡片：设置分组、对话气泡容器等。 */
    val Card = 24.dp

    /** 卡片内部的小容器：课程块、行内小卡。 */
    val Inner = 18.dp

    /** 更小的元素：标签、色块、缩略图。 */
    val Small = 12.dp
}

// ---------------------------------------------------------------------------
// 不透明度
// ---------------------------------------------------------------------------

/**
 * 大块卡片的玻璃底色：通透一些，让用户自定义的背景图透上来。
 *
 * 液态玻璃模式下再放开一档——它的底色本身就带 alpha（见 `TimetableColors` 的
 * `surfaceCard`），两层相乘后大约落在 50%–63%，才真的透得出后面的环境光。
 */
@Composable
fun glassCardAlpha(): Float = when {
    LocalLiquidGlassStyle.current -> if (LocalIsDark.current) 0.86f else 0.88f
    LocalIsDark.current -> 0.78f
    else -> 0.82f
}

/**
 * 弹窗 / 浮层的玻璃底色：留更多不透明度。
 *
 * 弹窗里多是表单，底下还压着一层遮罩，太透会让文字发灰；这里要的是那圈光感描边，
 * 而不是真的看穿过去。
 */
@Composable
fun glassPanelAlpha(): Float = when {
    LocalLiquidGlassStyle.current -> if (LocalIsDark.current) 0.86f else 0.88f
    LocalIsDark.current -> 0.90f
    else -> 0.92f
}

// ---------------------------------------------------------------------------
// 两种用法
// ---------------------------------------------------------------------------

/**
 * 凝光视效的玻璃材质容器（自带形状）。
 *
 * 共三层：
 * 1. **半透明底色**——故意用真 alpha 而不是实色，这样用户自定义的页面背景图能透上来，
 *    配合应用已有的背景模糊就是「半透明磨砂」；
 * 2. **纵向厚度渐变**——上缘提亮、下缘压暗，模拟玻璃的厚度；
 * 3. **光感描边**——1dp 的渐变描边，上亮下暗，是凝光视效里最出效果的一笔。
 *
 * 打开「液态玻璃」风格后（[LocalLiquidGlassStyle]），同一块玻璃会再加三样东西：
 * **斜向镜面扫光**（模拟光线掠过玻璃表面的反射）、**底部接触阴影**（让玻璃像浮在背景上，
 * 而不是贴上去）、以及**更亮的边缘高光 + 柔和外投影**。
 *
 * 这里**不做真正的背景模糊**：Compose 的 `Modifier.blur` 只模糊自身内容，
 * `RenderEffect` 又不跨窗口，应用内拿不到下层已绘制像素。用透明度 + 高光近似，
 * 好处是零依赖、零性能开销。
 *
 * @param tint 玻璃底色，默认取当前主题的卡片色。
 * @param tintAlpha 底色不透明度的**倍数**（与 [tint] 自带的 alpha 相乘）。
 *   之所以是相乘而不是覆盖：主题里的 `capsuleContainer` 这类颜色本身就带透明度，
 *   覆盖会把它们变成实色（v2.1.8 开发中踩过这个坑），相乘则天然安全——
 *   实色传默认值即可，半透明色传 1f 表示「保持原样，只叠玻璃层」。
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(GlassRadius.Card),
    tint: Color = LocalTimetableColors.current.surfaceCard,
    tintAlpha: Float = glassCardAlpha(),
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = LocalIsDark.current
    val liquid = LocalLiquidGlassStyle.current

    if (liquid) {
        Box(
            modifier = modifier
                .shadow(
                    elevation = if (dark) 16.dp else 12.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = if (dark) 0.42f else 0.16f),
                    spotColor = Color.Black.copy(alpha = if (dark) 0.46f else 0.22f)
                )
                .clip(shape)
                .drawBehind {
                    drawRect(liquidBaseBrush(tint, tintAlpha))
                    drawLiquidSheen(dark)
                    drawTopEdge(dark, short = true)
                }
                .border(width = 1.dp, brush = liquidRimBrush(dark), shape = shape),
            content = content
        )
    } else {
        Box(
            modifier = modifier
                .clip(shape)
                .drawBehind {
                    drawRect(tint.copy(alpha = tint.alpha * tintAlpha))
                    drawRect(depthBrush(dark))
                }
                .border(width = 1.dp, brush = rimBrush(dark), shape = shape),
            content = content
        )
    }
}

/**
 * 给**已经裁好圆角**的容器铺上玻璃层（无形状版）。
 *
 * 用法是把原有的 `.background(tint)` 直接换成 `.glassLayer(tint)`：底色会变成半透明、
 * 叠上纵向厚度渐变，并在上下缘各描一道光感边。因为外层已经 `.clip(shape)` 过，
 * 这三样都会被那个圆角裁掉，所以这里不需要知道具体形状——这也是它能一处处机械替换的原因。
 *
 * 打开「液态玻璃」风格后同样会多出镜面扫光与底部接触阴影，且底色换成真正的纵向渐变，
 * 让弹窗/浮层看起来是「一块有厚度的玻璃」而不是「一个压暗的矩形」。
 *
 * @param tintAlpha 底色不透明度的**倍数**（与 [tint] 自带的 alpha 相乘），默认取
 *   [glassPanelAlpha]（弹窗场景）；大块卡片用 [GlassSurface] 更合适。
 */
@Composable
fun Modifier.glassLayer(
    tint: Color,
    tintAlpha: Float = glassPanelAlpha(),
): Modifier {
    val dark = LocalIsDark.current
    val liquid = LocalLiquidGlassStyle.current

    if (liquid) {
        return this.drawBehind {
            drawRect(liquidBaseBrush(tint, tintAlpha))
            drawLiquidSheen(dark)

            val edge = 1.dp.toPx()
            if (size.width > 0f && size.height > edge * 2) {
                drawRect(
                    brush = liquidEdgeBrush(dark, top = true),
                    topLeft = Offset.Zero,
                    size = Size(size.width, edge)
                )
                drawRect(
                    brush = edgeBrush(dark, top = false),
                    topLeft = Offset(0f, size.height - edge),
                    size = Size(size.width, edge)
                )
            }
        }
    }

    return this.drawBehind {
        drawRect(tint.copy(alpha = tint.alpha * tintAlpha))
        drawRect(depthBrush(dark))

        val edge = 1.dp.toPx()
        if (size.width > 0f && size.height > edge * 2) {
            drawRect(
                brush = edgeBrush(dark, top = true),
                topLeft = Offset.Zero,
                size = Size(size.width, edge)
            )
            drawRect(
                brush = edgeBrush(dark, top = false),
                topLeft = Offset(0f, size.height - edge),
                size = Size(size.width, edge)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 液态玻璃的笔刷
// ---------------------------------------------------------------------------

/**
 * 液态玻璃的底色：一道纵向渐变。
 *
 * 不是单色 —— 真实玻璃因为上方采光更多，上沿总比下沿亮一点；把这点做进底色，
 * 玻璃才有「厚度」，否则再高的描边也像贴纸。
 */
private fun liquidBaseBrush(tint: Color, tintAlpha: Float): Brush {
    val alpha = (tint.alpha * tintAlpha).coerceIn(0f, 1f)
    return Brush.verticalGradient(
        0f to tint.copy(alpha = (alpha * 1.12f).coerceAtMost(1f)),
        0.55f to tint.copy(alpha = alpha),
        1f to tint.copy(alpha = alpha * 0.82f)
    )
}

/**
 * 镜面扫光 + 底部接触阴影。
 *
 * 扫光从左上角斜向右下、在 60% 处消失，模拟斜面反射；接触阴影只在最底部 28% 出现，
 * 让玻璃和背景之间有一道极淡的"压痕"，这是它看起来浮起来的关键。
 */
private fun DrawScope.drawLiquidSheen(dark: Boolean) {
    drawRect(
        brush = Brush.linearGradient(
            0f to Color.White.copy(alpha = if (dark) 0.16f else 0.34f),
            0.30f to Color.White.copy(alpha = if (dark) 0.05f else 0.10f),
            0.62f to Color.Transparent,
            1f to Color.Transparent,
            start = Offset.Zero,
            end = Offset(size.width * 0.85f, size.height)
        )
    )
    drawRect(
        brush = Brush.verticalGradient(
            0.72f to Color.Transparent,
            1f to Color.Black.copy(alpha = if (dark) 0.18f else 0.07f)
        )
    )
}

/** 在容器最上沿补一条短促的高光线（两端渐隐，避免圆角处出现硬边）。 */
private fun DrawScope.drawTopEdge(dark: Boolean, short: Boolean) {
    val edge = 1.dp.toPx()
    if (size.width <= 0f || size.height <= edge * 2) return
    val inset = if (short) size.width * 0.08f else 0f
    drawRect(
        brush = liquidEdgeBrush(dark, top = true),
        topLeft = Offset(inset, 0f),
        size = Size(size.width - inset * 2f, edge)
    )
}

/** 液态玻璃的边缘高光：比普通模式更亮一点，才压得住更薄的底。 */
private fun liquidEdgeBrush(dark: Boolean, top: Boolean): Brush {
    val peak = if (dark) 0.30f else if (top) 0.98f else 0.10f
    val base = if (dark) Color.White else if (top) Color.White else Color.Black
    return Brush.horizontalGradient(
        0f to base.copy(alpha = 0f),
        0.12f to base.copy(alpha = peak),
        0.88f to base.copy(alpha = peak),
        1f to base.copy(alpha = 0f)
    )
}

/** 液态玻璃的整圈描边：上亮下暗，比普通模式更细更透。 */
private fun liquidRimBrush(dark: Boolean): Brush = if (dark) {
    Brush.verticalGradient(
        0f to Color.White.copy(alpha = 0.34f),
        0.30f to Color.White.copy(alpha = 0.07f),
        0.72f to Color.White.copy(alpha = 0.05f),
        1f to Color.White.copy(alpha = 0.22f)
    )
} else {
    Brush.verticalGradient(
        0f to Color.White.copy(alpha = 1f),
        0.28f to Color.White.copy(alpha = 0.28f),
        0.72f to Color.Black.copy(alpha = 0.02f),
        1f to Color.Black.copy(alpha = 0.10f)
    )
}

// ---------------------------------------------------------------------------
// 笔刷
// ---------------------------------------------------------------------------

/** 纵向厚度渐变，叠在底色之上、内容之下。 */
private fun depthBrush(dark: Boolean): Brush = if (dark) {
    Brush.verticalGradient(
        0f to Color.White.copy(alpha = 0.06f),
        0.45f to Color.Transparent,
        1f to Color.Black.copy(alpha = 0.16f)
    )
} else {
    Brush.verticalGradient(
        0f to Color.White.copy(alpha = 0.35f),
        0.45f to Color.Transparent,
        1f to Color.Black.copy(alpha = 0.05f)
    )
}

/** 光感描边（整圈）：上缘受光最亮，下缘只留一点点反光。 */
private fun rimBrush(dark: Boolean): Brush = if (dark) {
    Brush.verticalGradient(
        0f to Color.White.copy(alpha = 0.20f),
        0.35f to Color.White.copy(alpha = 0.04f),
        1f to Color.White.copy(alpha = 0.12f)
    )
} else {
    Brush.verticalGradient(
        0f to Color.White.copy(alpha = 0.95f),
        0.35f to Color.Black.copy(alpha = 0.03f),
        1f to Color.Black.copy(alpha = 0.07f)
    )
}

/**
 * 单条水平边的高光，用于 [glassLayer] 的上下缘。
 *
 * 两端渐隐：矩形直角边直接铺满，在圆角处会突然断掉、显出一条硬线。
 */
private fun edgeBrush(dark: Boolean, top: Boolean): Brush {
    val peak = if (dark) 0.20f else if (top) 0.90f else 0.07f
    val base = if (dark) Color.White else if (top) Color.White else Color.Black
    return Brush.horizontalGradient(
        0f to base.copy(alpha = 0f),
        0.12f to base.copy(alpha = peak),
        0.88f to base.copy(alpha = peak),
        1f to base.copy(alpha = 0f)
    )
}
