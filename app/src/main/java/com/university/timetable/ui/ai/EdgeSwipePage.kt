package com.university.timetable.ui.ai

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * 子页面容器：统一处理
 *  1. **状态栏 / 安全区留白**——edge-to-edge 下内容不再被状态栏与导航栏遮挡；
 *  2. **边缘滑动返回**——从屏幕左缘向右滑（或右缘向左滑）返回上一级，
 *     与系统导航栈行为一致；拖动过程中通过 [onDragProgress] 暴露进度，便于做跟手动画。
 */
@Composable
fun EdgeSwipePage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** 触发返回所需的水平距离（dp） */
    thresholdDp: Float = 64f,
    /** 只有从这里开始的拖动才算边缘手势（dp） */
    edgeWidthDp: Float = 40f,
    onDragProgress: (Float) -> Unit = {},
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val thresholdPx = with(density) { thresholdDp.dp.toPx() }
    val edgeWidthPx = with(density) { edgeWidthDp.dp.toPx() }
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }

    val topInset = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

    // 系统返回键 / 系统导航手势返回：优先关闭当前子页面，而不是直接退出应用。
    BackHandler(enabled = true, onBack = onBack)

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(top = topInset, bottom = bottomInset)
            .pointerInput(edgeWidthPx) {
                // 记录本次拖动是否从边缘开始；不是则完全忽略
                var eligible = false
                var accumulated = 0f

                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        eligible = offset.x <= edgeWidthPx || offset.x >= size.width - edgeWidthPx
                        accumulated = 0f
                    },
                    onDragEnd = {
                        if (eligible && abs(accumulated) >= thresholdPx) onBack()
                        eligible = false
                        accumulated = 0f
                        onDragProgress(0f)
                    },
                    onDragCancel = {
                        eligible = false
                        accumulated = 0f
                        onDragProgress(0f)
                    }
                ) { _, delta ->
                    if (!eligible) return@detectHorizontalDragGestures
                    accumulated += delta
                    onDragProgress((abs(accumulated) / screenWidthPx).coerceIn(0f, 1f))
                }
            }
    ) {
        content()
    }
}
