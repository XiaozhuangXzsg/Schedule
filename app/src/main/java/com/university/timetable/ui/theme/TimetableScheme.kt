package com.university.timetable.ui.theme

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * 一套完整的应用配色（Material3 [ColorScheme] + 应用自己的 [TimetableColors]）。
 *
 * 之所以把配色计算从 `@Composable` 的 `UniversityTimetableTheme` 里抽出来，
 * 是因为**桌面小组件也要用同一套颜色**：Glance 不在 Compose 的组合作用域里，
 * 只能拿到一个普通的 `Context`，却必须算出和软件内完全一致的卡片底色 / 文字色。
 * 抽成纯函数后，界面和小组件调用的就是同一份逻辑，不会再出现「卡片主题和软件内不同步」。
 */
data class TimetableScheme(
    val colorScheme: ColorScheme,
    val extras: TimetableColors
)

/**
 * 应用自有的设计令牌（卡片底色、次要文字色……）。
 *
 * 纯函数，不依赖 Compose 运行时，界面与小组件共用。
 *
 * @param dark 是否深色
 * @param columbina 是否启用「娅娅 · 哥伦比娅」风格（优先级高于自选主题色）
 */
fun timetableColors(
    dark: Boolean,
    columbina: Boolean,
    liquid: Boolean = false
): TimetableColors {
    val base = baseTimetableColors(dark, columbina)
    return if (liquid) base.asLiquidGlass(dark) else base
}

/**
 * 把一套实色配色改写成「液态玻璃」用的半透明配色。
 *
 * 只改"面"的颜色（卡片、胶囊），不改文字色与状态色：玻璃需要有东西可以透上来，
 * 底色越不透明就越像一块塑料。透明度都压在 60%–90%，保证文字对比度仍然够。
 */
private fun TimetableColors.asLiquidGlass(dark: Boolean): TimetableColors = copy(
    gridLine = if (dark) Color(0x1FFFFFFF) else Color(0x0D141821),
    surfaceCard = if (dark) Color(0x991A1B21) else Color(0xB8FFFFFF),
    surfaceCardElevated = if (dark) Color(0xAD22242C) else Color(0xD9FFFFFF),
    capsuleContainer = if (dark) Color(0x2EFFFFFF) else Color(0x8AFFFFFF),
    capsuleSelected = if (dark) Color(0x3DFFFFFF) else Color(0xE6FFFFFF)
)

private fun baseTimetableColors(dark: Boolean, columbina: Boolean): TimetableColors = when {
    columbina && dark -> TimetableColors(
        nowIndicator = Color(0xFFFF7AA8),
        gridLine = Color(0x1FFFC9E4),
        surfaceCard = Color(0xFF221629),
        surfaceCardElevated = Color(0xFF2C1D34),
        mutedText = Color(0xFFB096BC),
        capsuleContainer = Color(0x26E0A6D0),
        capsuleSelected = Color(0xFF35233D),
        pageBackground = Color(0xFF140E18),
        success = Color(0xFF8FE3B0),
        successContainer = Color(0x2E3DB56B),
        onSuccessContainer = Color(0xFFA8EEC4),
        warning = Color(0xFFF3C08A),
        warningContainer = Color(0x33E8B96A),
        onWarningContainer = Color(0xFFFFD7A6),
        neutral = Color(0xFFBCA9C4),
        neutralContainer = Color(0x26FFFFFF),
        onNeutralContainer = Color(0xFFD3C2DA),
        holidayContainer = Color(0x3FF0508C),
        onHolidayContainer = Color(0xFFFFB3CC),
        makeupContainer = Color(0x33E8B96A),
        onMakeupContainer = Color(0xFFFFD7A6)
    )

    columbina -> TimetableColors(
        nowIndicator = Color(0xFFC7264F),
        gridLine = Color(0x14A8558C),
        surfaceCard = Color.White,
        surfaceCardElevated = Color(0xFFFDF7FC),
        mutedText = Color(0xFF9B849F),
        capsuleContainer = Color(0x14000000),
        capsuleSelected = Color.White,
        pageBackground = Color(0xFFFBF5FA),
        success = Color(0xFF2E9E5B),
        successContainer = Color(0x1A2E9E5B),
        onSuccessContainer = Color(0xFF2E9E5B),
        warning = Color(0xFFB76A1E),
        warningContainer = Color(0x1AC98A2E),
        onWarningContainer = Color(0xFFB76A1E),
        neutral = Color(0xFF7A6A80),
        neutralContainer = Color(0x14000000),
        onNeutralContainer = Color(0xFF7A6A80),
        holidayContainer = Color(0xFFFFE6EE),
        onHolidayContainer = Color(0xFFA3232F),
        makeupContainer = Color(0xFFFFF1E0),
        onMakeupContainer = Color(0xFF9A5B00)
    )

    dark -> TimetableColors(
        nowIndicator = Color(0xFFFF6B6B),
        gridLine = Color(0x1FFFFFFF),
        surfaceCard = Color(0xFF1B1C22),
        surfaceCardElevated = Color(0xFF23242B),
        mutedText = Color(0xFF9EA1AD),
        capsuleContainer = Color(0x1FFFFFFF),
        capsuleSelected = Color(0xFF2C2E37),
        pageBackground = Color(0xFF101114),
        success = Color(0xFF6FD79A),
        successContainer = Color(0x2E3DB56B),
        onSuccessContainer = Color(0xFF8FE3B0),
        warning = Color(0xFFF3B276),
        warningContainer = Color(0x2EF97A31),
        onWarningContainer = Color(0xFFFFC894),
        neutral = Color(0xFFA6A8B3),
        neutralContainer = Color(0x1FFFFFFF),
        onNeutralContainer = Color(0xFFB9BBC6),
        holidayContainer = Color(0x33FF6B81),
        onHolidayContainer = Color(0xFFFFB3BC),
        makeupContainer = Color(0x33F97A31),
        onMakeupContainer = Color(0xFFFFC894)
    )

    else -> TimetableColors(
        nowIndicator = Color(0xFFE5484D),
        gridLine = Color(0x0F000000),
        surfaceCard = Color.White,
        surfaceCardElevated = Color(0xFFFCFCFE),
        mutedText = Color(0xFF9A9AA2),
        capsuleContainer = Color(0x0F000000),
        capsuleSelected = Color.White,
        pageBackground = Color(0xFFF6F7FB),
        success = Color(0xFF2E9E5B),
        successContainer = Color(0x1A2E9E5B),
        onSuccessContainer = Color(0xFF2E9E5B),
        warning = Color(0xFFB76A1E),
        warningContainer = Color(0x1AF97A31),
        onWarningContainer = Color(0xFFB76A1E),
        neutral = Color(0xFF6B6B73),
        neutralContainer = Color(0x14000000),
        onNeutralContainer = Color(0xFF6B6B73),
        holidayContainer = Color(0xFFFFE9EC),
        onHolidayContainer = Color(0xFFA3232F),
        makeupContainer = Color(0xFFFFF1E0),
        onMakeupContainer = Color(0xFF9A5B00)
    )
}

/**
 * 不使用动态取色时的完整配色。
 *
 * @param presetIndex [ThemePresets.all] 的下标，仅在 [columbina] 为 false 时生效
 */
fun timetableScheme(
    dark: Boolean,
    presetIndex: Int,
    columbina: Boolean,
    liquid: Boolean = false
): TimetableScheme {
    val preset = if (columbina) ThemePresets.columbina else ThemePresets.byIndex(presetIndex)

    val colorScheme = when {
        columbina && dark -> darkColorScheme(
            primary = preset.seedDark,
            onPrimary = Color(0xFF3A0A22),
            primaryContainer = preset.seedDark.copy(alpha = 0.20f),
            onPrimaryContainer = Color(0xFFFFC9DE),
            secondary = ThemePresets.columbinaFeatherDark,
            background = Color(0xFF140E18),
            onBackground = Color(0xFFF0E7F2),
            surface = Color(0xFF1D1421),
            onSurface = Color(0xFFF0E7F2),
            surfaceVariant = Color(0xFF2B1C31),
            onSurfaceVariant = Color(0xFFC6B4CC),
            outlineVariant = Color(0xFF3A2742),
            error = Color(0xFFFF6B8A)
        )

        columbina -> lightColorScheme(
            primary = preset.seedLight,
            onPrimary = Color.White,
            primaryContainer = preset.seedLight.copy(alpha = 0.12f),
            onPrimaryContainer = preset.seedLight,
            secondary = ThemePresets.columbinaFeatherLight,
            background = Color(0xFFFBF5FA),
            onBackground = Color(0xFF20161F),
            surface = Color.White,
            onSurface = Color(0xFF20161F),
            surfaceVariant = Color(0xFFF6EBF4),
            onSurfaceVariant = Color(0xFF6B5470),
            outlineVariant = Color(0xFFEEDFEA),
            error = Color(0xFFC7264F)
        )

        dark -> darkColorScheme(
            primary = preset.seedDark,
            onPrimary = Color(0xFF10121A),
            primaryContainer = preset.seedDark.copy(alpha = 0.22f),
            onPrimaryContainer = preset.seedDark,
            secondary = preset.accent,
            background = Color(0xFF101114),
            onBackground = Color(0xFFE7E7EC),
            surface = Color(0xFF16171C),
            onSurface = Color(0xFFE7E7EC),
            surfaceVariant = Color(0xFF23242B),
            onSurfaceVariant = Color(0xFFB6B7C0),
            outlineVariant = Color(0xFF2E3038),
            error = Color(0xFFFF6B6B)
        )

        else -> lightColorScheme(
            primary = preset.seedLight,
            onPrimary = Color.White,
            primaryContainer = preset.seedLight.copy(alpha = 0.14f),
            onPrimaryContainer = preset.seedLight,
            secondary = preset.accent,
            background = Color(0xFFF6F7FB),
            onBackground = Color(0xFF16181D),
            surface = Color.White,
            onSurface = Color(0xFF16181D),
            surfaceVariant = Color(0xFFF0F1F6),
            onSurfaceVariant = Color(0xFF5C6070),
            outlineVariant = Color(0xFFE4E6EE),
            error = Color(0xFFD93B3B)
        )
    }

    return TimetableScheme(colorScheme, timetableColors(dark, columbina, liquid))
}

/** 系统是否处于深色模式（供非 Compose 场景使用，例如桌面小组件）。 */
fun isSystemDark(context: Context): Boolean =
    (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES

/**
 * 根据用户偏好解析出「当前实际生效的深浅色」。
 *
 * @param followSystem 偏好里的「跟随系统深浅色」
 * @param darkTheme 偏好里的手动深浅色（仅在 [followSystem] 为 false 时有意义）
 */
fun resolveDark(context: Context, followSystem: Boolean, darkTheme: Boolean): Boolean =
    if (followSystem) isSystemDark(context) else darkTheme
