package com.university.timetable.ui.tutorial

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect

/**
 * 引导层的可见状态。叠加层画什么**完全**由它决定 —— 没有独立的动画状态、没有延时任务，
 * 所以页面切换 / 返回 / 进程重建都不会留下「看不见但还在吃点击」的遮罩。
 */
data class TutorialUiState(
    val active: Boolean = false,
    val index: Int = 0,
    val showWelcome: Boolean = false,
    val finished: Boolean = false
)

/**
 * 新手引导的状态机。
 *
 * 刻意做成纯 Kotlin（不依赖 Composable / 定时器），这样「自动继续」的规则可以被 JVM 单测直接覆盖，
 * 也避免了用 `delay` 假装用户完成了操作。
 *
 * 自动继续只有两个信号源，都来自 [TutorialAnchorRegistry] 的真实布局上报：
 *  1. `advanceAnchor` 出现 —— 用户已经导航/打开了下一个界面；
 *  2. `anchor` 出现过、随后消失 —— 用户点掉了当前高亮的目标。
 * 「下一步」按钮始终保留为兜底（文案会引导用户去操作），但**没有任何一条路径靠计时器推进**。
 */
class TutorialController(
    private val steps: List<TutorialStep> = TutorialSteps.ALL
) {

    var state by mutableStateOf(TutorialUiState())
        private set

    /** 记录「本步高亮目标已经出现过」，用于识别「用户点掉了它」。切换步骤时清空。 */
    private val seenAnchors = mutableSetOf<String>()

    val total: Int get() = steps.size

    val current: TutorialStep? get() = steps.getOrNull(state.index)

    /** 当前显示的是欢迎卡片（还没进入第 1 步）。 */
    val isWelcome: Boolean get() = state.active && state.showWelcome

    /** 当前显示的是结束页。 */
    val isFinish: Boolean get() = state.active && !state.showWelcome && state.index >= steps.lastIndex

    /** 从第一步开始（含欢迎卡片）。首次启动自动调用，设置里的「新手引导」也调用它。 */
    fun start() {
        seenAnchors.clear()
        state = TutorialUiState(active = true, index = 0, showWelcome = true, finished = false)
    }

    /** 供外部（如 rememberSaveable 恢复）设置状态。 */
    fun applyState(newState: TutorialUiState) {
        seenAnchors.clear()
        state = newState
    }

    /** 欢迎卡片上的「开始引导」。 */
    fun beginSteps() {
        if (!state.active || !state.showWelcome) return
        seenAnchors.clear()
        state = state.copy(showWelcome = false)
    }

    /** 「跳过引导」/「暂时跳过」：仅写一次终态，遮罩立即消失。 */
    fun skip() {
        if (!state.active) return
        seenAnchors.clear()
        state = TutorialUiState(finished = true)
    }

    /** 结束页的「开始使用」。 */
    fun finish() = skip()

    /** 设置页「新手引导」入口：随时重看。 */
    fun restart() = start()

    fun next() {
        if (!state.active) return
        if (state.showWelcome) {
            beginSteps()
            return
        }
        if (state.index >= steps.lastIndex) {
            finish()
            return
        }
        seenAnchors.clear()
        state = state.copy(index = state.index + 1)
    }

    fun previous() {
        if (!state.active) return
        if (state.showWelcome) return
        seenAnchors.clear()
        if (state.index <= 0) {
            state = state.copy(showWelcome = true)
        } else {
            state = state.copy(index = state.index - 1)
        }
    }

    /** 直接跳到某一步（调试 / 将来做步骤列表用）。 */
    fun jumpTo(index: Int) {
        if (!state.active) return
        seenAnchors.clear()
        state = state.copy(index = index.coerceIn(0, steps.lastIndex), showWelcome = false)
    }

    /**
     * 锚点表变化时调用。只在真的满足推进条件时前进一格，其余情况什么都不做，
     * 因此可以安全地在每次重组后重复调用。
     */
    fun onAnchorsChanged(anchors: Map<String, Rect>) {
        if (!state.active || state.showWelcome) return
        if (state.index >= steps.lastIndex) return
        val step = current ?: return

        // 信号 1：下一步的入口已经出现在界面上 → 说明用户已经走过去了。
        val advanceAnchor = step.advanceAnchor
        if (advanceAnchor != null && anchors.containsKey(advanceAnchor)) {
            seenAnchors.clear()
            state = state.copy(index = state.index + 1)
            return
        }

        // 信号 2：当前高亮目标「出现过、又消失」→ 说明用户点掉了它。
        // 仅对 requiresAction 的步骤生效；且只有不依赖信号 1 的步骤才用它，避免一步被推进两次。
        val anchor = step.anchor
        if (step.requiresAction && anchor != null && advanceAnchor == null) {
            if (anchors.containsKey(anchor)) {
                seenAnchors += anchor
            } else if (anchor in seenAnchors) {
                seenAnchors.clear()
                state = state.copy(index = state.index + 1)
            }
        }
    }

    companion object {
        /**
         * 让引导状态跨 Activity 重建存活。
         *
         * 只放进 rememberSaveable，**不写进 DataStore**：避免把「正在引导中」这种临时状态持久化，
         * 持久化的只有 `prefs.tutorialSeen` 这一个「是否看过」的布尔值。
         */
        val saver: Saver<TutorialController, List<Any>> = Saver(
            save = {
                arrayListOf<Any>(
                    it.state.active,
                    it.state.index,
                    it.state.showWelcome,
                    it.state.finished
                )
            },
            restore = { saved ->
                TutorialController().apply {
                    applyState(
                        TutorialUiState(
                            active = saved[0] as Boolean,
                            index = saved[1] as Int,
                            showWelcome = saved[2] as Boolean,
                            finished = saved[3] as Boolean
                        )
                    )
                }
            }
        )
    }
}

/** 常规获取方式：旋转屏幕 / 进程重建后引导停在同一格，不会闪回欢迎卡片。 */
@Composable
fun rememberTutorialController(): TutorialController =
    rememberSaveable(saver = TutorialController.saver) { TutorialController() }
