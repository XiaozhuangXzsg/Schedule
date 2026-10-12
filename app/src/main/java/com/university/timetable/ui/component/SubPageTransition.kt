package com.university.timetable.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.university.timetable.data.prefs.TransitionStyle
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

private const val IN_DURATION = 260
private const val OUT_DURATION = 200

/**
 * 子页面统一的进出场转场。
 *
 * - [TransitionStyle.CROSSFADE]：交叉淡化（默认），从右轻滑淡入
 * - [TransitionStyle.ZOOM]：缩放淡入
 *
 * 关闭时先播完退出动画再通过 [onExited] 通知调用方卸载内容，
 * 避免动画被瞬间打断。整体由 `MutableTransitionState` 驱动。
 */
@Composable
fun SubPageTransition(
    visible: Boolean,
    style: Int = TransitionStyle.CROSSFADE,
    modifier: Modifier = Modifier,
    onExited: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val transitionState = remember { MutableTransitionState(false) }
    LaunchedEffect(visible) { transitionState.targetState = visible }

    // 退出动画播完后卸载内容
    if (!transitionState.currentState && !transitionState.isIdle && !transitionState.targetState) {
        LaunchedEffect(Unit) {
            snapshotFlow { transitionState.isIdle }
                .filter { it }
                .first()
            onExited()
        }
    }

    val enter = when (style) {
        TransitionStyle.ZOOM ->
            scaleIn(
                initialScale = 0.92f,
                animationSpec = tween(IN_DURATION, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(IN_DURATION))

        else ->
            slideInHorizontally(
                initialOffsetX = { it / 8 },
                animationSpec = tween(IN_DURATION, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(IN_DURATION))
    }

    val exit = when (style) {
        TransitionStyle.ZOOM ->
            scaleOut(
                targetScale = 0.94f,
                animationSpec = tween(OUT_DURATION)
            ) + fadeOut(animationSpec = tween(OUT_DURATION))

        else ->
            slideOutHorizontally(
                targetOffsetX = { it / 10 },
                animationSpec = tween(OUT_DURATION)
            ) + fadeOut(animationSpec = tween(OUT_DURATION))
    }

    AnimatedVisibility(
        visibleState = transitionState,
        enter = enter,
        exit = exit,
        modifier = modifier
    ) {
        content()
    }
}
