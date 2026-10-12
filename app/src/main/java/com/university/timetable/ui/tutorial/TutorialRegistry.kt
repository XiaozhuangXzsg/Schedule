package com.university.timetable.ui.tutorial

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * 引导锚点登记处。
 *
 * 目标组件只要在修饰符链上挂一个 [tutorialAnchor]，它每一帧的真实位置就会写进这里，
 * 引导层据此「挖洞」高亮 —— 不需要任何硬编码坐标，屏幕尺寸 / 字体大小 / 深色模式全自动适配。
 *
 * 用 `mutableStateMapOf` 而不是普通 Map：引导层读取时天然建立快照依赖，锚点增减会自动触发重组。
 */
class TutorialAnchorRegistry {

    private val anchors = mutableStateMapOf<String, Rect>()

    /**
     * 记录某个锚点的位置。
     *
     * 关键点：**值没变就不写**。`onGloballyPositioned` 每帧都会回调，
     * 而 SnapshotStateMap 即使写入相同值也会记录一次变更 → 引导层重组 → 又触发一轮布局，
     * 严重点会变成掉帧甚至死循环。这里做一次相等判断把它掐掉。
     */
    fun report(id: String, bounds: Rect) {
        if (id.isEmpty()) return
        if (bounds.width <= 0f || bounds.height <= 0f) return
        if (anchors[id] == bounds) return
        anchors[id] = bounds
    }

    /** 目标组件离开组合时调用，避免引导层继续对一个已经不存在的目标挖洞。 */
    fun clear(id: String) {
        anchors.remove(id)
    }

    fun boundsOf(id: String): Rect? = anchors[id]

    fun has(id: String): Boolean = anchors.containsKey(id)

    /** 当前全部锚点。在组合里读它会订阅整张表，锚点变化即触发重组。 */
    fun snapshot(): Map<String, Rect> = anchors.toMap()

    /** 测试用：清空全部登记。 */
    fun clearAll() {
        anchors.clear()
    }
}

/**
 * 把 [TutorialAnchorRegistry] 往下传。
 *
 * 默认 null：没有引导层时，[tutorialAnchor] 直接原样返回，**零成本**（不注册回调、不产生状态）。
 */
val LocalTutorialRegistry = staticCompositionLocalOf<TutorialAnchorRegistry?> { null }

/**
 * 给任意组件登记一个引导锚点。
 *
 * 用法（在修饰符链里）：
 * ```
 * Box(modifier = Modifier.fillMaxWidth().tutorialAnchor(TutorialAnchors.WIDGET_ENTRY)) { ... }
 * ```
 *
 * 没有引导层时返回原 Modifier；离开组合时自动注销。
 */
@Composable
fun Modifier.tutorialAnchor(id: String): Modifier {
    val registry = LocalTutorialRegistry.current ?: return this
    if (id.isEmpty()) return this

    DisposableEffect(registry, id) {
        onDispose { registry.clear(id) }
    }

    return this.onGloballyPositioned { coordinates ->
        registry.report(id, coordinates.boundsInRoot())
    }
}
