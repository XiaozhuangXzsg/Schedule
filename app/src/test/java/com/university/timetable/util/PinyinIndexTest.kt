package com.university.timetable.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 拼音首字母（GB2312 区位法）的回归测试。
 *
 * 这里守的是两件事：
 * 1. 23 个首字母分组全部可达，且边界字落在自己那一组；
 * 2. 排序与分组自洽（同一组内也是拼音序）。
 */
class PinyinIndexTest {

    @Test
    fun all_letters_are_reachable() {
        val samples = mapOf(
            "阿城学院" to 'A', "北京大学" to 'B', "擦擦学院" to 'C', "大连大学" to 'D',
            "峨眉学院" to 'E', "复旦大学" to 'F', "噶尔学院" to 'G', "哈尔滨工业大学" to 'H',
            "济南大学" to 'J', "喀什大学" to 'K', "兰州大学" to 'L', "牡丹江医学院" to 'M',
            "南昌大学" to 'N', "欧亚学院" to 'O', "攀枝花学院" to 'P', "青岛大学" to 'Q',
            "日照学院" to 'R', "上海交通大学" to 'S', "太原学院" to 'T', "温州大学" to 'W',
            "西安交通大学" to 'X', "烟台大学" to 'Y', "郑州大学" to 'Z'
        )
        for ((name, expected) in samples) {
            assertEquals("$name 的首字母", expected, PinyinIndex.initialOf(name))
        }
        assertEquals("分组数量", 23, PinyinIndex.letters.size)
    }

    @Test
    fun initial_of_typical_university_names() {
        assertEquals('B', PinyinIndex.initialOf("北京大学"))
        assertEquals('F', PinyinIndex.initialOf("复旦大学"))
        assertEquals('L', PinyinIndex.initialOf("兰州大学"))
        assertEquals('N', PinyinIndex.initialOf("南京大学"))
        assertEquals('Q', PinyinIndex.initialOf("清华大学"))
        assertEquals('S', PinyinIndex.initialOf("上海交通大学"))
        assertEquals('T', PinyinIndex.initialOf("同济大学"))
        assertEquals('W', PinyinIndex.initialOf("武汉大学"))
        assertEquals('X', PinyinIndex.initialOf("西安交通大学"))
        assertEquals('Z', PinyinIndex.initialOf("中南大学"))
        assertEquals('Z', PinyinIndex.initialOf("浙江大学"))
        assertEquals('Z', PinyinIndex.initialOf("遵义医科大学"))
        assertEquals('C', PinyinIndex.initialOf("长安大学"))
        assertEquals('C', PinyinIndex.initialOf("重庆大学"))
        assertEquals('J', PinyinIndex.initialOf("江苏大学"))
    }

    @Test
    fun same_syllable_characters_stay_in_one_group() {
        // 同音不同字（xī）必须都落在 X 组：这正是 Collator 锚点法会出错的地方。
        assertEquals('X', PinyinIndex.initialOf("西安交通大学"))
        assertEquals('X', PinyinIndex.initialOf("夕阳学院"))
        assertEquals('X', PinyinIndex.initialOf("昔阳学院"))
        assertEquals('L', PinyinIndex.initialOf("拉萨学院"))
        assertEquals('L', PinyinIndex.initialOf("垃圾学院"))
    }

    @Test
    fun initial_handles_latin_and_unknown() {
        assertEquals('A', PinyinIndex.initialOf("AP 学院"))
        assertEquals('Q', PinyinIndex.initialOf("q 大学"))
        assertEquals(PinyinIndex.FALLBACK, PinyinIndex.initialOf(""))
        assertEquals(PinyinIndex.FALLBACK, PinyinIndex.initialOf("2026 学院"))
        // GB2312 二级汉字（瓯 0xEAB1）不在拼音区，无法判定
        assertEquals(PinyinIndex.FALLBACK, PinyinIndex.initialOf("瓯江学院"))
    }

    @Test
    fun initials_of_full_name() {
        assertEquals("BJDX", PinyinIndex.initialsOf("北京大学"))
        assertEquals("LZDX", PinyinIndex.initialsOf("兰州大学"))
        assertEquals("XAJTDX", PinyinIndex.initialsOf("西安交通大学"))
    }

    @Test
    fun sort_is_pinyin_order() {
        val names = listOf("中南大学", "北京大学", "兰州大学", "复旦大学", "安徽大学")
        val sorted = names.sortedWith { a, b -> PinyinIndex.compare(a, b) }
        assertEquals(
            listOf("安徽大学", "北京大学", "复旦大学", "兰州大学", "中南大学"),
            sorted
        )
    }

    @Test
    fun sort_is_consistent_with_grouping() {
        val names = listOf("遵义医科大学", "郑州大学", "浙江大学", "中南大学")
        val sorted = names.sortedWith { a, b -> PinyinIndex.compare(a, b) }
        assertTrue(sorted.all { PinyinIndex.initialOf(it) == 'Z' })
        // zhe(浙) < zheng(郑) < zhong(中) < zun(遵)
        assertEquals(listOf("浙江大学", "郑州大学", "中南大学", "遵义医科大学"), sorted)
    }

    @Test
    fun polyphonic_prefix_is_corrected_by_word() {
        // 单字「重」读 zhòng，在 Z 区；但「重庆」读 Chóngqìng，要拆到 C 组。
        assertEquals('Z', PinyinIndex.initialOfChar('重'))
        assertEquals('C', PinyinIndex.initialOf("重庆大学"))
        assertEquals('C', PinyinIndex.initialOf("重庆医科大学"))
        // 「长」在 cháng / zhǎng 两种读音下 GB2312 都落在 C 区，无需修正
        assertEquals('C', PinyinIndex.initialOf("长春工业大学"))
        assertEquals('C', PinyinIndex.initialOf("长江大学文理学院"))
    }
}
