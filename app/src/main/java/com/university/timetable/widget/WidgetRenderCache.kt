package com.university.timetable.widget

import androidx.compose.runtime.mutableStateOf

/**
 * 桌面卡片「这一轮该渲染什么」的暂存处。
 *
 * 为什么需要它：Glance 的 [androidx.glance.appwidget.GlanceAppWidget.provideGlance]
 * **每个会话只执行一次**，之后的 `updateAll` 只是把已经存在的组合重新渲染一遍。
 * 如果数据是在 `provideGlance` 里读一次、再被 `provideContent { … }` 捕获成常量，
 * 那么这份数据会跟着会话一直缓存下去 —— 表现就是用户报的
 * 「改了透明度 / 改了课表，卡片不动，必须完全退出应用再进来才生效」。
 *
 * 把数据放进 Compose 状态里，刷新前由 [WidgetRefresher] 写入，
 * 组合读它：状态一变组合就失效，下一次 `updateAll` 渲染出来的就是新内容。
 *
 * 用状态而不是把数据塞进 Glance 的 DataStore，是因为 [WidgetData] 里带列表和配色，
 * 序列化一份既慢又容易和 Room 里的真实数据不同步；这里只是同一进程内的引用替换。
 */
internal object WidgetRenderCache {

    /** 「今日 / 待办 + 课表」这三张卡片共用的数据。 */
    val today = mutableStateOf<WidgetData?>(null)

    /** 「本周课表」卡片的数据。 */
    val week = mutableStateOf<WidgetData?>(null)
}
