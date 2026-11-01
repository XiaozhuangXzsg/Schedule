package com.university.timetable.ui.theme

import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 应用级额外设计令牌（Material3 之外的补充）。
 *
 * 注意：所有颜色都必须随深浅色切换，
 * 不要把 `Color(0xFF...)` 直接写在各个页面里，否则夜间模式会出现深色文字看不清的问题。
 */
data class TimetableColors(
    val nowIndicator: Color,
    val gridLine: Color,
    val surfaceCard: Color,
    val surfaceCardElevated: Color,
    val mutedText: Color,
    val capsuleContainer: Color,
    val capsuleSelected: Color,
    val pageBackground: Color,
    /** 设施「开放中」等正向状态 */
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    /** 「未开放 / 待开始」等提示状态 */
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    /** 中性「休息 / 已结束」状态 */
    val neutral: Color,
    val neutralContainer: Color,
    val onNeutralContainer: Color,
    /** 节假日提示底色（粉色系） */
    val holidayContainer: Color,
    val onHolidayContainer: Color,
    /** 调休提示底色（橙色系） */
    val makeupContainer: Color,
    val onMakeupContainer: Color
)

val LocalTimetableColors = staticCompositionLocalOf {
    TimetableColors(
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

val LocalIsDark = staticCompositionLocalOf { false }

/**
 * 是否启用「娅娅 · 哥伦比娅」风格。
 *
 * 页面可以据此追加羽毛 / 星钻等装饰元素，
 * 让个性化开关的开关只写一处、全应用生效。
 */
val LocalColumbinaStyle = staticCompositionLocalOf { false }

/**
 * 是否启用「液态玻璃（Liquid Glass）」视觉风格。
 *
 * 打开后玻璃面板会更薄更透、带镜面扫光与接触阴影，页面背景会出现柔和环境光；
 * 关掉就是原来的样子。它只影响"长什么样"，不参与任何业务判断。
 */
val LocalLiquidGlassStyle = staticCompositionLocalOf { false }

/**
 * 系统是否要求「减少动态效果」。
 *
 * 无障碍设置里把动画速度调到 0（或关闭动画）时，所有 Spring / 过渡都应退化成瞬时切换。
 * 读取 Settings.Global 不需要权限，且这里只在组合首次执行时读一次。
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** 读取系统的动画缩放设置，得到 [LocalReduceMotion] 的值。 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val resolver = context.contentResolver
        val animator = Settings.Global.getFloat(
            resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        )
        val transition = Settings.Global.getFloat(
            resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f
        )
        animator == 0f || transition == 0f
    }
}

/**
 * 页面文字颜色令牌。
 *
 * **约定**：界面上所有正文与标题都应显式使用这两个（或 `MaterialTheme.colorScheme.onSurface`），
 * 不要依赖 Material3 的默认文字色——在 Dialog / DropdownMenu / Popup 等作用域里，
 * 默认值可能不随主题变化，夜间模式下就会出现深色文字看不清的问题。
 */
val pageText: Color
    @Composable get() = MaterialTheme.colorScheme.onSurface

/** 次要文字（说明、时间、副标题）。 */
val pageMuted: Color
    @Composable get() = LocalTimetableColors.current.mutedText

/** 卡片 / 弹窗背景。 */
val pageCard: Color
    @Composable get() = LocalTimetableColors.current.surfaceCard

@Composable
fun UniversityTimetableTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themePresetIndex: Int = 0,
    columbina: Boolean = false,
    useDynamicColor: Boolean = false,
    liquidGlass: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val reduceMotion = rememberReduceMotion()
    // 配色计算抽到 TimetableScheme.kt 的纯函数里，桌面小组件复用同一份逻辑。
    val scheme = if (useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        TimetableScheme(
            colorScheme = if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context),
            extras = timetableColors(darkTheme, columbina, liquidGlass)
        )
    } else {
        timetableScheme(darkTheme, themePresetIndex, columbina, liquidGlass)
    }
    val colorScheme = scheme.colorScheme
    val extras = scheme.extras

    CompositionLocalProvider(
        LocalTimetableColors provides extras,
        LocalIsDark provides darkTheme,
        LocalColumbinaStyle provides columbina,
        LocalLiquidGlassStyle provides liquidGlass,
        LocalReduceMotion provides reduceMotion,
        // Compose 的 LocalContentColor 默认值是 Color.Black，只有 Surface / Card 等容器才会改写它。
        // 之前主题根节点没有提供，导致 Dialog（不在 Scaffold 的 Surface 之内）里所有
        // 未显式指定颜色的 Text / Icon 在夜间模式下都是黑字，看不见。
        LocalContentColor provides colorScheme.onBackground
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = TimetableTypography,
            content = content
        )
    }
}

val TimetableTypography = androidx.compose.material3.Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 30.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 24.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 13.5.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 13.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 11.5.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 10.sp)
)
