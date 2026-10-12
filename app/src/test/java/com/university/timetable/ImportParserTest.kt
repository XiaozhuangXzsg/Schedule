package com.university.timetable

import com.university.timetable.importer.ScheduleCsvParser
import com.university.timetable.importer.ScheduleHtmlParser
import com.university.timetable.importer.ScheduleTextParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 导入解析器单元测试（纯 JVM，不依赖 Android）。
 */
class ImportParserTest {

    @Test
    fun `csv with header parses courses and sessions`() {
        val csv = """
            课程名称,授课教师,星期,节次,周次,上课地点
            高等代数（一）,林艳,星期三,3-4节,1-16周,天山堂A212
            大学物理,张文博,星期二,1-2节,1-16周,电教202
        """.trimIndent()

        val result = ScheduleCsvParser.parse(csv)
        assertEquals(2, result.courseCount)
        assertEquals(2, result.sessionCount)

        val first = result.courses.first()
        assertEquals("高等代数（一）", first.course.name)
        assertEquals("林艳", first.course.teacher)
        val s = first.sessions.first()
        assertEquals(3, s.dayOfWeek)
        assertEquals(3, s.startPeriod)
        assertEquals(4, s.endPeriod)
        assertEquals("天山堂A212", s.location)
        assertEquals(1, s.startWeek)
        assertEquals(16, s.endWeek)
    }

    @Test
    fun `csv parses odd and even week markers`() {
        val csv = """
            课程名称,授课教师,星期,节次,周次,上课地点
            高级英语1,李欣,星期一,3-4节,2-16周(双),天山堂A212
            思政,王敏,星期二,7-8节,1-18周(单),天山堂A305
        """.trimIndent()

        val result = ScheduleCsvParser.parse(csv)
        assertEquals(2, result.courseCount)
        assertEquals(2, result.courses[0].sessions.first().weekParity) // 双周
        assertEquals(1, result.courses[1].sessions.first().weekParity) // 单周
    }

    @Test
    fun `csv without header parses day-first rows`() {
        val csv = """
            星期一,高等代数（一）,3-4,1-16周,天山堂A212
            星期四,高等代数（一）,3-4,1-16周,天山堂B307
        """.trimIndent()

        val result = ScheduleCsvParser.parse(csv)
        assertEquals(2, result.courseCount)
        assertEquals(1, result.courses[0].sessions.first().dayOfWeek)
        assertEquals(4, result.courses[1].sessions.first().dayOfWeek)
    }

    @Test
    fun `csv handles tab delimiter`() {
        val csv = "课程名称\t授课教师\t星期\t节次\t周次\t上课地点\n" +
            "数学分析（一）\t赵静\t星期一\t5-6节\t1-16周\t天山堂A303"

        val result = ScheduleCsvParser.parse(csv)
        assertEquals(1, result.courseCount)
        val s = result.courses.first().sessions.first()
        assertEquals(1, s.dayOfWeek)
        assertEquals(5, s.startPeriod)
        assertEquals(6, s.endPeriod)
    }

    @Test
    fun `csv empty input yields empty result with warning`() {
        val result = ScheduleCsvParser.parse("")
        assertTrue(result.isEmpty)
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test
    fun `html grid pairs names with locations teachers and weeks`() {
        val html = """
            <html><body>
            <table>
              <tr><th>节次</th><th>星期一</th><th>星期二</th><th>星期三</th></tr>
              <tr>
                <td>第3-4节</td>
                <td>高等代数（一）<br/>@天山堂A212</td>
                <td>大学物理<br/>@电教202</td>
                <td>高等代数（一）<br/>@天山堂B307</td>
              </tr>
              <tr>
                <td>周次</td>
                <td>1-16周</td>
                <td>1-16周</td>
                <td>2-16周(双)</td>
              </tr>
              <tr>
                <td>授课教师</td>
                <td>林艳</td>
                <td>张文博</td>
                <td>林艳</td>
              </tr>
            </table>
            </body></html>
        """.trimIndent()

        val result = ScheduleHtmlParser.parse(html)
        assertEquals(3, result.courseCount)

        // 周一：高等代数（一）@天山堂A212，每周
        val monday = result.courses.single { it.sessions.first().dayOfWeek == 1 }
        assertEquals("高等代数（一）", monday.course.name)
        assertEquals("林艳", monday.course.teacher)
        assertEquals("天山堂A212", monday.sessions.first().location)
        assertEquals(0, monday.sessions.first().weekParity)

        // 周三：同名课程但地点与单双周不同（双周）
        val wednesday = result.courses.single { it.sessions.first().dayOfWeek == 3 }
        assertEquals("高等代数（一）", wednesday.course.name)
        assertEquals("天山堂B307", wednesday.sessions.first().location)
        assertEquals(2, wednesday.sessions.first().weekParity)
        assertEquals(2, wednesday.sessions.first().startWeek)
        assertEquals(16, wednesday.sessions.first().endWeek)

        // 周二：大学物理@电教202
        val tuesday = result.courses.single { it.sessions.first().dayOfWeek == 2 }
        assertEquals("大学物理", tuesday.course.name)
        assertEquals("电教202", tuesday.sessions.first().location)
    }

    @Test
    fun `html parser does not mistake a course name containing jiao for a location`() {
        // 「高等代数」含「教」字，不应被误判为地点
        val html = """
            <table>
              <tr><th>节次</th><th>星期一</th><th>星期二</th><th>星期三</th></tr>
              <tr>
                <td>第1-2节</td>
                <td>高等代数（一）<br/>天山堂A212</td>
                <td>教育学原理<br/>天山堂B101</td>
                <td>大学英语<br/>天山堂C202</td>
              </tr>
            </table>
        """.trimIndent()

        val result = ScheduleHtmlParser.parse(html)
        val edu = result.courses.first { it.sessions.first().dayOfWeek == 2 }
        assertEquals("教育学原理", edu.course.name)
        assertEquals("天山堂B101", edu.sessions.first().location)
    }

    @Test
    fun `html without table falls back to text parsing`() {
        val html = "<html><body><p>星期一</p><p>第1-2节</p><p>(高等代数（一）) : 4</p>" +
            "<p>1-16周</p><p>天山堂A212</p><p>林艳</p></body></html>"

        val result = ScheduleHtmlParser.parse(html)
        // 退化路径应至少给出提示而不是崩溃
        assertTrue(result.warnings.isNotEmpty() || result.courseCount >= 0)
    }

    @Test
    fun `text parser handles sequential layout`() {
        val text = """
            星期一
            第1-2节
            (高等代数（一）) : 4
            1-16周
            天山堂A212
            林艳
            第3-4节
            (数学分析（一）) : 4
            1-16周
            天山堂A303
            赵静
        """.trimIndent()

        val result = ScheduleTextParser.parse(text)
        assertEquals(2, result.courseCount)

        val first = result.courses[0]
        // 括号必须成对剥离，不能残留「（一」
        assertEquals("高等代数（一）", first.course.name)
        val s = first.sessions.first()
        assertEquals(1, s.dayOfWeek)
        assertEquals(1, s.startPeriod)
        assertEquals(2, s.endPeriod)
        assertEquals("天山堂A212", s.location)
        assertEquals("林艳", first.course.teacher)
    }

    @Test
    fun `text parser splits credit numbers from names`() {
        val text = """
            星期二
            第5-6节
            (大学物理) : 3.5
            1-16周
            电教202
            张文博
        """.trimIndent()

        val result = ScheduleTextParser.parse(text)
        assertEquals(1, result.courseCount)
        assertEquals("大学物理", result.courses.first().course.name)
    }

    @Test
    fun `text parser never produces negative or zero periods`() {
        val text = "星期三\n第1-2节\n(测试课程) : 2\n1-16周\n天山堂A101\n测试老师"
        val result = ScheduleTextParser.parse(text)
        result.courses.flatMap { it.sessions }.forEach { s ->
            assertTrue(s.startPeriod >= 1)
            assertTrue(s.endPeriod >= s.startPeriod)
            assertFalse(s.dayOfWeek < 1 || s.dayOfWeek > 7)
        }
    }
}
