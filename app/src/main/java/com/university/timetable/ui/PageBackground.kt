package com.university.timetable.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.university.timetable.data.prefs.PageKeys
import com.university.timetable.data.prefs.UiPreferences
import com.university.timetable.ui.theme.LocalColumbinaStyle
import com.university.timetable.ui.theme.LocalIsDark
import com.university.timetable.ui.theme.LocalTimetableColors
import com.university.timetable.ui.theme.ThemePresets
import com.university.timetable.util.ImageLoader

/**
 * 页面背景容器：支持
 *  1. 全局主题背景色
 *  2. 单页自定义背景色
 *  3. 单页自定义背景图片（可调透明度与模糊度）
 */
@Composable
fun PageBackground(
    prefs: UiPreferences,
    page: String,
    content: @Composable () -> Unit
) {
    val colors = LocalTimetableColors.current
    val context = LocalContext.current

    val customColor = UiPreferences.backgroundFor(prefs, page)
    val imageUri = UiPreferences.backgroundImageFor(prefs, page)

    val baseColor = if (customColor != UiPreferences.UNSET) {
        Color(customColor)
    } else {
        colors.pageBackground
    }

    val bitmap = remember(imageUri) {
        if (imageUri.isBlank()) null else ImageLoader.load(context, imageUri)
    }

    Box(modifier = Modifier.fillMaxSize().background(baseColor)) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .then(
                        if (prefs.backgroundImageBlur > 0f) {
                            Modifier.blur(prefs.backgroundImageBlur.dp)
                        } else Modifier
                    )
            )
            // 让内容依然清晰可读
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                baseColor.copy(alpha = (1f - prefs.backgroundImageAlpha) * 0.85f),
                                baseColor.copy(alpha = (1f - prefs.backgroundImageAlpha) * 0.55f)
                            )
                        )
                    )
            )
        }
        if (LocalColumbinaStyle.current) {
            ColumbinaVeil(modifier = Modifier.matchParentSize())
        }
        content()
    }
}

/**
 * 「娅娅 · 哥伦比娅」风格的页面点缀：
 * 蔷薇与丁香的柔和光晕、两枚淡金星钻，以及左上角一簇若隐若现的白羽。
 *
 * 刻意做得很淡——它只是氛围，不应该抢走课表本身的可读性。
 */
@Composable
private fun ColumbinaVeil(modifier: Modifier = Modifier) {
    val dark = LocalIsDark.current
    val feather = if (dark) ThemePresets.columbinaFeatherDark else ThemePresets.columbinaFeatherLight
    val rose = if (dark) Color(0xFFFF7AA8) else Color(0xFFE0407E)
    val gold = ThemePresets.columbinaGold

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // 顶部右侧：羽紫光晕
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(feather.copy(alpha = if (dark) 0.20f else 0.13f), Color.Transparent),
                center = Offset(w * 0.84f, h * 0.05f),
                radius = w * 0.90f
            )
        )
        // 底部左侧：玫红光晕
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(rose.copy(alpha = if (dark) 0.16f else 0.09f), Color.Transparent),
                center = Offset(w * 0.10f, h * 0.94f),
                radius = w * 0.95f
            )
        )

        // 两枚菱形星钻
        drawDiamond(
            center = Offset(w * 0.90f, h * 0.20f),
            radius = w * 0.032f,
            color = gold.copy(alpha = if (dark) 0.42f else 0.34f),
            strokeWidth = w * 0.0045f
        )
        drawDiamond(
            center = Offset(w * 0.07f, h * 0.58f),
            radius = w * 0.020f,
            color = rose.copy(alpha = if (dark) 0.34f else 0.24f),
            strokeWidth = w * 0.0035f
        )

        // 右侧一簇白羽（角色头侧的羽翼），三根渐次展开、朝页心扫过
        val featherColor = if (dark) Color.White.copy(alpha = 0.085f) else feather.copy(alpha = 0.13f)
        val base = Offset(w * 0.94f, h * 0.44f)
        listOf(182f, 202f, 222f).forEachIndexed { index, degrees ->
            drawFeather(
                origin = base,
                length = w * (0.20f - index * 0.026f),
                degrees = degrees,
                color = featherColor,
                strokeWidth = w * 0.0035f
            )
        }
    }
}

/** 画一根简笔羽毛：弧形羽轴 + 两侧短羽枝。 */
private fun DrawScope.drawFeather(
    origin: Offset,
    length: Float,
    degrees: Float,
    color: Color,
    strokeWidth: Float
) {
    val radians = Math.toRadians(degrees.toDouble())
    val dir = Offset(kotlin.math.cos(radians).toFloat(), kotlin.math.sin(radians).toFloat())
    val normal = Offset(-dir.y, dir.x)
    val tip = Offset(origin.x + dir.x * length, origin.y + dir.y * length)
    val control = Offset(
        origin.x + dir.x * length * 0.55f + normal.x * length * 0.16f,
        origin.y + dir.y * length * 0.55f + normal.y * length * 0.16f
    )

    val spine = Path().apply {
        moveTo(origin.x, origin.y)
        quadraticBezierTo(control.x, control.y, tip.x, tip.y)
    }
    drawPath(spine, color = color, style = Stroke(width = strokeWidth))

    // 羽枝：沿羽轴均匀取样，朝两侧伸出
    val strokes = 7
    for (i in 1..strokes) {
        val t = i.toFloat() / (strokes + 1)
        val point = Offset(
            (1 - t) * (1 - t) * origin.x + 2 * (1 - t) * t * control.x + t * t * tip.x,
            (1 - t) * (1 - t) * origin.y + 2 * (1 - t) * t * control.y + t * t * tip.y
        )
        val barb = length * 0.30f * (1f - t * 0.65f)
        val side = if (i % 2 == 0) 1f else -1f
        val end = Offset(
            point.x + normal.x * barb * side + dir.x * barb * 0.35f,
            point.y + normal.y * barb * side + dir.y * barb * 0.35f
        )
        drawLine(
            color = color,
            start = point,
            end = end,
            strokeWidth = strokeWidth * 0.7f
        )
    }
}

/** 画一枚菱形（细描边 + 中心一点），用作星钻点缀。 */
private fun DrawScope.drawDiamond(
    center: Offset,
    radius: Float,
    color: Color,
    strokeWidth: Float
) {
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        lineTo(center.x + radius * 0.62f, center.y)
        lineTo(center.x, center.y + radius)
        lineTo(center.x - radius * 0.62f, center.y)
        close()
    }
    drawPath(path, color = color, style = Stroke(width = strokeWidth))
    drawCircle(color = color, radius = strokeWidth * 1.4f, center = center)
}

/** 页面装饰渐变（顶部柔和光晕，模仿截图中课表卡片的粉/蓝渐变）。 */
@Composable
fun PageAccentGlow(modifier: Modifier = Modifier, tint: Color) {
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                colors = listOf(
                    tint.copy(alpha = 0.16f),
                    Color.Transparent
                )
            )
        )
    )
}
