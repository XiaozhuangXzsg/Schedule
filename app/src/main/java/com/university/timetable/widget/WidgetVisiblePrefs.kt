package com.university.timetable.widget

import com.university.timetable.data.prefs.UiPreferences

/**
 * 桌面卡片**显示内容真正依赖**的那几个偏好。
 *
 * 存在的意义是让「改了设置 → 卡片立刻变」这件事不依赖记性：
 * [com.university.timetable.TimetableApp] 盯着这个对象的实例，
 * 只要不相等就推一次卡片刷新 —— 以后往卡片里加一项受偏好控制的显示，
 * 只要在这里补一个字段就行，不会出现「某个开关改了卡片不动」。
 *
 * 字段来源：`GlanceWidgets.kt` 里 `WidgetDataLoader.loadToday/loadWeek` 与
 * `widgetThemeOf` 读到的 `prefs.*`。**故意只放这些**，
 * 这样改昵称、改提醒时间、改洗衣机倒计时之类与卡片无关的操作不会白唤醒启动器。
 */
internal data class WidgetVisiblePrefs(
    /** 卡片底色透明度。 */
    val widgetBgAlpha: Float,
    /** 主题色预设编号。 */
    val themePreset: Int,
    /** 哥伦比娅（粉紫）风格开关。 */
    val columbinaStyle: Boolean,
    /** 手动深浅色。 */
    val darkTheme: Boolean,
    /** 是否跟随系统深浅色。 */
    val followSystemTheme: Boolean,
    /** 学期开始日期（ISO），决定卡片上的周次。 */
    val semesterStart: String,
    /** 总周数，决定周次的上限。 */
    val totalWeeks: Int
) {
    companion object {
        fun from(prefs: UiPreferences) = WidgetVisiblePrefs(
            widgetBgAlpha = prefs.widgetBgAlpha,
            themePreset = prefs.themePreset,
            columbinaStyle = prefs.columbinaStyle,
            darkTheme = prefs.darkTheme,
            followSystemTheme = prefs.followSystemTheme,
            semesterStart = prefs.semesterStart,
            totalWeeks = prefs.totalWeeks
        )
    }
}
