package com.university.timetable.util

import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap

/**
 * 按拼音给中文校名取首字母、排序——**不引入任何拼音库**。
 *
 * 做法是经典的 GB2312 一级汉字区位法：GB2312 的一级汉字（0xB0A1–0xD7F9，3755 个常用字）
 * 就是**按拼音排序**的，每个首字母对应一段连续编码。把首字编码成 GBK 两字节，
 * 查它落在哪一段，就得到首字母。
 *
 * 为什么不用 [java.text.Collator]：zh_CN 的 Collator 虽然大体是拼音序，但同一个音节里的
 * 不同汉字（如 `西` 与 `夕`，都是 xī）会按次级权重分出先后，用它做「≤ 锚点」的判定会把
 * `西安交通大学` 判成 W 组（v2.2.0 开发时实测踩到）。而**区间判定**不受这种同音字次序影响。
 *
 * 编码区之外的生僻字（GB2312 二级汉字、扩展区）无法确定拼音，统一落到 `#` 分组。
 */
object PinyinIndex {

    private val GBK: Charset = Charset.forName("GBK")

    /**
     * 首字母 → 该字母在 GB2312 一级汉字区里的**起始编码**（含）。
     *
     * 每个值都用「该字母下拼音最小的字」校验过：
     * 啊(a) 芭(ba) 擦(ca) 搭(da) 蛾(e) 发(fa) 噶(ga) 哈(ha) 击(ji) 喀(ka) 垃(la) 妈(ma)
     * 拿(na) 哦(o) 啪(pa) 期(qi) 然(ran) 撒(sa) 他(ta) 挖(wa) 昔(xi) 压(ya) 匝(za)。
     *
     * 汉语拼音没有以 `I` / `U` / `V` 开头的音节，所以这三组不存在。
     */
    private val RANGES: List<Pair<Char, Int>> = listOf(
        'A' to 0xB0A1,
        'B' to 0xB0C5,
        'C' to 0xB2C1,
        'D' to 0xB4EE,
        'E' to 0xB6EA,
        'F' to 0xB7A2,
        'G' to 0xB8C1,
        'H' to 0xB9FE,
        'J' to 0xBBF7,
        'K' to 0xBFA6,
        'L' to 0xC0AC,
        'M' to 0xC2E8,
        'N' to 0xC4C3,
        'O' to 0xC5B6,
        'P' to 0xC5BE,
        'Q' to 0xC6DA,
        'R' to 0xC8BB,
        'S' to 0xC8F6,
        'T' to 0xCBFB,
        'W' to 0xCDDA,
        'X' to 0xCEF4,
        'Y' to 0xD1B9,
        'Z' to 0xD4D1
    )

    /** 一级汉字区结束编码（`座`），之后的字不再按拼音排列。 */
    private const val HANZI_START = 0xB0A1
    private const val HANZI_END = 0xD7F9

    /**
     * 二级汉字（0xD8A1 以后，不按拼音排序）里出现在校名开头的那些字，手工补上首字母。
     *
     * 目前内置目录里只有这三个：亳州、濮阳、衢州。以后再收录带生僻首字的学校，
     * 把它们补进这张表即可，不必改算法。
     */
    private val OVERRIDES: Map<Char, Char> = mapOf(
        '亳' to 'B',
        '濮' to 'P',
        '衢' to 'Q'
    )

    /**
     * 词级修正：多音字只看单字会取到错读音。
     *
     * `重` 单字读 zhòng（GB2312 里排在 Z 区），但 `重庆` 读 Chóngqìng，必须拆到 C 组。
     * 内置目录里只有这一个词需要修正（`长春` `长江` 的 `长` 本来就在 C 区，
     * `华东` `宿州` `六盘水` 的单字读音也与词音一致）。
     */
    private val PREFIX_OVERRIDES: Map<String, Char> = mapOf(
        "重庆" to 'C'
    )

    /** 无法判定时的分组。 */
    const val FALLBACK = '#'

    /** 分组顺序，也就是字母索引的顺序。 */
    val letters: List<Char> = RANGES.map { it.first }

    private val codeCache = ConcurrentHashMap<Char, Int>()

    /** 单个汉字的 GBK 编码；非两字节字符返回 -1。 */
    private fun gbkCode(ch: Char): Int = codeCache.getOrPut(ch) {
        if (ch.code < 0x80) return@getOrPut ch.code
        val bytes = ch.toString().toByteArray(GBK)
        if (bytes.size != 2) return@getOrPut -1
        val hi = bytes[0].toInt() and 0xFF
        val lo = bytes[1].toInt() and 0xFF
        (hi shl 8) or lo
    }

    /** 取单个字的拼音首字母；无法判定返回 `#`。 */
    fun initialOfChar(ch: Char): Char {
        OVERRIDES[ch]?.let { return it }
        when {
            ch in 'A'..'Z' -> return ch
            ch in 'a'..'z' -> return ch.uppercaseChar()
            ch.code < 0x80 -> return FALLBACK
        }
        val code = gbkCode(ch)
        if (code < HANZI_START || code > HANZI_END) return FALLBACK
        var result = FALLBACK
        for ((letter, start) in RANGES) {
            if (code >= start) result = letter else break
        }
        return result
    }

    /** 取校名的首字母。 */
    fun initialOf(text: String): Char {
        if (text.isEmpty()) return FALLBACK
        for ((prefix, letter) in PREFIX_OVERRIDES) {
            if (text.startsWith(prefix)) return letter
        }
        return initialOfChar(text[0])
    }

    /** 逐字首字母拼起来，用于「输入拼音首字母也能搜到」。 */
    fun initialsOf(text: String): String {
        if (text.isEmpty()) return ""
        return buildString(text.length) {
            val head = initialOf(text)
            if (head != FALLBACK) append(head)
            for (i in 1 until text.length) {
                val c = initialOfChar(text[i])
                if (c != FALLBACK) append(c)
            }
        }
    }

    /**
     * 拼音序比较：先按 GBK 编码逐字节比，再按长度比。
     *
     * 一级汉字的 GBK 编码本身就是拼音序，所以这个比较等价于拼音排序，
     * 且与 [initialOfChar] 的区间判定**完全自洽**（同一个键既分组又排序）。
     */
    fun compare(a: String, b: String): Int {
        if (a == b) return 0
        val ba = a.toByteArray(GBK)
        val bb = b.toByteArray(GBK)
        val n = minOf(ba.size, bb.size)
        for (i in 0 until n) {
            val x = ba[i].toInt() and 0xFF
            val y = bb[i].toInt() and 0xFF
            if (x != y) return x - y
        }
        return ba.size - bb.size
    }
}
