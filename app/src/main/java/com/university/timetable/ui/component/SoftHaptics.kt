package com.university.timetable.ui.component

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView

/**
 * 是否允许触感反馈，由 `MainActivity` 从偏好 [com.university.timetable.data.prefs.UiPreferences.hapticFeedback] 提供。
 *
 * 默认 `true`：没有提供者时（例如 Compose Preview）触感仍然可用，
 * 因为真正兜底的是系统「触感反馈」总开关 —— [View.performHapticFeedback] 会自己尊重它。
 */
val LocalHapticsEnabled = staticCompositionLocalOf { true }

/**
 * 柔性反馈（ColorOS 17「流体设计」三要素之一）。
 *
 * 刻意只用三个**极轻**的系统 tick，而不是 [HapticFeedbackConstants.LONG_PRESS]：
 * 那一下是「嗡」的一声重震，用在开关和切页上会显得暴躁；
 * `CLOCK_TICK` / `CONTEXT_CLICK` / `KEYBOARD_TAP` 是短促的「哒」，
 * 更接近 ColorOS 的柔性反馈手感。
 *
 * 传入的 `View` 必须是真实挂载的 View（[LocalView] 给的就是 DecorView 内层的那个），
 * 否则 `performHapticFeedback` 会静默返回 false。
 */
class SoftHaptics internal constructor(
    private val view: View,
    private val enabled: Boolean
) {
    /** 最轻的一档：标签页切换、滚动到位、按压反馈。 */
    fun tick() = fire(HapticFeedbackConstants.CLOCK_TICK)

    /** 中等一档：开关切换、选中项变化。 */
    fun toggle() = fire(HapticFeedbackConstants.CONTEXT_CLICK)

    /** 按键感：提交、确认、发送。 */
    fun confirm() = fire(HapticFeedbackConstants.KEYBOARD_TAP)

    private fun fire(constant: Int) {
        if (enabled) view.performHapticFeedback(constant)
    }
}

/**
 * 取一个跟随偏好开关的 [SoftHaptics]。
 *
 * 在 Composable 里调用即可，`enabled` 变化时会换实例，
 * 所以调用方不用自己判断开关状态。
 */
@Composable
fun rememberSoftHaptics(): SoftHaptics {
    val view = LocalView.current
    val enabled = LocalHapticsEnabled.current
    return remember(view, enabled) { SoftHaptics(view, enabled) }
}
