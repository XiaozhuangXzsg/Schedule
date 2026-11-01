package com.university.timetable.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** 页面标识，用于「独立设置各页面背景」。 */
object PageKeys {
    const val TODAY = "today"
    const val WEEK = "week"
    const val SETTINGS = "settings"
}

/**
 * 已上完课程的显示方式。
 */
object PastCourseMode {
    /** 置灰显示（默认） */
    const val DIM = 0

    /** 直接隐藏 */
    const val HIDE = 1
}

/**
 * 子页面转场动画风格。
 */
object TransitionStyle {
    /** 交叉淡化（默认） */
    const val CROSSFADE = 0

    /** 缩放淡入 */
    const val ZOOM = 1

    /**
     * 流体滑入（ColorOS 17「流体设计」动效）。
     *
     * 与 [CROSSFADE] / [ZOOM] 的区别：不用固定时长的 tween，改用低弹弹簧，
     * 位移更远、带轻微回弹与缩放，收尾不是「准时停住」而是「弹到静止」。
     */
    const val FLUID = 2
}

/**
 * 周视图「当前时间轴条」的样式（模仿 iOS 日历顶部那条红线）。
 */
object NowLineStyle {
    /** 细线 + 左端小圆球（iOS 风格，默认） */
    const val DOT = 0

    /** 加粗线 + 左端大圆球 */
    const val BOLD = 1

    /** 只要细线，不带圆球 */
    const val PLAIN = 2

    /** 虚线 + 小圆球 */
    const val DASHED = 3
}

/**
 * 今日页「正在上的这节课」的进度显示样式。
 */
object NowProgressStyle {
    /** 从左到右推进（默认） */
    const val LEFT_TO_RIGHT = 0

    /** 从下到上漫上来 */
    const val BOTTOM_TO_TOP = 1
}

/**
 * 用户偏好设置（DataStore 持久化）。
 */
data class UiPreferences(
    val darkTheme: Boolean = false,
    /** 主题色种子索引，见 ThemePresets */
    val themePreset: Int = 0,
    /** 是否跟随系统深浅色 */
    val followSystemTheme: Boolean = true,
    /** 学期第 1 周的周一，ISO 格式 */
    val semesterStart: String = "2026-08-31",
    /** 总周数 */
    val totalWeeks: Int = 20,
    /** 打开应用时定位到的周次，null 表示自动按当前日期计算 */
    val manualWeek: Int? = null,
    /** 简洁模式（类 WakeUp VIP 简洁模式） */
    val simpleMode: Boolean = false,
    /** 显示教师名 */
    val showTeacher: Boolean = true,
    /** 显示上课地点 */
    val showLocation: Boolean = true,
    /** 显示单双周标记 */
    val showWeekTag: Boolean = true,
    /** 网格显示周末 */
    val showWeekend: Boolean = true,
    /** 12/24 小时制（时间轴文本） */
    val use24Hour: Boolean = true,
    /** 上课提醒提前分钟数 */
    val remindBeforeMinutes: Int = 15,
    /** 是否开启上课提醒 */
    val reminderEnabled: Boolean = true,
    /** 是否开启流体云常驻通知 */
    val fluidCloudEnabled: Boolean = true,
    /** 时间轴显示当前时间指示线 */
    val showNowIndicator: Boolean = true,
    /** 今日页显示校园生活助手卡片 */
    val showLifeAssistant: Boolean = true,
    /** 各页面自定义背景色（ARGB），-1 表示未设置 */
    val todayBackground: Int = UNSET,
    val weekBackground: Int = UNSET,
    val settingsBackground: Int = UNSET,
    /** 各页面自定义背景图 content uri */
    val todayBackgroundImage: String = "",
    val weekBackgroundImage: String = "",
    val settingsBackgroundImage: String = "",
    /** 背景图透明度 0f..1f */
    val backgroundImageAlpha: Float = 0.35f,
    /** 背景图模糊半径（dp），0 表示不模糊 */
    val backgroundImageBlur: Float = 16f,
    /** 是否已写入示例课表 */
    val seeded: Boolean = false,
    /** 已上完课程的显示方式，见 [PastCourseMode] */
    val pastCourseMode: Int = PastCourseMode.DIM,
    /** 子页面转场动画，见 [TransitionStyle] */
    val transitionStyle: Int = TransitionStyle.CROSSFADE,
    /** 是否已经看过首次启动的软件说明（看过之后不再自动弹出） */
    val introSeen: Boolean = false,
    /** 设置页顶部展示的昵称 */
    val nickname: String = DEFAULT_NICKNAME,
    /** 自定义头像的 content uri，空串表示使用应用图标作为默认头像 */
    val avatarUri: String = "",
    /** 哥伦比娅（娅娅）主题风格：蔷薇紫金配色 + 羽翼/星钻装饰 */
    val columbinaStyle: Boolean = true,
    /** 液态玻璃（Apple Liquid Glass）视觉风格，只改观感，不影响任何业务逻辑 */
    val liquidGlass: Boolean = false,
    /** 周视图是否显示「当前时间轴条」 */
    val weekNowLine: Boolean = true,
    /** 时间轴条样式，见 [NowLineStyle] */
    val weekNowLineStyle: Int = NowLineStyle.DOT,
    /** 时间轴条颜色（ARGB），[UNSET] 表示跟随主题强调色 */
    val weekNowLineColor: Int = UNSET,
    /** 今日页「正在上的课」是否显示进度 */
    val nowProgress: Boolean = true,
    /** 进度显示样式，见 [NowProgressStyle] */
    val nowProgressStyle: Int = NowProgressStyle.LEFT_TO_RIGHT,
    /** 是否已经完成或跳过新手引导 */
    val tutorialSeen: Boolean = false,
    /**
     * 桌面卡片背景的不透明度（[MIN_WIDGET_BG_ALPHA]~1）。
     *
     * 1 表示完全不透明；调低后卡片会透出桌面壁纸。
     * 只作用于桌面卡片，应用内页面不受影响。
     */
    val widgetBgAlpha: Float = 1f,
    /**
     * 触感反馈（柔性反馈）开关。
     *
     * 开启后轻点开关、切换标签页、长按课程等操作会触发极轻的系统 tick。
     * 系统层面「触感反馈」总开关关闭时，这些调用会自动失效。
     */
    val hapticFeedback: Boolean = true,
    /** 是否允许启动时自动检查更新（每天最多一次） */
    val autoCheckUpdates: Boolean = true,
    /** 上次检查更新的时间戳（毫秒），0 表示从未检查 */
    val lastUpdateCheckAt: Long = 0L,
    /** 用户点了「稍后再说」的版本号，0 表示没有 */
    val postponedVersionCode: Int = 0,
    /** 用户点「稍后再说」的时间戳（毫秒） */
    val postponedAt: Long = 0L
) {
    companion object {
        const val UNSET = -1

        /** 卡片背景透明度的下限。再低文字会压在壁纸上，读不清。 */
        const val MIN_WIDGET_BG_ALPHA = 0.35f

        /** 默认昵称。 */
        const val DEFAULT_NICKNAME = "娅娅"
        fun backgroundFor(prefs: UiPreferences, page: String): Int = when (page) {
            PageKeys.TODAY -> prefs.todayBackground
            PageKeys.WEEK -> prefs.weekBackground
            else -> prefs.settingsBackground
        }

        fun backgroundImageFor(prefs: UiPreferences, page: String): String = when (page) {
            PageKeys.TODAY -> prefs.todayBackgroundImage
            PageKeys.WEEK -> prefs.weekBackgroundImage
            else -> prefs.settingsBackgroundImage
        }
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ui_preferences")

/**
 * 偏好设置仓库。所有写入均为挂起函数，读取暴露为 Flow。
 */
class PreferencesRepository(private val context: Context) {

    private object Keys {
        val darkTheme = booleanPreferencesKey("dark_theme")
        val themePreset = intPreferencesKey("theme_preset")
        val followSystem = booleanPreferencesKey("follow_system_theme")
        val semesterStart = stringPreferencesKey("semester_start")
        val totalWeeks = intPreferencesKey("total_weeks")
        val manualWeek = intPreferencesKey("manual_week")
        val simpleMode = booleanPreferencesKey("simple_mode")
        val showTeacher = booleanPreferencesKey("show_teacher")
        val showLocation = booleanPreferencesKey("show_location")
        val showWeekTag = booleanPreferencesKey("show_week_tag")
        val showWeekend = booleanPreferencesKey("show_weekend")
        val use24Hour = booleanPreferencesKey("use_24_hour")
        val remindBefore = intPreferencesKey("remind_before")
        val reminderEnabled = booleanPreferencesKey("reminder_enabled")
        val fluidCloud = booleanPreferencesKey("fluid_cloud")
        val nowIndicator = booleanPreferencesKey("now_indicator")
        val lifeAssistant = booleanPreferencesKey("life_assistant")
        val todayBg = intPreferencesKey("today_bg")
        val weekBg = intPreferencesKey("week_bg")
        val settingsBg = intPreferencesKey("settings_bg")
        val todayBgImage = stringPreferencesKey("today_bg_image")
        val weekBgImage = stringPreferencesKey("week_bg_image")
        val settingsBgImage = stringPreferencesKey("settings_bg_image")
        val bgAlpha = floatPreferencesKey("bg_alpha")
        val bgBlur = floatPreferencesKey("bg_blur")
        val seeded = booleanPreferencesKey("seeded")
        val lastReminded = stringPreferencesKey("last_reminded_key")
        val pastCourseMode = intPreferencesKey("past_course_mode")
        val transitionStyle = intPreferencesKey("transition_style")
        val introSeen = booleanPreferencesKey("intro_seen")
        val nickname = stringPreferencesKey("nickname")
        val avatarUri = stringPreferencesKey("avatar_uri")
        val columbinaStyle = booleanPreferencesKey("columbina_style")
        val liquidGlass = booleanPreferencesKey("liquid_glass")
        val weekNowLine = booleanPreferencesKey("week_now_line")
        val weekNowLineStyle = intPreferencesKey("week_now_line_style")
        val weekNowLineColor = intPreferencesKey("week_now_line_color")
        val nowProgress = booleanPreferencesKey("now_progress")
        val nowProgressStyle = intPreferencesKey("now_progress_style")
        val tutorialSeen = booleanPreferencesKey("tutorial_seen")
        val widgetBgAlpha = floatPreferencesKey("widget_bg_alpha")
        val hapticFeedback = booleanPreferencesKey("haptic_feedback")
        val autoCheckUpdates = booleanPreferencesKey("auto_check_updates")
        val lastUpdateCheckAt = longPreferencesKey("last_update_check_at")
        val postponedVersionCode = intPreferencesKey("postponed_version_code")
        val postponedAt = longPreferencesKey("postponed_at")
    }

    val preferences: Flow<UiPreferences> = context.dataStore.data.map { p ->
        UiPreferences(
            darkTheme = p[Keys.darkTheme] ?: false,
            themePreset = p[Keys.themePreset] ?: 0,
            followSystemTheme = p[Keys.followSystem] ?: true,
            semesterStart = p[Keys.semesterStart] ?: defaultSemesterStart(),
            totalWeeks = p[Keys.totalWeeks] ?: 20,
            manualWeek = p[Keys.manualWeek],
            simpleMode = p[Keys.simpleMode] ?: false,
            showTeacher = p[Keys.showTeacher] ?: true,
            showLocation = p[Keys.showLocation] ?: true,
            showWeekTag = p[Keys.showWeekTag] ?: true,
            showWeekend = p[Keys.showWeekend] ?: true,
            use24Hour = p[Keys.use24Hour] ?: true,
            remindBeforeMinutes = p[Keys.remindBefore] ?: 15,
            reminderEnabled = p[Keys.reminderEnabled] ?: true,
            fluidCloudEnabled = p[Keys.fluidCloud] ?: true,
            showNowIndicator = p[Keys.nowIndicator] ?: true,
            showLifeAssistant = p[Keys.lifeAssistant] ?: true,
            todayBackground = p[Keys.todayBg] ?: UiPreferences.UNSET,
            weekBackground = p[Keys.weekBg] ?: UiPreferences.UNSET,
            settingsBackground = p[Keys.settingsBg] ?: UiPreferences.UNSET,
            todayBackgroundImage = p[Keys.todayBgImage] ?: "",
            weekBackgroundImage = p[Keys.weekBgImage] ?: "",
            settingsBackgroundImage = p[Keys.settingsBgImage] ?: "",
            backgroundImageAlpha = p[Keys.bgAlpha] ?: 0.35f,
            backgroundImageBlur = p[Keys.bgBlur] ?: 16f,
            seeded = p[Keys.seeded] ?: false,
            pastCourseMode = p[Keys.pastCourseMode] ?: PastCourseMode.DIM,
            transitionStyle = p[Keys.transitionStyle] ?: TransitionStyle.CROSSFADE,
            introSeen = p[Keys.introSeen] ?: false,
            nickname = p[Keys.nickname] ?: UiPreferences.DEFAULT_NICKNAME,
            avatarUri = p[Keys.avatarUri] ?: "",
            columbinaStyle = p[Keys.columbinaStyle] ?: true,
            liquidGlass = p[Keys.liquidGlass] ?: false,
            weekNowLine = p[Keys.weekNowLine] ?: true,
            weekNowLineStyle = p[Keys.weekNowLineStyle] ?: NowLineStyle.DOT,
            weekNowLineColor = p[Keys.weekNowLineColor] ?: UiPreferences.UNSET,
            nowProgress = p[Keys.nowProgress] ?: true,
            nowProgressStyle = p[Keys.nowProgressStyle] ?: NowProgressStyle.LEFT_TO_RIGHT,
            tutorialSeen = p[Keys.tutorialSeen] ?: false,
            widgetBgAlpha = (p[Keys.widgetBgAlpha] ?: 1f)
                .coerceIn(UiPreferences.MIN_WIDGET_BG_ALPHA, 1f),
            hapticFeedback = p[Keys.hapticFeedback] ?: true,
            autoCheckUpdates = p[Keys.autoCheckUpdates] ?: true,
            lastUpdateCheckAt = p[Keys.lastUpdateCheckAt] ?: 0L,
            postponedVersionCode = p[Keys.postponedVersionCode] ?: 0,
            postponedAt = p[Keys.postponedAt] ?: 0L
        )
    }

    suspend fun setDarkTheme(value: Boolean) = edit { it[Keys.darkTheme] = value }

    suspend fun setFollowSystem(value: Boolean) = edit { it[Keys.followSystem] = value }

    suspend fun setThemePreset(index: Int) = edit { it[Keys.themePreset] = index }

    suspend fun setSemesterStart(isoDate: String) = edit { it[Keys.semesterStart] = isoDate }

    suspend fun setTotalWeeks(weeks: Int) = edit { it[Keys.totalWeeks] = weeks.coerceIn(1, 40) }

    suspend fun setManualWeek(week: Int?) = edit { prefs ->
        if (week == null) prefs.remove(Keys.manualWeek) else prefs[Keys.manualWeek] = week.coerceAtLeast(1)
    }

    suspend fun setSimpleMode(value: Boolean) = edit { it[Keys.simpleMode] = value }

    suspend fun setShowTeacher(value: Boolean) = edit { it[Keys.showTeacher] = value }

    suspend fun setShowLocation(value: Boolean) = edit { it[Keys.showLocation] = value }

    suspend fun setShowWeekTag(value: Boolean) = edit { it[Keys.showWeekTag] = value }

    suspend fun setShowWeekend(value: Boolean) = edit { it[Keys.showWeekend] = value }

    suspend fun setUse24Hour(value: Boolean) = edit { it[Keys.use24Hour] = value }

    suspend fun setRemindBefore(minutes: Int) = edit { it[Keys.remindBefore] = minutes.coerceIn(0, 120) }

    suspend fun setReminderEnabled(value: Boolean) = edit { it[Keys.reminderEnabled] = value }

    suspend fun setFluidCloudEnabled(value: Boolean) = edit { it[Keys.fluidCloud] = value }

    suspend fun setShowNowIndicator(value: Boolean) = edit { it[Keys.nowIndicator] = value }

    suspend fun setShowLifeAssistant(value: Boolean) = edit { it[Keys.lifeAssistant] = value }

    suspend fun setPastCourseMode(mode: Int) = edit { it[Keys.pastCourseMode] = mode }

    suspend fun setTransitionStyle(style: Int) = edit { it[Keys.transitionStyle] = style }

    /** 标记首次启动的软件说明已展示过，之后不再自动弹出。 */
    suspend fun setIntroSeen(value: Boolean) = edit { it[Keys.introSeen] = value }

    /** 设置页顶部昵称；传入空白时回落到默认昵称。 */
    suspend fun setNickname(value: String) = edit {
        val trimmed = value.trim().take(16)
        if (trimmed.isEmpty()) it.remove(Keys.nickname) else it[Keys.nickname] = trimmed
    }

    /** 自定义头像 content uri；传空串恢复为默认头像（应用图标）。 */
    suspend fun setAvatarUri(uri: String) = edit {
        if (uri.isBlank()) it.remove(Keys.avatarUri) else it[Keys.avatarUri] = uri
    }

    /** 哥伦比娅（娅娅）主题风格开关。 */
    suspend fun setColumbinaStyle(value: Boolean) = edit { it[Keys.columbinaStyle] = value }

    suspend fun setLiquidGlass(value: Boolean) = edit { it[Keys.liquidGlass] = value }

    /** 周视图「当前时间轴条」开关。 */
    suspend fun setWeekNowLine(value: Boolean) = edit { it[Keys.weekNowLine] = value }

    /** 时间轴条样式，见 [NowLineStyle]。 */
    suspend fun setWeekNowLineStyle(style: Int) = edit { it[Keys.weekNowLineStyle] = style }

    /** 时间轴条颜色；传 [UiPreferences.UNSET] 恢复为跟随主题强调色。 */
    suspend fun setWeekNowLineColor(color: Int) = edit {
        if (color == UiPreferences.UNSET) {
            it.remove(Keys.weekNowLineColor)
        } else {
            it[Keys.weekNowLineColor] = color
        }
    }

    /** 今日页「正在上的课」进度开关。 */
    suspend fun setNowProgress(value: Boolean) = edit { it[Keys.nowProgress] = value }

    /** 进度样式，见 [NowProgressStyle]。 */
    suspend fun setNowProgressStyle(style: Int) = edit { it[Keys.nowProgressStyle] = style }

    /** 新手引导是否已完成或主动跳过。 */
    suspend fun setTutorialSeen(value: Boolean) = edit { it[Keys.tutorialSeen] = value }

    /** 桌面卡片背景不透明度；限制在 [UiPreferences.MIN_WIDGET_BG_ALPHA]~1 之间。 */
    suspend fun setWidgetBgAlpha(alpha: Float) =
        edit { it[Keys.widgetBgAlpha] = alpha.coerceIn(UiPreferences.MIN_WIDGET_BG_ALPHA, 1f) }

    suspend fun setHapticFeedback(value: Boolean) = edit { it[Keys.hapticFeedback] = value }

    suspend fun setPageBackground(page: String, color: Int) = edit { prefs ->
        when (page) {
            PageKeys.TODAY -> prefs[Keys.todayBg] = color
            PageKeys.WEEK -> prefs[Keys.weekBg] = color
            else -> prefs[Keys.settingsBg] = color
        }
    }

    suspend fun setPageBackgroundImage(page: String, uri: String) = edit { prefs ->
        when (page) {
            PageKeys.TODAY -> prefs[Keys.todayBgImage] = uri
            PageKeys.WEEK -> prefs[Keys.weekBgImage] = uri
            else -> prefs[Keys.settingsBgImage] = uri
        }
    }

    suspend fun setBackgroundImageAlpha(alpha: Float) =
        edit { it[Keys.bgAlpha] = alpha.coerceIn(0f, 1f) }

    suspend fun setBackgroundImageBlur(blur: Float) =
        edit { it[Keys.bgBlur] = blur.coerceIn(0f, 40f) }

    suspend fun setSeeded(value: Boolean) = edit { it[Keys.seeded] = value }

    suspend fun setAutoCheckUpdates(value: Boolean) = edit { it[Keys.autoCheckUpdates] = value }

    suspend fun setLastUpdateCheckAt(timestamp: Long) = edit { it[Keys.lastUpdateCheckAt] = timestamp }

    /** 记录「稍后再说」的版本与时间，用于控制提醒间隔。 */
    suspend fun setPostponed(versionCode: Int, timestamp: Long) = edit { prefs ->
        prefs[Keys.postponedVersionCode] = versionCode
        prefs[Keys.postponedAt] = timestamp
    }

    /** 读取上次已提醒的课程标识，避免同一节课重复提醒。 */
    suspend fun lastRemindedKey(): String =
        context.dataStore.data.first()[Keys.lastReminded] ?: ""

    suspend fun setLastRemindedKey(key: String) = edit { it[Keys.lastReminded] = key }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private fun defaultSemesterStart(): String {
        // 默认取「本周周一」作为第 1 周，保证开箱可用
        val today = LocalDate.now()
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        return monday.toString()
    }
}
