package com.university.timetable.ui.ai

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * 用 [EdgeSwipePage] 包裹 AI 子页面：
 * - 处理状态栏 / 安全区留白
 * - 支持左右边缘滑动返回
 * - 拖动时页面跟手缩放并淡出，松手回弹或完成返回
 */
@Composable
fun AiSubPage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var dragProgress by remember { mutableFloatStateOf(0f) }
    val animated by animateFloatAsState(
        targetValue = dragProgress,
        label = "aiSwipeProgress"
    )

    EdgeSwipePage(
        onBack = onBack,
        modifier = modifier.graphicsLayerCompat(animated),
        onDragProgress = { dragProgress = it },
        content = content
    )
}

/** 拖动进度 → 轻微缩放 + 淡出，营造「正在退出」的反馈。 */
private fun Modifier.graphicsLayerCompat(progress: Float): Modifier =
    this.graphicsLayer {
        val p = progress.coerceIn(0f, 1f)
        scaleX = 1f - 0.04f * p
        scaleY = 1f - 0.04f * p
        alpha = 1f - 0.25f * p
    }
