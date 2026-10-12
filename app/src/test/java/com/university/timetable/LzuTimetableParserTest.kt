package com.university.timetable

import com.university.timetable.data.entity.WeekParity
import com.university.timetable.importer.LzuTimetableParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 兰州大学教务课表解析测试。
 *
 * 样本 `lzu_timetable_sample.html` 是真实的
 * `jwk.lzu.edu.cn/academic/manager/coursearrange/showTimetable.do` 导出页面
 * （原始文件为 GBK，已转成 UTF-8）。
 */
class LzuTimetableParserTest {

    private val html: String by lazy {
        javaClass.classLoader!!
            .getResourceAsStream("lzu_timetable_sample.html")!!
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
    }

    @Test
    fun `recognises the lzu timetable page`() {
        assertTrue(LzuTimetableParser.looksLikeLzuTimetable(html))
    }

    @Test
    fun `parses all courses`() {
        val result = LzuTimetableParser.parse(html).result
        assertFalse("应当解析出课程", result.isEmpty)

        val names = result.courses.map { it.course.name }.toSet()
        // 抽样校验真实课表中的课程
        listOf(
            "高阶英语1",
            "解析几何",
            "数学分析（一）",
            "高等代数（一）",
            "计算机基础与C语言",
            "习近平新时代中国特色社会主义思想概论",
            "生物多样性与保护(通识)",
            "体育（1/4）",
            "计算机基础与C语言实习",
            "职业生涯发展与规划1",
            "大学生心理健康（网络共享课）",
            "Blockly 创意趣味编程（网络共享课）"
        ).forEach { expected ->
            assertTrue("缺少课程：$expected（实际：$names）", names.contains(expected))
        }
    }

    @Test
    fun `parses period times from the table header`() {
        val periods = LzuTimetableParser.parse(html).periodTimes
        assertTrue("应解析出节次时间", periods.isNotEmpty())

        val byPeriod = periods.associateBy { it.period }
        // 第1节 08:30-09:15
        assertEquals("08:30", byPeriod[1]?.startTime)
        assertEquals("09:15", byPeriod[1]?.endTime)
        // 第2节 09:25-10:10
        assertEquals("09:25", byPeriod[2]?.startTime)
        assertEquals("10:10", byPeriod[2]?.endTime)
        // 中午两节：12:20 / 13:25（在兰大作息里分别接在第 4 节之后）
        assertEquals("12:20", byPeriod[5]?.startTime)
        assertEquals("13:25", byPeriod[6]?.startTime)
        // 第7 行（第5节表头）14:30-15:15 按行序对应到第 7 节
        assertEquals("14:30", byPeriod[7]?.startTime)
        assertEquals("15:15", byPeriod[7]?.endTime)
        // 第11 行（第9节表头）19:00-19:45
        assertEquals("19:00", byPeriod[11]?.startTime)
        // 全表共 14 个节次
        assertEquals(14, byPeriod.size)
    }

    @Test
    fun `monday first period is english and spans merged periods`() {
        val result = LzuTimetableParser.parse(html).result
        // 周一第 1-2 节：高阶英语1 @天山堂A212 4-19 周全周
        val english = result.courses.first { it.course.name == "高阶英语1" }
        assertTrue(english.course.teacher!!.isNotBlank())

        val monday = english.sessions.first { it.dayOfWeek == 1 }
        assertEquals(1, monday.startPeriod)
        assertEquals(2, monday.endPeriod)
        assertEquals("天山堂A212", monday.location)
        assertEquals(4, monday.startWeek)
        assertEquals(19, monday.endWeek)
        assertEquals(WeekParity.EVERY, monday.weekParity)
    }

    @Test
    fun `friday geometry is at periods one and two`() {
        val result = LzuTimetableParser.parse(html).result
        // 周五（day=5）第 1-2 节：解析几何 @天山堂A408
        val geometry = result.courses.first { it.course.name == "解析几何" }
        val friday = geometry.sessions.first { it.dayOfWeek == 5 }
        assertEquals(1, friday.startPeriod)
        assertEquals(2, friday.endPeriod)
        assertEquals("天山堂A408", friday.location)
        assertEquals(4, friday.startWeek)
        assertEquals(19, friday.endWeek)
    }

    @Test
    fun `parses even week courses`() {
        val result = LzuTimetableParser.parse(html).result
        // 周三第 3-4 节：高阶英语1 4-18周双周
        val english = result.courses.first { it.course.name == "高阶英语1" }
        val wednesday = english.sessions.first { it.dayOfWeek == 3 }
        assertEquals(3, wednesday.startPeriod)
        assertEquals(4, wednesday.endPeriod)
        assertEquals(4, wednesday.startWeek)
        assertEquals(18, wednesday.endWeek)
        assertEquals(WeekParity.EVEN, wednesday.weekParity)
    }

    @Test
    fun `parses odd week courses`() {
        val result = LzuTimetableParser.parse(html).result
        // 周四第 3-4 节：大学生心理健康 6-15周单周
        val psych = result.courses.first { it.course.name == "大学生心理健康（网络共享课）" }
        val thursday = psych.sessions.first { it.dayOfWeek == 4 }
        assertEquals(WeekParity.ODD, thursday.weekParity)
        assertEquals(6, thursday.startWeek)
        assertEquals(15, thursday.endWeek)
        assertEquals("天山堂B503", thursday.location)
    }

    @Test
    fun `splits a cell containing two courses with different weeks`() {
        val result = LzuTimetableParser.parse(html).result
        // 周二（day=2）第 7-8 节同一格里有两门「计算机基础与C语言」：
        // 4-8 周 @秦岭堂A312，9-19 周 @秦岭堂B106
        val sessions = result.courses
            .filter { it.course.name == "计算机基础与C语言" }
            .flatMap { it.sessions }
            .filter { it.dayOfWeek == 2 }

        val early = sessions.firstOrNull { it.startWeek == 4 && it.endWeek == 8 }
        val late = sessions.firstOrNull { it.startWeek == 9 && it.endWeek == 19 }
        assertNotNull("应解析出 4-8 周的课", early)
        assertNotNull("应解析出 9-19 周的课", late)
        assertEquals("秦岭堂A312", early!!.location)
        assertEquals("秦岭堂B106", late!!.location)
        // 同格两门课的节次都应合并为连续区间
        assertEquals(7, early.startPeriod)
        assertEquals(8, early.endPeriod)
        assertEquals(7, late.startPeriod)
        assertEquals(8, late.endPeriod)
    }

    @Test
    fun `parses evening three-period course on monday`() {
        val result = LzuTimetableParser.parse(html).result
        // 周一第 11-13 节连上：生物多样性与保护(通识) @秦岭堂A304
        val bio = result.courses.first { it.course.name == "生物多样性与保护(通识)" }
        val monday = bio.sessions.first { it.dayOfWeek == 1 }
        assertEquals(11, monday.startPeriod)
        assertEquals(13, monday.endPeriod)
        assertEquals("秦岭堂A304", monday.location)
    }

    @Test
    fun `parses swimming class on tuesday evening`() {
        val result = LzuTimetableParser.parse(html).result
        val pe = result.courses.first { it.course.name == "体育（1/4）" }
        val tuesday = pe.sessions.first { it.dayOfWeek == 2 }
        assertEquals(11, tuesday.startPeriod)
        assertEquals(12, tuesday.endPeriod)
        assertEquals("游泳馆", tuesday.location)
    }

    @Test
    fun `extracts teacher names including multiple teachers`() {
        val result = LzuTimetableParser.parse(html).result
        val bio = result.courses.first { it.course.name == "生物多样性与保护(通识)" }
        assertEquals("任正炜,刘坤", bio.course.teacher)
    }

    @Test
    fun `all sessions are within valid ranges`() {
        val result = LzuTimetableParser.parse(html).result
        result.courses.flatMap { it.sessions }.forEach { s ->
            assertTrue("day ${s.dayOfWeek}", s.dayOfWeek in 1..7)
            assertTrue("start ${s.startPeriod}", s.startPeriod >= 1)
            assertTrue("end ${s.endPeriod}", s.endPeriod >= s.startPeriod)
            assertTrue("week ${s.startWeek}", s.startWeek >= 1)
            assertTrue("weekEnd", s.endWeek >= s.startWeek)
        }
    }
}
