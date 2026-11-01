package com.university.timetable.update

/**
 * 版本号工具。
 *
 * 更新判断只认 versionCode（一个单调递增的整数），不比较版本名字符串：
 * "1.10.0" 与 "1.9.0" 按字符串比会得出错误结果，按 code 比则永远正确。
 *
 * code 的推导规则与 app/build.gradle.kts 中的 versionCodeOf 完全一致：
 * major * 10000 + minor * 100 + patch（1.0.0 -> 10000）。
 */
object VersionNumbers {

    private val VERSION_REGEX = Regex("^v?(\\d{1,2})[.](\\d{1,2})[.](\\d{1,2})$")

    fun codeOf(major: Int, minor: Int, patch: Int): Long =
        major.toLong() * 10000L + minor.toLong() * 100L + patch.toLong()

    /** 解析 "1.2.3" / "v1.2.3"；不合法（或某一段超过 99）时返回 null。 */
    fun parseCode(name: String): Long? {
        val match = VERSION_REGEX.matchEntire(name.trim()) ?: return null
        val major = match.groupValues[1].toIntOrNull() ?: return null
        val minor = match.groupValues[2].toIntOrNull() ?: return null
        val patch = match.groupValues[3].toIntOrNull() ?: return null
        if (major > 99 || minor > 99 || patch > 99) return null
        return codeOf(major, minor, patch)
    }

    /** 去掉可能存在的 "v" 前缀，用于显示。 */
    fun displayName(tagOrName: String): String = tagOrName.trim().removePrefix("v").removePrefix("V")

    fun isStableName(tagOrName: String): Boolean = parseCode(tagOrName) != null
}
