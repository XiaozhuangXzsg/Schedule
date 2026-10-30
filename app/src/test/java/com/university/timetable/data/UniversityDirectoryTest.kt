package com.university.timetable.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 高校目录资产的回归测试：直接读 `src/main/assets/university_directory.tsv`，
 * 保证打包进 APK 的那份数据是完整的、可解析的、兰大在里面的。
 */
class UniversityDirectoryTest {

    private val asset = File("src/main/assets/university_directory.tsv")

    @Test
    fun asset_exists_and_holds_all_schools() {
        assertTrue("资产文件缺失：${asset.absolutePath}", asset.isFile)
        val schools = UniversityDirectory.parse(asset.readText(Charsets.UTF_8))
        // 只收「办学层次 = 本科」：专科 1540 所与未载明 425 所不收，
        // 另有 54 所本科院校表里没有任何可用入口，也收不进来。
        assertEquals("高校数量", 1358, schools.size)
    }

    @Test
    fun every_school_has_url_province_and_initial() {
        val schools = UniversityDirectory.parse(asset.readText(Charsets.UTF_8))
        for (school in schools) {
            assertTrue("${school.name} 的 URL 不合法：${school.url}", school.url.startsWith("http"))
            assertTrue("${school.name} 缺省份", school.province.isNotBlank())
            assertTrue(
                "${school.name} 首字母未解析（${school.initial}）",
                school.initial != com.university.timetable.util.PinyinIndex.FALLBACK
            )
            assertTrue("${school.name} 首字母串为空", school.initials.isNotBlank())
        }
    }

    @Test
    fun lanzhou_university_is_present_and_pinned() {
        val schools = UniversityDirectory.parse(asset.readText(Charsets.UTF_8))
        val lzu = schools.firstOrNull { it.name == UniversityDirectory.PINNED_NAME }
        assertNotNull("目录里必须有兰州大学", lzu)
        assertEquals("甘肃省", lzu!!.province)
        assertEquals("https://jwk.lzu.edu.cn/academic/index_frame.jsp", lzu.url)
        assertEquals('L', lzu.initial)
    }

    @Test
    fun names_are_unique_and_sorted() {
        val schools = UniversityDirectory.parse(asset.readText(Charsets.UTF_8))
        assertEquals("校名不应重复", schools.size, schools.map { it.name }.toSet().size)
        val sorted = schools.sortedWith { a, b -> com.university.timetable.util.PinyinIndex.compare(a.name, b.name) }
        assertEquals("应按拼音序排列", schools.map { it.name }, sorted.map { it.name })
    }

    @Test
    fun parse_skips_broken_lines() {
        val text = buildString {
            append("北京大学\t北京市\thttps://a.example/login\n")
            append("\n")
            append("缺列\n")
            append("没有网址\t北京市\tnot-a-url\n")
            append("兰州大学\t甘肃省\thttps://jwk.lzu.edu.cn/academic/index_frame.jsp\n")
        }
        val schools = UniversityDirectory.parse(text)
        assertEquals(2, schools.size)
        assertEquals(setOf("北京大学", "兰州大学"), schools.map { it.name }.toSet())
        // 解析后按拼音序排列：北京(B) 在 兰州(L) 之前
        assertEquals(listOf("北京大学", "兰州大学"), schools.map { it.name })
    }

    @Test
    fun search_matches_name_province_and_pinyin_initials() {
        val schools = UniversityDirectory.parse(asset.readText(Charsets.UTF_8))
        fun hit(query: String) = schools.filter { it.matches(query) }.map { it.name }

        assertTrue("按校名", hit("北京大学").contains("北京大学"))
        assertTrue("按省份", hit("甘肃省").contains("兰州大学"))
        assertTrue("按拼音首字母", hit("bjdX").contains("北京大学"))
        assertTrue("按拼音首字母", hit("lzdx").contains("兰州大学"))
        assertEquals("空查询返回全部", schools.size, hit("").size)
    }
}
