package com.university.timetable.data.ai

import android.content.Context
import com.university.timetable.data.AppDatabase
import com.university.timetable.data.entity.ChatMessage
import com.university.timetable.data.entity.ChatRole
import com.university.timetable.data.entity.ChatSession
import com.university.timetable.data.repository.ScheduleRepository
import com.university.timetable.util.SemesterWeeks
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * AI 会话仓库：会话、消息以及系统提示（含实时课表 / 待办 / 校园生活上下文）。
 */
class AiChatRepository(
    context: Context,
    private val schedule: ScheduleRepository = ScheduleRepository(context),
    db: AppDatabase = AppDatabase.get(context)
) {

    private val dao = db.aiChatDao()

    val sessions: Flow<List<ChatSession>> = dao.observeSessions()

    fun messages(sessionId: Long): Flow<List<ChatMessage>> = dao.observeMessages(sessionId)

    suspend fun createSession(title: String = "新对话"): Long =
        dao.insertSession(ChatSession(title = title))

    /** 取最近会话；没有则新建一个。 */
    suspend fun ensureSession(): Long =
        dao.latestSession()?.id ?: createSession()

    suspend fun renameSession(id: Long, title: String) =
        dao.renameSession(id, title, System.currentTimeMillis())

    suspend fun deleteSession(id: Long) {
        dao.clearMessages(id)
        dao.deleteSession(id)
    }

    suspend fun clearMessages(sessionId: Long) = dao.clearMessages(sessionId)

    suspend fun deleteMessage(id: Long) = dao.deleteMessage(id)

    suspend fun addMessage(message: ChatMessage): Long {
        val id = dao.insertMessage(message)
        dao.touchSession(message.sessionId, System.currentTimeMillis())
        return id
    }

    suspend fun updateMessage(message: ChatMessage) = dao.updateMessage(message)

    suspend fun getMessages(sessionId: Long): List<ChatMessage> = dao.getMessages(sessionId)

    suspend fun messageCount(sessionId: Long): Int = dao.countMessages(sessionId)

    /** 若会话还是「新对话」且已有用户消息，用首条消息自动命名。 */
    suspend fun autoTitle(sessionId: Long, firstUserText: String) {
        val session = dao.getSession(sessionId) ?: return
        if (session.title != "新对话") return
        val title = firstUserText.trim().replace("\n", " ").take(18)
        if (title.isNotBlank()) renameSession(sessionId, title)
    }

    /**
     * 构造系统提示：包含人格设定、动作协议、以及当前真实数据。
     *
     * 只有在这里把「事实」交给模型，模型才不会编造课程。
     */
    suspend fun buildSystemPrompt(
        today: LocalDate = LocalDate.now(),
        semesterStart: LocalDate,
        totalWeeks: Int,
        allowTools: Boolean
    ): String {
        val week = SemesterWeeks.weekOf(today, semesterStart).coerceIn(1, totalWeeks)
        val holiday = schedule.isHoliday(today)
        val ctx = AiExecContext(today = today, semesterStart = semesterStart, totalWeeks = totalWeeks)

        // 关键：这里注入的是**整学期**的排课规则 + 节假日 + 已有日期变更，
        // 而不是只把「本周」喂给模型。模型因此可以推算任意一天，也能看到放假当天的原始排课。
        val context = buildString {
            append(AiContextBuilder.semesterOverview(schedule, ctx))
            append("\n\n")
            append("【今天】\n")
            append(AiContextBuilder.todaySummary(schedule, today, week))
            append("\n")
            append("【本周】\n")
            append(AiContextBuilder.weekSummary(schedule, week, semesterStart, totalWeeks))
            append("\n")
            append(AiContextBuilder.todoSummary(schedule, today))
            append("\n")
            append(AiContextBuilder.campusSummary(schedule, today, holiday?.isHoliday == true))
            append("\n")
            append(AiContextBuilder.periodSummary(schedule.periodTimesOnce()))
        }

        return buildString {
            append("你是「娅娅课表」App 内置的助手，帮助学生管理课表、待办与校园生活信息。\n")
            append("今天是 $today（第 $week 周）")
            holiday?.let { append("，注意：${it.name}${if (it.isHoliday) "（放假）" else "（调休）"}") }
            append("。学期共 $totalWeeks 周。\n\n")

            append("回答要求：\n")
            append("1. 用简体中文，简洁清晰，适当使用列表；不确定的信息要说明不确定，不要编造。\n")
            append("2. 你可以回答**当前学期任意日期**的课表问题，不受「本周」或界面当前显示的周次限制。\n")
            append("3. 推算某一天课表的方法：先用「排课规则」按星期 + 周次 + 单双周 + 有效周次算出**基础课表**（这一天原本应该上的课），\n")
            append("   再叠加「节假日与调休」和「已有的日期变更」得到**实际课表**（最终真正要上的课）。\n")
            append("4. 放假导致某天没课，不等于那天没有排课：基础课表里原本要上的课仍然存在，可以用于调课。\n")
            append("5. 下面【今天】【本周】给的已经是**实际课表**；若它与基础课表不同，说明当天/本周存在日期变更或放假。\n")
            append("6. 学生问校园生活时间（供电、浴室、洗衣机等）时，依据下面的设施数据。\n\n")

            if (allowTools) {
                append("【可执行操作】\n")
                append("你不仅可以查询，还可以真实地新增 / 修改 / 删除课程、调课、补课、停课、换教室。\n")
                append("判断学生明确要求修改时，在回复末尾追加动作块（不要用其它格式，一次一个）：\n")
                append("```action\n{\"action\":\"...\", ...}\n```\n")
                append("动作清单（date 一律用 yyyy-MM-dd）：\n")
                append("- query_schedule: 查询某天的课表 {date, base(true 同时返回基础课表)}（只读，用于需要精确结果的场合）\n")
                append("- move_day: 把某天全部课程调到另一天 {from, to}\n")
                append("- move_course: 只把某天的某门课调到另一天 {from, to, name}\n")
                append("- cancel_day: 某天停课 {date, reason}\n")
                append("- add_one_off_course: 在某天临时加课 / 补课 {date, name, start_period, end_period, location, teacher}\n")
                append("- change_room: 只改某天的教室 {date, name, location}\n")
                append("- edit_course_on_date: 只改某天某节课的节次 / 教室 / 教师 {date, name, start_period, end_period, location, teacher}\n")
                append("- undo_day_changes: 撤销某天的全部调课 / 停课改动 {date}\n")
                append("- add_course: 新增**整学期重复**的课程 {name, teacher, location, day_of_week(1-7), start_period, end_period, start_week, end_week, parity(0每周/1单周/2双周)}\n")
                append("- update_course_rule: 修改**整学期**排课规则 {name, day_of_week, start_period, end_period, location, teacher, start_week, end_week, parity}\n")
                append("- clear_day: 清空某星期**整学期**的课程 {day_of_week}\n")
                append("- clear_all_courses: 清空全部课程 {}\n")
                append("- delete_course: 删除整门课程（含全部时段）{name}\n")
                append("- add_todo: 添加待办 {content, date}\n")
                append("- update_facility_hours: 修改设施时间 {name, windows(如 \"07:30-23:00\"，多段用;分隔), note}\n")
                append("- start_laundry_timer: 洗衣机倒计时 {minutes, label}\n")
                append("\n调课与「改课表」的区别（非常重要，别搞错）：\n")
                append("- 学生说「把 10 月 7 日的课调到 10 月 10 日」「这周三的课挪到周六」指的是**只影响这两天的调课**，\n")
                append("  必须用 move_day / move_course。**绝对不要**用 clear_day + add_course 去实现，那会改掉整学期的排课。\n")
                append("- 只有学生明确说「以后每周」「整学期都改成」时，才用 add_course / update_course_rule / clear_day。\n")
                append("- 只改某一天的一节课（时间、教室、老师）用 edit_course_on_date 或 change_room，不要动排课规则。\n")
                append("- 目标日期已有课不要覆盖，冲突时如实说明。\n")
                append("\n重要：动作块只是「提议」，App 会先让学生确认后才会真正执行。\n")
                append("**只要你打算修改任何数据，回复末尾就必须带一个 action 块。**\n")
                append("只在文字里说「我将为你…，请确认」而不输出 action 块 = 什么都不会发生：\n")
                append("学生只会看到一段话，既没有确认按钮，数据也不会变。这是最常见的失败方式，务必避免。\n")
                append("正确写法：先用一两句话说明「我将为你…」，紧接着在末尾给出 ```action 块。\n")
                append("删除类与整学期规则类操作（clear_*、delete_course、update_course_rule）要先提醒影响范围。\n\n")
            } else {
                append("【只读模式】你目前只能查看数据，不能提出修改。\n\n")
            }

            append("【当前数据】\n")
            append(context)
        }
    }

    /** 把历史消息转换成发给模型的格式（只取最近 N 条，避免超长）。 */
    fun toApiMessages(history: List<ChatMessage>, imageDataUrls: Map<Long, List<String>>): List<ApiMessage> =
        history.filter { it.role == ChatRole.USER || it.role == ChatRole.ASSISTANT }
            .takeLast(20)
            .map { msg ->
                ApiMessage(
                    role = msg.role,
                    text = msg.content,
                    imageDataUrls = imageDataUrls[msg.id].orEmpty()
                )
            }
}
