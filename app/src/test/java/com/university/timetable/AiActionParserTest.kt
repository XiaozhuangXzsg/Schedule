package com.university.timetable

import com.university.timetable.data.ai.AiAction
import com.university.timetable.data.ai.AiActionParser
import com.university.timetable.data.entity.WeekParity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AI 动作解析测试。
 *
 * 这部分直接关系数据安全：模型只「提议」动作，App 必须先解析出来给用户确认，
 * 因此解析必须严格——格式不对就当作没有动作，不能猜。
 */
class AiActionParserTest {

    @Test
    fun `parses add_course action`() {
        val reply = """
            好的，我将在周四上午为你添加一门课。
            ```action
            {"action":"add_course","name":"线性代数","teacher":"张三","location":"天山堂A101",
             "day_of_week":4,"start_period":3,"end_period":4,"start_week":1,"end_week":16,"parity":0}
            ```
        """.trimIndent()

        val (text, action) = AiActionParser.parse(reply)
        assertNotNull(action)
        val a = action as AiAction.AddCourse
        assertEquals("线性代数", a.name)
        assertEquals("张三", a.teacher)
        assertEquals("天山堂A101", a.location)
        assertEquals(4, a.dayOfWeek)
        assertEquals(3, a.startPeriod)
        assertEquals(4, a.endPeriod)
        assertEquals(1, a.startWeek)
        assertEquals(16, a.endWeek)
        assertEquals(WeekParity.EVERY, a.parity)
        // 展示文本应保留模型说的话，且不再包含动作 JSON
        assertTrue(text.contains("周四"))
        assertTrue(!text.contains("```action"))
        assertTrue(!text.contains("add_course"))
    }

    @Test
    fun `parses odd week parity`() {
        val reply = """```action
            {"action":"add_course","name":"体育","day_of_week":2,"start_period":9,"end_period":10,"parity":1}
            ```"""
        val (_, action) = AiActionParser.parse(reply)
        assertEquals(WeekParity.ODD, (action as AiAction.AddCourse).parity)
    }

    @Test
    fun `parses add_todo with date`() {
        val reply = """```action
            {"action":"add_todo","content":"交实验报告","date":"2026-09-26"}
            ```"""
        val (_, action) = AiActionParser.parse(reply)
        val a = action as AiAction.AddTodo
        assertEquals("交实验报告", a.content)
        assertEquals("2026-09-26", a.date)
    }

    @Test
    fun `add_todo defaults to today when date missing`() {
        val reply = """```action
            {"action":"add_todo","content":"买牙膏"}
            ```"""
        val (_, action) = AiActionParser.parse(reply)
        val a = action as AiAction.AddTodo
        assertEquals(java.time.LocalDate.now().toString(), a.date)
    }

    @Test
    fun `parses clear_day`() {
        val reply = """```action
            {"action":"clear_day","day_of_week":5}
            ```"""
        val (_, action) = AiActionParser.parse(reply)
        assertEquals(5, (action as AiAction.ClearDay).dayOfWeek)
    }

    @Test
    fun `parses clear_all_courses`() {
        val reply = """```action
            {"action":"clear_all_courses"}
            ```"""
        val (_, action) = AiActionParser.parse(reply)
        assertTrue(action is AiAction.ClearAllCourses)
    }

    @Test
    fun `parses facility hours update`() {
        val reply = """```action
            {"action":"update_facility_hours","name":"浴室开放","windows":"11:00-14:00;16:30-23:00","note":"周二起"}
            ```"""
        val (_, action) = AiActionParser.parse(reply)
        val a = action as AiAction.UpdateFacilityHours
        assertEquals("浴室开放", a.facilityName)
        assertEquals("11:00-14:00;16:30-23:00", a.windows)
        assertEquals("周二起", a.note)
    }

    @Test
    fun `parses laundry timer and clamps minutes`() {
        val reply = """```action
            {"action":"start_laundry_timer","minutes":999,"label":"洗衣机"}
            ```"""
        val (_, action) = AiActionParser.parse(reply)
        val a = action as AiAction.StartLaundryTimer
        assertEquals(240, a.minutes) // 上限 240
        assertEquals("洗衣机", a.labelText)
    }

    @Test
    fun `no action block yields null action and unchanged text`() {
        val reply = "今天你有 4 节课。"
        val (text, action) = AiActionParser.parse(reply)
        assertNull(action)
        assertEquals(reply, text)
    }

    @Test
    fun `malformed json yields null action instead of crashing`() {
        val reply = """```action
            {"action":"add_course", this is not json}
            ```"""
        val (_, action) = AiActionParser.parse(reply)
        assertNull(action)
    }

    @Test
    fun `unknown action name yields null`() {
        val reply = """```action
            {"action":"delete_everything"}
            ```"""
        val (_, action) = AiActionParser.parse(reply)
        assertNull(action)
    }

    @Test
    fun `add_course without name is rejected`() {
        val reply = """```action
            {"action":"add_course","day_of_week":1,"start_period":1,"end_period":2}
            ```"""
        val (_, action) = AiActionParser.parse(reply)
        assertNull(action)
    }

    @Test
    fun `day and periods are clamped to valid ranges`() {
        val reply = """```action
            {"action":"add_course","name":"越界课程","day_of_week":99,"start_period":-5,"end_period":0}
            ```"""
        val (_, action) = AiActionParser.parse(reply)
        val a = action as AiAction.AddCourse
        assertEquals(7, a.dayOfWeek)
        assertEquals(1, a.startPeriod)
        assertEquals(1, a.endPeriod)
    }

    @Test
    fun `describe lists the changes for confirmation`() {
        val action = AiAction.AddCourse(
            name = "数学分析",
            teacher = "赵敦",
            location = "天山堂A303",
            dayOfWeek = 3,
            startPeriod = 1,
            endPeriod = 2,
            startWeek = 4,
            endWeek = 19,
            parity = WeekParity.EVERY
        )
        val details = AiActionParser.describe(action)
        assertTrue(details.any { it.contains("数学分析") })
        assertTrue(details.any { it.contains("周三") })
        assertTrue(details.any { it.contains("1-2") })
        assertTrue(details.any { it.contains("天山堂A303") })
    }

    @Test
    fun `describe marks destructive actions as irreversible`() {
        assertTrue(
            AiActionParser.describe(AiAction.ClearAllCourses)
                .any { it.contains("不可撤销") }
        )
        assertTrue(
            AiActionParser.describe(AiAction.ClearDay(1))
                .any { it.contains("不可撤销") }
        )
    }

    @Test
    fun `action serialisation round trips for history`() {
        val action = AiAction.AddTodo("写作业", "2026-09-26")
        val json = com.university.timetable.data.ai.AiContextBuilder.actionToJson(action)
        assertTrue(json.contains("add_todo"))
        assertTrue(json.contains("写作业"))
        assertTrue(json.contains("2026-09-26"))
    }

    @Test
    fun `multiple action blocks only first is used`() {
        val reply = """
            先做这个：
            ```action
            {"action":"add_todo","content":"第一件事"}
            ```
            再做这个：
            ```action
            {"action":"add_todo","content":"第二件事"}
            ```
        """.trimIndent()
        val (_, action) = AiActionParser.parse(reply)
        assertEquals("第一件事", (action as AiAction.AddTodo).content)
    }

    // ---------------- 课表查询与单日编辑 ----------------

    @Test
    fun `parses query_schedule and defaults to base schedule`() {
        val reply = """```action
            {"action":"query_schedule","date":"2026-10-07"}
            ```"""
        val (_, action) = AiActionParser.parse(reply)
        val a = action as AiAction.QuerySchedule
        assertEquals("2026-10-07", a.date)
        assertTrue("查询必须默认读基础课表（放假也看得到原本的课）", a.base)
        assertTrue("查询是只读动作，不应弹确认框", a.readOnly)
    }

    @Test
    fun `parses query_schedule with base disabled`() {
        val reply = """```action
            {"action":"query_schedule","date":"2026-10-10","base":false}
            ```"""
        val (_, action) = AiActionParser.parse(reply)
        assertTrue(!(action as AiAction.QuerySchedule).base)
    }

    @Test
    fun `parses move_day for moving a whole day`() {
        val reply = """```action
            {"action":"move_day","from":"2026-10-07","to":"2026-10-10"}
            ```"""
        val (_, action) = AiActionParser.parse(reply)
        val a = action as AiAction.MoveDayCourses
        assertEquals("2026-10-07", a.from)
        assertEquals("2026-10-10", a.to)
        assertTrue("整天调课只影响两天，不能标记成整学期破坏性操作", !a.destructive)
    }

    @Test
    fun `parses move_course for a single course`() {
        val reply = """```action
            {"action":"move_course","from":"2026-10-07","to":"2026-10-10","name":"数据结构"}
            ```"""
        val (_, action) = AiActionParser.parse(reply)
        val a = action as AiAction.MoveOneCourse
        assertEquals("数据结构", a.courseName)
    }

    @Test
    fun `move_course without a course name is rejected`() {
        val reply = """```action
            {"action":"move_course","from":"2026-10-07","to":"2026-10-10"}
            ```"""
        val (_, action) = AiActionParser.parse(reply)
        assertNull("目标课程不明确时不能猜，必须返回 null 让 AI 追问", action)
    }

    @Test
    fun `parses cancel_day with reason`() {
        val reply = """```action
            {"action":"cancel_day","date":"2026-10-07","reason":"校运会"}
            ```"""
        val (_, action) = AiActionParser.parse(reply)
        val a = action as AiAction.CancelDay
        assertEquals("2026-10-07", a.date)
        assertEquals("校运会", a.reason)
    }

    @Test
    fun `parses add_one_off_course and clamps periods`() {
        val reply = """```action
            {"action":"add_one_off_course","date":"2026-10-10","name":"补课：形势与政策",
             "start_period":5,"end_period":3,"location":"秦岭堂C301"}
            ```"""
        val (_, action) = AiActionParser.parse(reply)
        val a = action as AiAction.AddOneOffCourse
        assertEquals("2026-10-10", a.date)
        assertEquals(5, a.startPeriod)
        assertEquals("结束节次早于开始节次时应回落到开始节次", 5, a.endPeriod)
        assertEquals("秦岭堂C301", a.location)
    }

    @Test
    fun `add_one_off_course without start period is rejected`() {
        val reply = """```action
            {"action":"add_one_off_course","date":"2026-10-10","name":"补课"}
            ```"""
        assertNull(AiActionParser.parse(reply).second)
    }

    @Test
    fun `parses change_room on a single day`() {
        val reply = """```action
            {"action":"change_room","date":"2026-10-07","name":"数据结构","location":"天山堂B202"}
            ```"""
        val (_, action) = AiActionParser.parse(reply)
        val a = action as AiAction.ChangeRoom
        assertEquals("天山堂B202", a.location)
    }

    @Test
    fun `change_room without a location is rejected`() {
        val reply = """```action
            {"action":"change_room","date":"2026-10-07","name":"数据结构"}
            ```"""
        assertNull(AiActionParser.parse(reply).second)
    }

    /**
     * 真机实测踩到过：模型把课程名写成 `course_name`，严格的单键解析返回 null，
     * 表现是「AI 在对话里说它要改教室，但确认框没出现」——用户完全无从判断。
     * 这里锁住几种常见同义键名。
     */
    @Test
    fun `course name key tolerates common aliases`() {
        for (key in listOf("name", "course_name", "courseName", "title")) {
            val reply = """```action
                {"action":"change_room","date":"2026-10-09","$key":"解析几何","location":"测试楼B999"}
                ```"""
            val action = AiActionParser.parse(reply).second
            assertTrue("键名 $key 应当被接受", action is AiAction.ChangeRoom)
            assertEquals("解析几何", (action as AiAction.ChangeRoom).courseName)
        }
    }

    @Test
    fun `change_room 的别名键同样适用于单门调课`() {
        val reply = """```action
            {"action":"move_course","from":"2026-09-23","to":"2026-09-24","course_name":"数学分析（一）"}
            ```"""
        val action = AiActionParser.parse(reply).second
        assertTrue(action is AiAction.MoveOneCourse)
        assertEquals("数学分析（一）", (action as AiAction.MoveOneCourse).courseName)
    }

    @Test
    fun `parses edit_course_on_date with optional fields`() {
        val reply = """```action
            {"action":"edit_course_on_date","date":"2026-10-07","name":"数据结构",
             "start_period":3,"end_period":4,"location":"天山堂C303"}
            ```"""
        val (_, action) = AiActionParser.parse(reply)
        val a = action as AiAction.EditCourseOnDate
        assertEquals(3, a.startPeriod)
        assertEquals(4, a.endPeriod)
        assertEquals("天山堂C303", a.location)
    }

    @Test
    fun `parses undo_day_changes`() {
        val reply = """```action
            {"action":"undo_day_changes","date":"2026-10-07"}
            ```"""
        val (_, action) = AiActionParser.parse(reply)
        assertEquals("2026-10-07", (action as AiAction.UndoDayChanges).date)
    }

    @Test
    fun `update_course_rule is marked destructive and clamps the weekday`() {
        val reply = """```action
            {"action":"update_course_rule","name":"数据结构","day_of_week":9,
             "start_period":1,"end_period":2,"location":"天山堂A101"}
            ```"""
        val (_, action) = AiActionParser.parse(reply)
        val a = action as AiAction.UpdateCourseRule
        assertEquals("越界星期应被夹到合法范围", 7, a.dayOfWeek)
        assertTrue("改整学期规则必须被视为破坏性操作并二次确认", a.destructive)
    }

    @Test
    fun `delete_course is marked destructive`() {
        val reply = """```action
            {"action":"delete_course","name":"大学体育"}
            ```"""
        val (_, action) = AiActionParser.parse(reply)
        val a = action as AiAction.DeleteCourse
        assertEquals("大学体育", a.name)
        assertTrue(a.destructive)
    }

    @Test
    fun `describe explains a day move without claiming a whole semester change`() {
        val details = AiActionParser.describe(
            AiAction.MoveDayCourses(from = "2026-10-07", to = "2026-10-10")
        )
        assertTrue(details.any { it.contains("2026-10-07") })
        assertTrue(details.any { it.contains("2026-10-10") })
        assertTrue(
            "确认文案必须说明只影响这两天",
            details.any { it.contains("只影响") || it.contains("仅") }
        )
    }

    @Test
    fun `describe explains the scope of a cancel day`() {
        val details = AiActionParser.describe(AiAction.CancelDay(date = "2026-10-07", reason = "校运会"))
        assertTrue(details.any { it.contains("2026-10-07") })
        assertTrue(details.any { it.contains("校运会") })
    }

    @Test
    fun `new actions round trip through actionToJson`() {
        val moveJson = com.university.timetable.data.ai.AiContextBuilder
            .actionToJson(AiAction.MoveDayCourses("2026-10-07", "2026-10-10"))
        assertTrue(moveJson.contains("move_day"))
        assertTrue(moveJson.contains("2026-10-07"))
        assertTrue(moveJson.contains("2026-10-10"))

        val queryJson = com.university.timetable.data.ai.AiContextBuilder
            .actionToJson(AiAction.QuerySchedule("2026-09-23", base = true))
        assertTrue(queryJson.contains("query_schedule"))
        assertTrue(queryJson.contains("2026-09-23"))
    }

    @Test
    fun `describe of a query never warns about irreversible changes`() {
        val details = AiActionParser.describe(AiAction.QuerySchedule("2026-10-07"))
        assertTrue(details.none { it.contains("不可撤销") })
    }
}
