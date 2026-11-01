package com.university.timetable.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.unit.IntOffset
import com.university.timetable.data.prefs.TransitionStyle
import com.university.timetable.ui.theme.LocalReduceMotion
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

private const val IN_DURATION = 260
private const val OUT_DURATION = 200

/**
 * 流体动效的弹簧参数。
 *
 * `DampingRatioLowBouncy`（0.75）会带来约 5% 的过冲 —— 页面滑到位后「顿一下再吸住」，
 * 这正是 ColorOS 17 流体设计里那种「有质量感」的来源；
 * 若改成 `NoBouncy` 就是普通的减速停下，和 tween 没有体感差别。
 */
private val FluidSlideIn = spring<IntOffset>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMediumLow
)

private val FluidSlideOut = spring<IntOffset>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium
)

private val FluidScaleIn = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMediumLow
)

private val FluidFadeIn = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow
)

private val FluidFadeOut = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium
)

/**
 * 子页面统一的进出场转场。
 *
 * - [TransitionStyle.CROSSFADE]：交叉淡化（默认），从右轻滑淡入
 * - [TransitionStyle.ZOOM]：缩放淡入
 * - [TransitionStyle.FLUID]：流体滑入，弹簧驱动、带过冲与缩放（ColorOS 17 风格）
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
    // 系统打开「减少动态效果」时，转场退化为瞬时切换（仍然保留卸载回调的时序）
    val reduceMotion = LocalReduceMotion.current
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

    val enter = if (reduceMotion) fadeIn(animationSpec = snap()) else when (style) {
        TransitionStyle.ZOOM ->
            scaleIn(
                initialScale = 0.92f,
                animationSpec = tween(IN_DURATION, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(IN_DURATION))

        TransitionStyle.FLUID ->
            slideInHorizontally(
                initialOffsetX = { it / 3 },
                animationSpec = FluidSlideIn
            ) + scaleIn(
                initialScale = 0.94f,
                animationSpec = FluidScaleIn
            ) + fadeIn(animationSpec = FluidFadeIn)

        else ->
            slideInHorizontally(
                initialOffsetX = { it / 8 },
                animationSpec = tween(IN_DURATION, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(IN_DURATION))
    }

    val exit = if (reduceMotion) fadeOut(animationSpec = snap()) else when (style) {
        TransitionStyle.ZOOM ->
            scaleOut(
                targetScale = 0.94f,
                animationSpec = tween(OUT_DURATION)
            ) + fadeOut(animationSpec = tween(OUT_DURATION))

        TransitionStyle.FLUID ->
            slideOutHorizontally(
                targetOffsetX = { it / 6 },
                animationSpec = FluidSlideOut
            ) + fadeOut(animationSpec = FluidFadeOut)

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
