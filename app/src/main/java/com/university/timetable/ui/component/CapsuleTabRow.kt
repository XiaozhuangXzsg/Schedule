package com.university.timetable.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.university.timetable.ui.theme.LocalTimetableColors
import com.university.timetable.ui.tutorial.tutorialAnchor
import kotlin.math.abs

data class CapsuleTab(
    val label: String,
    val icon: ImageVector,
    /** 新手引导的锚点 id；非空时该 tab 会把自己的屏幕位置上报给引导层。 */
    val anchorId: String? = null
)

private val ContainerHeight = 54.dp
private val InnerPadding = 5.dp
private val PillHeight = 44.dp
private val PillShape = RoundedCornerShape(22.dp)

/**
 * 胶囊式分段切换器（参考「蜜蜂记账」的悬浮胶囊设计）。
 *
 * [position] 支持小数：直接传入 HorizontalPager 的
 * `currentPage + currentPageOffsetFraction`，指示器即可跟手联动。
 */
@Composable
fun CapsuleTabRow(
    tabs: List<CapsuleTab>,
    position: Float,
    onTabClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTimetableColors.current
    val density = LocalDensity.current
    val count = tabs.size.coerceAtLeast(1)

    var containerWidthPx by remember { mutableIntStateOf(0) }
    val paddingPx = with(density) { (InnerPadding * 2).toPx() }
    val contentWidthPx: Float = (containerWidthPx - paddingPx).coerceAtLeast(0f)
    val pillWidthDp = with(density) { (contentWidthPx / count).toDp() }
    val clampedPosition = position.coerceIn(0f, (count - 1).toFloat())
    val safePosition: Float = if (clampedPosition.isNaN()) 0f else clampedPosition

    val indicatorOffset by animateDpAsState(
        targetValue = pillWidthDp * safePosition,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "capsuleIndicatorOffset"
    )

    // ColorOS 17 凝光视效：底栏也用统一的玻璃材质。
    // `capsuleContainer` 自身已经带透明度，所以 tintAlpha 传 1f——只叠厚度渐变与光感描边，
    // 不再把底色二次衰减（否则胶囊会淡到看不清边界）。
    GlassSurface(
        modifier = modifier
            .shadow(
                elevation = 14.dp,
                shape = RoundedCornerShape(ContainerHeight / 2),
                ambientColor = Color.Black.copy(alpha = 0.10f),
                spotColor = Color.Black.copy(alpha = 0.16f)
            )
            .height(ContainerHeight)
            .onSizeChanged { containerWidthPx = it.width },
        shape = RoundedCornerShape(ContainerHeight / 2),
        tint = colors.capsuleContainer,
        tintAlpha = 1f
    ) {
        Box(modifier = Modifier.padding(InnerPadding)) {
            if (contentWidthPx > 0) {
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(pillWidthDp)
                        .height(PillHeight)
                        .clip(PillShape)
                        .background(colors.capsuleSelected)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().height(PillHeight),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tab ->
                    val selection = (1f - abs(safePosition - index)).coerceIn(0f, 1f)
                    val contentColor by animateColorAsState(
                        targetValue = if (selection > 0.5f) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            colors.mutedText
                        },
                        animationSpec = tween(220),
                        label = "capsuleContent$index"
                    )
                    val iconScale by animateFloatAsState(
                        targetValue = 1f + 0.08f * selection,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "capsuleScale$index"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(PillHeight)
                            .clip(PillShape)
                            .then(
                                if (tab.anchorId != null) {
                                    Modifier.tutorialAnchor(tab.anchorId)
                                } else {
                                    Modifier
                                }
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onTabClick(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.graphicsLayer {
                                scaleX = iconScale
                                scaleY = iconScale
                            }
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = contentColor,
                                modifier = Modifier.size(18.dp)
                            )
                            if (selection > 0.2f) {
                                Text(
                                    text = tab.label,
                                    color = contentColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .padding(start = (6 * selection).dp)
                                        .graphicsLayer { alpha = ((selection - 0.2f) / 0.8f).coerceIn(0f, 1f) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
