package com.university.timetable.data

import android.content.Context
import com.university.timetable.util.PinyinIndex

/** 目录里的一所学校。 */
data class University(
    val name: String,
    val province: String,
    val url: String,
    /** 首字母分组，`#` 表示无法判定。 */
    val initial: Char,
    /** 拼音首字母串，用来支持「输入 bjdx 也能搜到北京大学」。 */
    val initials: String
) {
    /** 搜索是否命中：校名包含 / 省份包含 / 拼音首字母前缀。 */
    fun matches(query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        if (name.contains(q, ignoreCase = true)) return true
        if (province.contains(q)) return true
        if (q.all { it in 'a'..'z' || it in 'A'..'Z' }) {
            val upper = q.uppercase()
            if (initials.startsWith(upper)) return true
            if (initials.contains(upper)) return true
        }
        return false
    }
}

/**
 * 全国高校教务系统入口目录。
 *
 * 数据来自内置资产 `assets/university_directory.tsv`（1358 所，三列：
 * 学校名称 / 省级地区 / 入口 URL），由 `全国高校课表网址目录_2026-10-20.xlsx` 导出。
 *
 * **只收录「办学层次 = 本科」的学校**：专科（1540 所）与办学层次未载明（425 所）
 * 一律不进目录；另有 54 所本科院校表里没有任何可用入口，也不收录（列出来也点不进去）。
 * 入口优先取表里的「网页导入优先入口」，没有时退回「原目录入口（追溯）」。
 *
 * 首次读取后缓存在内存里，之后不再碰 IO。
 */
object UniversityDirectory {

    /** 置顶的那一所。 */
    const val PINNED_NAME = "兰州大学"

    private const val ASSET = "university_directory.tsv"

    @Volatile
    private var cache: List<University>? = null

    /** 全部学校，已按拼音序排好。 */
    fun all(context: Context): List<University> {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            val loaded = load(context.applicationContext)
            cache = loaded
            return loaded
        }
    }

    /** 置顶项；目录里没有时返回 null。 */
    fun pinned(context: Context): University? =
        all(context).firstOrNull { it.name == PINNED_NAME }

    /** 除置顶项以外的全部学校。 */
    fun others(context: Context): List<University> =
        all(context).filter { it.name != PINNED_NAME }

    /** 按查询过滤，结果仍保持拼音序。 */
    fun search(context: Context, query: String): List<University> =
        all(context).filter { it.matches(query) }

    private fun load(context: Context): List<University> {
        val text = runCatching {
            context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrNull().orEmpty()
        return parse(text)
    }

    /** 资产文本 → 学校列表（纯函数，便于单测）。 */
    internal fun parse(text: String): List<University> {
        return text.lineSequence()
            .mapNotNull { line ->
                if (line.isBlank()) return@mapNotNull null
                val parts = line.split('\t')
                if (parts.size < 3) return@mapNotNull null
                val name = parts[0].trim()
                val url = parts[2].trim()
                if (name.isEmpty() || !url.startsWith("http", ignoreCase = true)) {
                    return@mapNotNull null
                }
                University(
                    name = name,
                    province = parts[1].trim(),
                    url = url,
                    initial = PinyinIndex.initialOf(name),
                    initials = PinyinIndex.initialsOf(name)
                )
            }
            .sortedWith { a, b -> PinyinIndex.compare(a.name, b.name) }
            .toList()
    }
}
