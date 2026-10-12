package com.university.timetable.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 主题色预设。每个预设提供浅色 / 深色两套种子色，
 * 用于生成 Material3 配色方案。
 */
data class ThemePreset(
    val name: String,
    val seedLight: Color,
    val seedDark: Color,
    val accent: Color
)

object ThemePresets {

    val all: List<ThemePreset> = listOf(
        ThemePreset("曜石蓝", Color(0xFF2F6BFF), Color(0xFF8FB0FF), Color(0xFF4C7DFF)),
        ThemePreset("薄荷青", Color(0xFF12B5A5), Color(0xFF6FDCCE), Color(0xFF1FC7B6)),
        ThemePreset("霞光粉", Color(0xFFF0508C), Color(0xFFFF9CBB), Color(0xFFFF6E9C)),
        ThemePreset("暖阳橙", Color(0xFFF97A31), Color(0xFFFFB183), Color(0xFFFF8D4D)),
        ThemePreset("香芋紫", Color(0xFF8257E6), Color(0xFFC0A6FF), Color(0xFF9B7BF0)),
        ThemePreset("松林绿", Color(0xFF2E9E5B), Color(0xFF7CD79B), Color(0xFF3DB56B)),
        ThemePreset("石墨灰", Color(0xFF4A5568), Color(0xFFA7B2C4), Color(0xFF5C6B82))
    )

    fun byIndex(index: Int): ThemePreset = all.getOrElse(index) { all.first() }

    /**
     * 「娅娅 · 哥伦比娅」专属配色：深紫发色 + 玫红挑染 + 白羽。
     *
     * 它**不放进 [all]**，因此不会出现在设置页的主题色色板里；
     * 由「哥伦比娅风格」开关单独启用（见 `UniversityTimetableTheme(columbina = …)`）。
     * 用户一旦点选色板里的任意颜色，就等于切换回普通风格。
     */
    val columbina = ThemePreset(
        name = "娅娅 · 哥伦比娅",
        seedLight = Color(0xFFB3266B),
        seedDark = Color(0xFFF4A6C8),
        accent = Color(0xFFE0407E)
    )

    /** 哥伦比娅风格的羽毛 / 星钻点缀色（浅色与深色各一枚）。 */
    val columbinaFeatherLight = Color(0xFF8B5CF6)
    val columbinaFeatherDark = Color(0xFFC9B4FF)
    val columbinaGold = Color(0xFFE8B96A)
}

/**
 * 课程色块的候选配色（柔和、低饱和，接近截图中的风格）。
 * index 由课程 id / 名称哈希决定，保证同名课程颜色稳定。
 */
object CoursePalette {

    val lightPalette: List<Pair<Color, Color>> = listOf(
        Color(0xFFE8F6FB) to Color(0xFF2E7D96), // 天蓝
        Color(0xFFFDECEF) to Color(0xFFC2415F), // 胭脂
        Color(0xFFEDF7EE) to Color(0xFF3B7A4A), // 茶绿
        Color(0xFFF3EEFC) to Color(0xFF6D4FBF), // 紫罗兰
        Color(0xFFFDF3E6) to Color(0xFFB76A1E), // 杏橙
        Color(0xFFEAF0FD) to Color(0xFF3455B5), // 靛青
        Color(0xFFFCEFF7) to Color(0xFFA64682), // 品红
        Color(0xFFEAF7F5) to Color(0xFF2C7C72) // 青瓷
    )

    val darkPalette: List<Pair<Color, Color>> = listOf(
        Color(0xFF17323D) to Color(0xFFA5DDF0),
        Color(0xFF3D1E27) to Color(0xFFF7B9C8),
        Color(0xFF1D3524) to Color(0xFFAEE3BC),
        Color(0xFF2B2440) to Color(0xFFCDBBFF),
        Color(0xFF3A2A16) to Color(0xFFF5CB96),
        Color(0xFF1E2740) to Color(0xFFB3C6FF),
        Color(0xFF3A2030) to Color(0xFFF3BCDC),
        Color(0xFF17332F) to Color(0xFFA6E3DC)
    )

    /** 依据任意 key 取稳定索引。 */
    fun indexFor(key: Any?): Int {
        val hash = key?.hashCode() ?: 0
        return ((hash % 8) + 8) % 8
    }

    /** 返回 (容器色, 前景色)。 */
    fun colorsFor(key: Any?, custom: Int?, dark: Boolean): Pair<Color, Color> {
        if (custom != null && custom != 0) {
            val container = Color(custom)
            val fg = if (container.luminance() > 0.55f) Color(0xFF1B1B1F) else Color(0xFFF3F3F7)
            return container to fg
        }
        val palette: List<Pair<Color, Color>> = if (dark) darkPalette else lightPalette
        return palette[indexFor(key)]
    }

    private fun Color.luminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue
}
