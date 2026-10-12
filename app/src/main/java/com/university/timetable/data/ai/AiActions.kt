package com.university.timetable.data.ai

import android.util.Log
import com.university.timetable.data.entity.Course
import com.university.timetable.data.entity.CourseSession
import com.university.timetable.data.entity.DefaultPeriodTimes
import com.university.timetable.data.entity.TodoItem
import com.university.timetable.data.entity.WeekParity
import com.university.timetable.data.repository.ClassBlock
import com.university.timetable.data.repository.ScheduleRepository
import com.university.timetable.util.SemesterWeeks
import java.time.LocalDate

/**
 * AI 可执行的受控操作。
 *
 * 设计原则：**模型只提出动作，绝不直接落库**。
 * 模型输出一个 ```action``` JSON 块 → App 解析 → 弹确认框展示「将要修改什么」→
 * 用户点确认后才由 [AiActionExecutor] 执行 → 执行结果回传模型。
 */
sealed interface AiAction {
    /** 界面展示用的动作名 */
    val label: String

    /** 只读动作：不需要用户确认，执行结果直接回喂给模型继续回答。 */
    val readOnly: Boolean get() = false

    /** 高风险动作（会删除整学期规则），确认框需要额外强调。 */
    val destructive: Boolean get() = false

    /** 新增一门课程（含首次上课时段）。 */
    data class AddCourse(
        val name: String,
        val teacher: String?,
        val location: String?,
        val dayOfWeek: Int,
        val startPeriod: Int,
        val endPeriod: Int,
        val startWeek: Int,
        val endWeek: Int,
        val parity: Int
    ) : AiAction {
        override val label get() = "新增课程"
    }

    /** 删除某一天的全部课程。 */
    data class ClearDay(val dayOfWeek: Int) : AiAction {
        override val label get() = "清空一天的课程"
        override val destructive get() = true
    }

    /** 清空整个课表。 */
    data object ClearAllCourses : AiAction {
        override val label get() = "清空全部课程"
        override val destructive get() = true
    }

    /** 添加今日待办。 */
    data class AddTodo(
        val content: String,
        val date: String,
        val imageUris: List<String> = emptyList()
    ) : AiAction {
        override val label get() = "添加待办"
    }

    /** 修改校园生活设施的开放时间。 */
    data class UpdateFacilityHours(
        val facilityName: String,
        val windows: String,
        val note: String? = null
    ) : AiAction {
        override val label get() = "修改校园生活时间"
    }

    /** 启动洗衣机倒计时。 */
    data class StartLaundryTimer(val minutes: Int, val labelText: String?) : AiAction {
        override val label get() = "启动洗衣机倒计时"
    }

    // ------------------------------------------------------------------
    // 课表查询与「只影响某一天」的编辑
    //
    // 关键区分：
    // - 带 date 的动作只影响那一天（写日期变更记录），不动整学期排课规则；
    // - 只有 UpdateCourseRule / DeleteCourse 才改整学期规则，且必须用户明确要求。
    // ------------------------------------------------------------------

    /**
     * 查询某一天的课表（只读）。
     *
     * [base] = true 返回「基础课表」（放假当天依然能看到原本要上的课），
     * false 返回「实际课表」（叠加放假与调课后的最终结果）。
     */
    data class QuerySchedule(val date: String, val base: Boolean = true) : AiAction {
        override val label get() = "查询课表"
        override val readOnly get() = true
    }

    /** 把 [from] 当天的全部课程调到 [to]（只影响这两天）。 */
    data class MoveDayCourses(val from: String, val to: String) : AiAction {
        override val label get() = "调课（整天）"
    }

    /** 只把 [from] 某门课调到 [to]。 */
    data class MoveOneCourse(val from: String, val to: String, val courseName: String) : AiAction {
        override val label get() = "调课（单门）"
    }

    /** 停课：某一天不上课，但不删整学期规则。 */
    data class CancelDay(val date: String, val reason: String? = null) : AiAction {
        override val label get() = "停课一天"
    }

    /** 补课 / 临时加课：只在某一天加一节。 */
    data class AddOneOffCourse(
        val date: String,
        val name: String,
        val startPeriod: Int,
        val endPeriod: Int,
        val location: String? = null,
        val teacher: String? = null
    ) : AiAction {
        override val label get() = "补课 / 临时加课"
    }

    /** 换教室：只改某一天的教室。 */
    data class ChangeRoom(val date: String, val courseName: String, val location: String) : AiAction {
        override val label get() = "换教室（当天）"
    }

    /** 只修改某一天某节课的时间 / 地点，整学期规则不变。 */
    data class EditCourseOnDate(
        val date: String,
        val courseName: String,
        val startPeriod: Int? = null,
        val endPeriod: Int? = null,
        val location: String? = null,
        val teacher: String? = null
    ) : AiAction {
        override val label get() = "修改当天课程"
    }

    /** 撤销某一天的全部调课记录，恢复为基础课表。 */
    data class UndoDayChanges(val date: String) : AiAction {
        override val label get() = "撤销当天调课"
    }

    /** 修改整学期的排课规则（必须用户明确要求「以后每周都…」）。 */
    data class UpdateCourseRule(
        val name: String,
        val startPeriod: Int? = null,
        val endPeriod: Int? = null,
        val dayOfWeek: Int? = null,
        val location: String? = null,
        val teacher: String? = null,
        val startWeek: Int? = null,
        val endWeek: Int? = null,
        val parity: Int? = null
    ) : AiAction {
        override val label get() = "修改整学期课程规则"
        override val destructive get() = true
    }

    /** 删除整门课程（含它在整学期的所有上课时段）。 */
    data class DeleteCourse(val name: String) : AiAction {
        override val label get() = "删除整门课程"
        override val destructive get() = true
    }
}

/**
 * 解析模型回复中的动作块，并生成给用户确认的说明。
 *
 * 说明：这里**不使用 org.json**，而是自带一个极小的解析器。
 * 原因：(1) 动作 JSON 结构很浅（扁平键值 + 字符串数组），无需完整 JSON 库；
 * (2) org.json 在 JVM 单元测试中是空实现（not mocked），
 * 用自带解析器才能让这段**直接关系数据安全**的逻辑被真正测试到。
 */
object AiActionParser {

    private val actionBlockRegex = Regex(
        """```action\s*([\s\S]*?)```""",
        RegexOption.IGNORE_CASE
    )

    /** 从模型回复里抽出动作；同时返回去掉动作块后的展示文本。 */
    fun parse(reply: String): Pair<String, AiAction?> {
        val match = actionBlockRegex.find(reply) ?: return reply.trim() to null
        val json = match.groupValues[1].trim()
        val action = runCatching { toAction(FlatJson(json)) }.getOrNull()
        val text = reply.removeRange(match.range).trim()
        // 有动作块但解析不出来时必须留痕：否则表现是「AI 说要改，确认框却没出现」，
        // 光看界面完全无法定位是模型写错了键名还是解析器漏了分支。
        if (action == null) {
            Log.w(TAG, "动作块解析失败，已忽略。原始内容：$json")
        } else {
            Log.d(TAG, "已解析动作：${action::class.simpleName}")
        }
        return text to action
    }

    private const val TAG = "AiActionParser"

    private fun toAction(json: FlatJson): AiAction? = when (json.string("action")) {
        "add_course" -> {
            val name = json.stringAny("name", "course_name", "courseName", "title") ?: return null
            AiAction.AddCourse(
                name = name,
                teacher = json.string("teacher"),
                location = json.string("location"),
                dayOfWeek = (json.int("day_of_week") ?: 1).coerceIn(1, 7),
                startPeriod = (json.int("start_period") ?: 1).coerceAtLeast(1),
                endPeriod = (json.int("end_period") ?: json.int("start_period") ?: 1).coerceAtLeast(1),
                startWeek = (json.int("start_week") ?: 1).coerceAtLeast(1),
                endWeek = (json.int("end_week") ?: 20).coerceAtLeast(1),
                parity = (json.int("parity") ?: WeekParity.EVERY).coerceIn(0, 2)
            )
        }

        "clear_day" -> AiAction.ClearDay(
            (json.int("day_of_week") ?: 1).coerceIn(1, 7)
        )

        "clear_all_courses" -> AiAction.ClearAllCourses

        "add_todo" -> {
            val content = json.string("content") ?: return null
            AiAction.AddTodo(
                content = content,
                date = json.string("date") ?: LocalDate.now().toString(),
                imageUris = json.stringArray("images").take(TodoItem.MAX_IMAGES)
            )
        }

        "update_facility_hours" -> {
            val name = json.stringAny("name", "course_name", "courseName", "title") ?: return null
            val windows = json.string("windows") ?: return null
            AiAction.UpdateFacilityHours(name, windows, json.string("note"))
        }

        "start_laundry_timer" -> AiAction.StartLaundryTimer(
            minutes = (json.int("minutes") ?: 40).coerceIn(1, 240),
            labelText = json.string("label")
        )

        // ---------------- 课表查询与单日编辑 ----------------

        "query_schedule" -> {
            val date = json.string("date") ?: return null
            AiAction.QuerySchedule(date = date, base = json.bool("base") ?: true)
        }

        "move_day" -> {
            val from = json.string("from") ?: return null
            val to = json.string("to") ?: return null
            AiAction.MoveDayCourses(from = from, to = to)
        }

        "move_course" -> {
            val from = json.string("from") ?: return null
            val to = json.string("to") ?: return null
            val name = json.stringAny("name", "course_name", "courseName", "title") ?: return null
            AiAction.MoveOneCourse(from = from, to = to, courseName = name)
        }

        "cancel_day" -> {
            val date = json.string("date") ?: return null
            AiAction.CancelDay(date = date, reason = json.string("reason"))
        }

        "add_one_off_course" -> {
            val date = json.string("date") ?: return null
            val name = json.stringAny("name", "course_name", "courseName", "title") ?: return null
            val start = (json.int("start_period") ?: return null).coerceAtLeast(1)
            AiAction.AddOneOffCourse(
                date = date,
                name = name,
                startPeriod = start,
                endPeriod = (json.int("end_period") ?: start).coerceAtLeast(start),
                location = json.string("location"),
                teacher = json.string("teacher")
            )
        }

        "change_room" -> {
            val date = json.string("date") ?: return null
            val name = json.stringAny("name", "course_name", "courseName", "title") ?: return null
            val location = json.string("location") ?: return null
            AiAction.ChangeRoom(date = date, courseName = name, location = location)
        }

        "edit_course_on_date" -> {
            val date = json.string("date") ?: return null
            val name = json.stringAny("name", "course_name", "courseName", "title") ?: return null
            AiAction.EditCourseOnDate(
                date = date,
                courseName = name,
                startPeriod = json.int("start_period"),
                endPeriod = json.int("end_period"),
                location = json.string("location"),
                teacher = json.string("teacher")
            )
        }

        "undo_day_changes" -> {
            val date = json.string("date") ?: return null
            AiAction.UndoDayChanges(date)
        }

        "update_course_rule" -> {
            val name = json.stringAny("name", "course_name", "courseName", "title") ?: return null
            AiAction.UpdateCourseRule(
                name = name,
                startPeriod = json.int("start_period"),
                endPeriod = json.int("end_period"),
                dayOfWeek = json.int("day_of_week")?.coerceIn(1, 7),
                location = json.string("location"),
                teacher = json.string("teacher"),
                startWeek = json.int("start_week"),
                endWeek = json.int("end_week"),
                parity = json.int("parity")?.coerceIn(0, 2)
            )
        }

        "delete_course" -> {
            val name = json.stringAny("name", "course_name", "courseName", "title") ?: return null
            AiAction.DeleteCourse(name)
        }

        else -> null
    }

    /**
     * 极简 JSON 取值器：只支持扁平的 `"key": value`，value 可为
     * 字符串（含转义）／数字／true·false／字符串数组。足够解析动作块。
     */
    internal class FlatJson(private val raw: String) {

        /** 严格版：键必须存在。 */
        fun string(key: String): String? {
            val value = rawValue(key) ?: return null
            if (!value.startsWith("\"")) return null
            val inner = value.substring(1, value.length - 1)
            val unescaped = inner
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\\\", "\\")
            return unescaped.ifBlank { null }
        }

        /**
         * 宽松版：依次尝试多个键名。
         *
         * 实测模型会把 `name` 写成 `course_name` / `courseName` / `title`，
         * 严格按单一键名解析会**静默丢弃整条动作**，用户看到的是「AI 说它要改，但确认框没出现」。
         * 这里容忍常见同义键，避免这种「说而不做」的困惑。
         */
        fun stringAny(vararg keys: String): String? =
            keys.firstNotNullOfOrNull { string(it) }

        fun int(key: String): Int? {
            val value = rawValue(key) ?: return null
            return value.trim().toDoubleOrNull()?.toInt()
        }

        /** 布尔值；同时容忍模型写成 "true" / 1 这类形式。 */
        fun bool(key: String): Boolean? {
            val value = rawValue(key)?.trim()?.trim('"') ?: return null
            return when (value.lowercase()) {
                "true", "1", "yes" -> true
                "false", "0", "no" -> false
                else -> null
            }
        }

        fun stringArray(key: String): List<String> {
            val value = rawValue(key) ?: return emptyList()
            if (!value.startsWith("[")) return emptyList()
            val body = value.substring(1, value.length - 1)
            if (body.isBlank()) return emptyList()
            return Regex(""""((?:\\.|[^"\\])*)"""")
                .findAll(body)
                .map { it.groupValues[1].replace("\\\"", "\"").replace("\\\\", "\\") }
                .filter { it.isNotBlank() }
                .toList()
        }

        /** 取出 `"key":` 之后的原始片段（字符串、数字、数组等）。 */
        private fun rawValue(key: String): String? {
            val keyIndex = raw.indexOf("\"$key\"")
            if (keyIndex < 0) return null
            var i = keyIndex + key.length + 2
            // 跳过冒号与空白
            while (i < raw.length && (raw[i] == ':' || raw[i].isWhitespace())) i++
            if (i >= raw.length) return null

            return when (raw[i]) {
                '"' -> {
                    // 字符串：找到未被转义的结束引号
                    val sb = StringBuilder("\"")
                    i++
                    while (i < raw.length) {
                        val c = raw[i]
                        if (c == '\\' && i + 1 < raw.length) {
                            sb.append(c).append(raw[i + 1])
                            i += 2
                            continue
                        }
                        sb.append(c)
                        if (c == '"') break
                        i++
                    }
                    sb.toString()
                }

                '[' -> {
                    var depth = 0
                    val sb = StringBuilder()
                    while (i < raw.length) {
                        val c = raw[i]
                        if (c == '[') depth++
                        if (c == ']') {
                            depth--
                            sb.append(c)
                            if (depth == 0) break
                            i++
                            continue
                        }
                        sb.append(c)
                        i++
                    }
                    sb.toString()
                }

                else -> {
                    val start = i
                    while (i < raw.length && raw[i] != ',' && raw[i] != '}' && !raw[i].isWhitespace()) i++
                    raw.substring(start, i)
                }
            }
        }
    }

    /** 生成确认框里的「将要进行的修改」说明。 */
    fun describe(action: AiAction): List<String> = when (action) {
        is AiAction.AddCourse -> listOf(
            "课程：${action.name}",
            "时间：${SemesterWeeks.dayLabel(action.dayOfWeek)} 第 ${action.startPeriod}-${action.endPeriod} 节",
            "周次：${action.startWeek}-${action.endWeek} 周" +
                if (action.parity != WeekParity.EVERY) "（${WeekParity.label(action.parity)}）" else "",
            action.location?.let { "地点：$it" } ?: "地点：未填写",
            action.teacher?.let { "教师：$it" } ?: "教师：未填写"
        )

        is AiAction.ClearDay -> listOf(
            "将删除 ${SemesterWeeks.dayLabel(action.dayOfWeek)} 的**全部**课程时段",
            "该操作不可撤销"
        )

        AiAction.ClearAllCourses -> listOf(
            "将删除课表中的**全部课程**",
            "该操作不可撤销，建议先导出备份"
        )

        is AiAction.AddTodo -> listOf(
            "日期：${action.date}",
            "内容：${action.content}"
        ) + if (action.imageUris.isEmpty()) emptyList() else listOf("附带 ${action.imageUris.size} 张图片")

        is AiAction.UpdateFacilityHours -> listOf(
            "设施：${action.facilityName}",
            "新的开放时间：${action.windows}",
            action.note?.let { "备注：$it" } ?: "备注：不变"
        )

        is AiAction.StartLaundryTimer -> listOf(
            "倒计时：${action.minutes} 分钟",
            "名称：${action.labelText ?: "洗衣机"}"
        )

        is AiAction.QuerySchedule -> listOf(
            "查询日期：${action.date}",
            if (action.base) "内容：基础课表（放假当天仍然显示原本要上的课）" else "内容：实际课表"
        )

        is AiAction.MoveDayCourses -> listOf(
            "源日期：${action.from}",
            "目标日期：${action.to}",
            "将把源日期**原本应上的全部课程**移到目标日期",
            "只影响这两个日期，不会改动整学期的排课规则",
            "目标日期已有的课程会保留，时间冲突会提示但不会覆盖"
        )

        is AiAction.MoveOneCourse -> listOf(
            "课程：${action.courseName}",
            "从 ${action.from} 调到 ${action.to}",
            "只影响这两天的这一门课"
        )

        is AiAction.CancelDay -> listOf(
            "停课日期：${action.date}",
            action.reason?.let { "原因：$it" } ?: "原因：未填写",
            "整学期排课规则不受影响，之后可随时撤销"
        )

        is AiAction.AddOneOffCourse -> listOf(
            "日期：${action.date}",
            "课程：${action.name}",
            "节次：第 ${action.startPeriod}-${action.endPeriod} 节",
            action.location?.let { "地点：$it" } ?: "地点：未填写",
            "只在 ${action.date} 这一天加课，不影响其它周次"
        )

        is AiAction.ChangeRoom -> listOf(
            "日期：${action.date}",
            "课程：${action.courseName}",
            "新教室：${action.location}",
            "只改这一天的教室，其它周次不变"
        )

        is AiAction.EditCourseOnDate -> listOf(
            "日期：${action.date}",
            "课程：${action.courseName}",
            action.startPeriod?.let { "新节次：第 $it-${action.endPeriod ?: it} 节" } ?: "节次：不变",
            action.location?.let { "新教室：$it" } ?: "教室：不变",
            action.teacher?.let { "新教师：$it" } ?: "教师：不变",
            "只改这一天，整学期排课规则不变"
        )

        is AiAction.UndoDayChanges -> listOf(
            "撤销日期：${action.date}",
            "将删除这一天的调课 / 停课 / 补课 / 换教室记录，恢复为原本的排课规则"
        )

        is AiAction.UpdateCourseRule -> listOf(
            "课程：${action.name}",
            "**将修改整学期的排课规则**（不是只改某一天）",
            action.dayOfWeek?.let { "新星期：${SemesterWeeks.dayLabel(it)}" } ?: "星期：不变",
            action.startPeriod?.let { "新节次：第 $it-${action.endPeriod ?: it} 节" } ?: "节次：不变",
            action.location?.let { "新教室：$it" } ?: "教室：不变",
            action.teacher?.let { "新教师：$it" } ?: "教师：不变",
            if (action.startWeek != null || action.endWeek != null) {
                "新周次：${action.startWeek ?: 1}-${action.endWeek ?: 20} 周"
            } else "周次：不变"
        )

        is AiAction.DeleteCourse -> listOf(
            "课程：${action.name}",
            "**将删除这门课在整学期的全部上课时段**",
            "请先确认课表界面上确实不再需要这门课"
        )
    }
}

/**
 * 动作执行时需要的「学期事实」。
 *
 * 由 ViewModel 从用户偏好里读出后传进来，执行器本身不依赖 Context，
 * 因此这段直接关系数据安全的逻辑可以被 JVM 单元测试完整覆盖。
 */
data class AiExecContext(
    val today: LocalDate,
    val semesterStart: LocalDate,
    val totalWeeks: Int
)

/**
 * 一次可撤销操作的凭据。
 *
 * 执行动作时如果产生了可回退的改动，就一并返回；界面据此显示「撤销」按钮。
 */
class UndoToken(
    val label: String,
    private val block: suspend () -> String
) {
    suspend fun apply(): String = block()
}

/** 动作执行结果：给用户看的文字 + 可选撤销凭据。 */
data class AiExecutionResult(
    val message: String,
    val undo: UndoToken? = null
)

/**
 * 动作执行器：**只有用户确认后**才被调用（只读动作除外）。
 *
 * 所有写入都走 [ScheduleRepository] —— 与手动编辑课程、手动调休是**同一套接口与校验**，
 * 因此 AI 的改动会真实落库，并立刻反映在课表界面、桌面组件与提醒里。
 */
class AiActionExecutor(
    private val repo: ScheduleRepository
) {

    /** 执行动作并返回给模型/用户看的结果文本。 */
    suspend fun execute(action: AiAction, ctx: AiExecContext): AiExecutionResult = when (action) {
        is AiAction.AddCourse -> {
            val session = CourseSession(
                courseId = 0L,
                dayOfWeek = action.dayOfWeek,
                startPeriod = action.startPeriod,
                endPeriod = action.endPeriod,
                location = action.location,
                startWeek = action.startWeek,
                endWeek = action.endWeek,
                weekParity = action.parity
            )
            val id = repo.addCourseWithSessions(
                Course(
                    name = action.name,
                    teacher = action.teacher,
                    color = null,
                    category = "课程"
                ),
                listOf(session)
            )
            AiExecutionResult(
                "已新增课程「${action.name}」(id=$id)：${SemesterWeeks.dayLabel(action.dayOfWeek)} " +
                    "第 ${action.startPeriod}-${action.endPeriod} 节，${action.startWeek}-${action.endWeek} 周",
                undo = UndoToken("删除课程「${action.name}」") {
                    repo.deleteCourse(id)
                    "已撤销：删除课程「${action.name}」"
                }
            )
        }

        is AiAction.ClearDay -> {
            repo.clearDay(action.dayOfWeek)
            AiExecutionResult("已清空 ${SemesterWeeks.dayLabel(action.dayOfWeek)} 的全部课程")
        }

        AiAction.ClearAllCourses -> {
            repo.clearAll()
            AiExecutionResult("已清空全部课程")
        }

        is AiAction.AddTodo -> {
            val id = repo.upsertTodo(
                TodoItem(
                    date = action.date,
                    content = action.content,
                    imageUris = TodoItem.encodeImages(action.imageUris)
                )
            )
            AiExecutionResult(
                "已在 ${action.date} 添加待办「${action.content}」(id=$id)",
                undo = UndoToken("删除待办「${action.content}」") {
                    repo.deleteTodo(id)
                    "已撤销：删除待办「${action.content}」"
                }
            )
        }

        is AiAction.UpdateFacilityHours -> {
            val facility = repo.facilitiesOnce().firstOrNull {
                it.name.contains(action.facilityName) || action.facilityName.contains(it.name)
            } ?: return AiExecutionResult("未找到名为「${action.facilityName}」的设施，未做修改")
            val before = facility.windows
            repo.upsertFacility(
                facility.copy(
                    windows = action.windows,
                    note = action.note ?: facility.note
                )
            )
            AiExecutionResult(
                "已把「${facility.name}」的默认开放时间改为 ${action.windows}",
                undo = UndoToken("恢复「${facility.name}」的开放时间") {
                    repo.upsertFacility(facility.copy(windows = before))
                    "已撤销：恢复「${facility.name}」的开放时间为 $before"
                }
            )
        }

        is AiAction.StartLaundryTimer ->
            AiExecutionResult("已启动 ${action.minutes} 分钟倒计时（${action.labelText ?: "洗衣机"}）")

        // ---------------- 查询 ----------------

        is AiAction.QuerySchedule -> {
            val date = parseDate(action.date)
                ?: return AiExecutionResult("无法识别日期「${action.date}」，请使用 yyyy-MM-dd 格式")
            AiExecutionResult(AiContextBuilder.dayDetail(repo, date, ctx, base = action.base))
        }

        // ---------------- 只影响某一天的编辑 ----------------

        is AiAction.MoveDayCourses -> {
            val from = parseDate(action.from)
                ?: return AiExecutionResult("无法识别源日期「${action.from}」")
            val to = parseDate(action.to)
                ?: return AiExecutionResult("无法识别目标日期「${action.to}」")

            val result = repo.moveDay(from, to, ctx.semesterStart, ctx.totalWeeks)
            AiExecutionResult(
                result.message,
                undo = if (result.moved > 0) {
                    UndoToken("撤销 $from → $to 的调课") {
                        val n = repo.clearOverridesOn(from) + repo.clearOverridesOn(to)
                        "已撤销：删除 $n 条调课记录，$from 与 $to 恢复到原本的排课"
                    }
                } else null
            )
        }

        is AiAction.MoveOneCourse -> {
            val from = parseDate(action.from)
                ?: return AiExecutionResult("无法识别源日期「${action.from}」")
            val to = parseDate(action.to)
                ?: return AiExecutionResult("无法识别目标日期「${action.to}」")

            val week = weekOf(from, ctx)
            val snapshot = repo.daySnapshotOnce(from, week)
            val block = snapshot.baseBlocks.firstOrNull {
                it.name.contains(action.courseName) || action.courseName.contains(it.name)
            } ?: return AiExecutionResult(
                "$from 原本的课程里没有找到「${action.courseName}」，未做修改。" +
                    "当天的课程是：${
                        snapshot.baseBlocks.joinToString("、") { it.name }.ifBlank { "（无）" }
                    }"
            )

            val moved = repo.moveOneCourse(from, to, block, ctx.semesterStart, ctx.totalWeeks)
            AiExecutionResult(
                moved.message,
                undo = if (moved.moved > 0) {
                    UndoToken("撤销「${block.name}」的调课") {
                        repo.undoMoveOne(from, to, block.sessionId)
                    }
                } else null
            )
        }

        is AiAction.CancelDay -> {
            val date = parseDate(action.date)
                ?: return AiExecutionResult("无法识别日期「${action.date}」")
            val count = repo.cancelDay(date, ctx.semesterStart, action.reason)
            if (count == 0) {
                AiExecutionResult(
                    "$date 原本就没有课，或已经停过课，未做修改",
                    undo = null
                )
            } else {
                AiExecutionResult(
                    "已停课：$date 原本的 $count 门课不再显示（整学期排课规则未改动）",
                    undo = UndoToken("撤销 $date 的停课") {
                        repo.clearOverridesOn(date)
                        "已撤销：$date 恢复为原本的课表"
                    }
                )
            }
        }

        is AiAction.AddOneOffCourse -> {
            val date = parseDate(action.date)
                ?: return AiExecutionResult("无法识别日期「${action.date}」")
            val id = repo.addOneOff(
                date = date,
                name = action.name,
                startPeriod = action.startPeriod,
                endPeriod = action.endPeriod,
                location = action.location,
                teacher = action.teacher,
                note = "AI 添加的临时课程"
            )
            val conflict = repo.effectiveBlocksOnce(date, weekOf(date, ctx))
                .filter { it.sessionId != id && it.name != action.name }
                .filter { it.startPeriod <= action.endPeriod && action.startPeriod <= it.endPeriod }
            AiExecutionResult(
                buildString {
                    append("已在 $date 第 ${action.startPeriod}-${action.endPeriod} 节添加「${action.name}」")
                    if (conflict.isNotEmpty()) {
                        append("。注意：与 ${conflict.joinToString("、") { it.name }} 时间冲突")
                    }
                },
                undo = UndoToken("删除 $date 的「${action.name}」") {
                    repo.deleteOverride(id)
                    "已撤销：删除 $date 的「${action.name}」"
                }
            )
        }

        is AiAction.ChangeRoom -> {
            val date = parseDate(action.date)
                ?: return AiExecutionResult("无法识别日期「${action.date}」")
            val week = weekOf(date, ctx)
            val block = repo.daySnapshotOnce(date, week).baseBlocks.firstOrNull {
                it.name.contains(action.courseName) || action.courseName.contains(it.name)
            }
            repo.changeRoomOn(date, block?.name ?: action.courseName, action.location, block?.sessionId)
            AiExecutionResult(
                "已把 $date 的「${block?.name ?: action.courseName}」教室改为 ${action.location}（其它周次不变）",
                undo = UndoToken("撤销 $date 的换教室") {
                    repo.clearOverridesOn(date)
                    "已撤销：$date 恢复为原本的教室"
                }
            )
        }

        is AiAction.EditCourseOnDate -> {
            val date = parseDate(action.date)
                ?: return AiExecutionResult("无法识别日期「${action.date}」")
            val week = weekOf(date, ctx)
            val block = repo.daySnapshotOnce(date, week).baseBlocks.firstOrNull {
                it.name.contains(action.courseName) || action.courseName.contains(it.name)
            } ?: return AiExecutionResult("$date 原本没有「${action.courseName}」这门课，未做修改")

            val start = action.startPeriod ?: block.startPeriod
            val end = action.endPeriod ?: maxOf(start, block.endPeriod)
            val moved = repo.replaceCourseOnDate(
                date = date,
                block = block,
                startPeriod = start,
                endPeriod = end,
                location = action.location ?: block.location,
                teacher = action.teacher ?: block.teacher
            )
            AiExecutionResult(
                moved,
                undo = UndoToken("撤销 $date 的课程修改") {
                    repo.clearOverridesOn(date)
                    "已撤销：$date 恢复为原本的排课"
                }
            )
        }

        is AiAction.UndoDayChanges -> {
            val date = parseDate(action.date)
                ?: return AiExecutionResult("无法识别日期「${action.date}」")
            val n = repo.clearOverridesOn(date)
            AiExecutionResult(
                if (n == 0) "$date 没有可撤销的调课记录" else "已撤销：删除 $date 的 $n 条调课记录"
            )
        }

        // ---------------- 整学期规则（高风险，需明确指令） ----------------

        is AiAction.UpdateCourseRule -> {
            val outcome = repo.updateCourseRule(
                name = action.name,
                startPeriod = action.startPeriod,
                endPeriod = action.endPeriod,
                dayOfWeek = action.dayOfWeek,
                location = action.location,
                teacher = action.teacher,
                startWeek = action.startWeek,
                endWeek = action.endWeek,
                parity = action.parity
            )
            AiExecutionResult(outcome)
        }

        is AiAction.DeleteCourse -> {
            val course = repo.coursesOnce().firstOrNull {
                it.name.contains(action.name) || action.name.contains(it.name)
            } ?: return AiExecutionResult("没有找到名为「${action.name}」的课程，未做修改")

            val backup = repo.courseWithSessions(course.id)
            val sessionCount = backup?.second?.size ?: 0
            repo.deleteCourse(course.id)
            AiExecutionResult(
                "已删除课程「${course.name}」（含 $sessionCount 个上课时段）",
                undo = UndoToken("恢复课程「${course.name}」") {
                    if (backup == null) {
                        "无法恢复：备份已失效"
                    } else {
                        val newId = repo.addCourseWithSessions(
                            backup.first.copy(id = 0L),
                            backup.second.map { it.copy(id = 0L, courseId = 0L) }
                        )
                        "已撤销：恢复课程「${course.name}」(新 id=$newId)"
                    }
                }
            )
        }
    }

    private fun parseDate(raw: String): LocalDate? =
        runCatching { LocalDate.parse(raw.trim()) }.getOrNull()

    private fun weekOf(date: LocalDate, ctx: AiExecContext): Int =
        SemesterWeeks.weekOf(date, ctx.semesterStart).coerceIn(1, ctx.totalWeeks.coerceAtLeast(1))
}

// ---------------------------------------------------------------------------
// 只读上下文：让模型了解当前课表 / 待办 / 校园生活
// ---------------------------------------------------------------------------

object AiContextBuilder {

    /** 单条课程的一行文本。 */
    private fun StringBuilder.appendBlock(b: ClassBlock, indent: String = "") {
        append(indent)
        append("- ")
        if (b.startTime.isNotBlank()) append("${b.startTime}-${b.endTime} ") else append("第${b.startPeriod}-${b.endPeriod}节 ")
        append(b.name)
        b.location?.takeIf { it.isNotBlank() }?.let { append(" @$it") }
        b.teacher?.takeIf { it.isNotBlank() }?.let { append(" 教师:$it") }
        if (b.weekParity != WeekParity.EVERY) append(" (${WeekParity.label(b.weekParity)})")
        append("\n")
    }

    /**
     * 今日课表摘要。
     *
     * 取的是 [DaySnapshot.blocks]（**实际课表**），与今日页完全一致；
     * 如果当天基础课表有课而实际课表为空（放假 / 停课 / 整天调出），
     * 额外把基础课表也列出来 —— 否则模型会以为「今天什么都没排」，从而拒绝调课。
     */
    suspend fun todaySummary(repo: ScheduleRepository, today: LocalDate, week: Int): String {
        val snap = repo.daySnapshotOnce(today, week)
        val actual = snap.blocks
        val base = snap.baseBlocks
        return buildString {
            if (actual.isNotEmpty()) {
                append("今天（$today，第 $week 周）实际要上的课：\n")
                actual.forEach { appendBlock(it) }
            } else {
                append("今天（$today，第 $week 周）实际没有课。\n")
            }
            if (base.isNotEmpty() && actual.isEmpty()) {
                val why = when {
                    snap.holiday?.isMakeupDay == true -> "当天是调休补课日但被停课"
                    snap.isHoliday -> "当天放假（${snap.holiday?.name ?: "节假日"}）"
                    snap.hasOverrides -> "当天已停课或整天调出"
                    else -> "当天没有安排"
                }
                append("原因：$why。**但当天原本排了下面这些课**，仍可作为调课 / 补课的源课：\n")
                base.forEach { appendBlock(it, indent = "  ") }
            }
            if (snap.hasOverrides && actual.isNotEmpty()) {
                append("（当天有日期变更记录，以上已是叠加变更后的实际课表）\n")
            }
        }
    }

    /**
     * 整周课表摘要。
     *
     * **必须与周视图同源**：走 [ScheduleRepository.weekBlocksOnce]（即 `weekBlocksOf`），
     * 否则界面调完课之后，模型看到的「本周」还是旧的。
     */
    suspend fun weekSummary(
        repo: ScheduleRepository,
        week: Int,
        semesterStart: LocalDate,
        totalWeeks: Int
    ): String {
        val blocks = repo.weekBlocksOnce(week, semesterStart, totalWeeks)
        val sb = StringBuilder("第 $week 周课表（实际课表，与界面周视图一致）：\n")
        if (blocks.isEmpty()) {
            sb.append("（本周没有课程）\n")
        } else {
            for (day in 1..7) {
                val dayBlocks = blocks.filter { it.dayOfWeek == day }.sortedBy { it.startPeriod }
                if (dayBlocks.isEmpty()) continue
                val date = SemesterWeeks.dateOf(week, day, semesterStart)
                sb.append("${SemesterWeeks.dayLabel(day)}（$date）：\n")
                dayBlocks.forEach { sb.appendBlock(it, indent = "  ") }
            }
        }
        return sb.toString()
    }

    /** 待办摘要。 */
    suspend fun todoSummary(repo: ScheduleRepository, from: LocalDate): String {
        val items = repo.todosFrom(from)
        if (items.isEmpty()) return "从 $from 起没有待办事项。"
        return buildString {
            append("从 $from 起的待办：\n")
            items.take(30).forEach { t ->
                append("- ${t.date} ${if (t.done) "[已完成] " else ""}${t.content}")
                if (t.images.isNotEmpty()) append("（含 ${t.images.size} 张图片）")
                append("\n")
            }
        }
    }

    /** 校园生活摘要。 */
    suspend fun campusSummary(repo: ScheduleRepository, today: LocalDate, holiday: Boolean): String {
        val facilities = repo.facilitiesOnce().filter { it.enabled }
        if (facilities.isEmpty()) return "还没有配置校园生活设施。"
        return buildString {
            append("校园生活设施开放时间（今天是 $today${if (holiday) "，节假日" else ""}）：\n")
            facilities.forEach { f ->
                append("- ${f.name}")
                f.location?.takeIf { it.isNotBlank() }?.let { append("（$it）") }
                append("：")
                val windows = com.university.timetable.data.entity.TimeWindowCodec.decode(f.windows)
                append(windows.joinToString("、") { it.label() })
                f.note?.takeIf { it.isNotBlank() }?.let { append("  备注:$it") }
                append("\n")
            }
        }
    }

    /** 作息时间摘要。 */
    fun periodSummary(periods: List<com.university.timetable.data.entity.PeriodTime>): String {
        val list = periods.ifEmpty { DefaultPeriodTimes.ALL }
        return "作息：" + list.joinToString("，") { "第${it.period}节 ${it.startTime}-${it.endTime}" }
    }

    // ------------------------------------------------------------------
    // 任意日期的课表查询（AI 不再被限制在「本周」）
    // ------------------------------------------------------------------

    /**
     * 某一天的课表详情，**基础课表与实际课表都给**。
     *
     * 这是修复「AI 只能读取本周、调不了课」的核心：
     * 放假的 10-07 在实际课表里是空的，但基础课表里仍然列出原本要上的课，
     * 模型因此既知道「当天不用上课」，也知道「原本有哪些课可以用来调到别处」。
     */
    suspend fun dayDetail(
        repo: ScheduleRepository,
        date: LocalDate,
        ctx: AiExecContext,
        base: Boolean = true
    ): String {
        val week = SemesterWeeks.weekOf(date, ctx.semesterStart)
            .coerceIn(1, ctx.totalWeeks.coerceAtLeast(1))
        val snap = repo.daySnapshotOnce(date, week)
        val dayLabel = SemesterWeeks.dayLabel(date.dayOfWeek.value)

        val sb = StringBuilder()
        sb.append("$date $dayLabel 第 $week 周")
        snap.holiday?.let {
            sb.append(if (it.isHoliday) "（${it.name}·放假）" else "（${it.name}·调休补课）")
        }
        sb.append("\n")

        if (base) {
            sb.append("【基础课表】按排课规则，这一天原本应上：\n")
            if (snap.baseBlocks.isEmpty()) {
                sb.append("- （没有课）\n")
            } else {
                snap.baseBlocks.forEach { b ->
                    sb.append("- 第${b.startPeriod}-${b.endPeriod}节 ${b.name}")
                    b.location?.takeIf { it.isNotBlank() }?.let { sb.append(" @$it") }
                    b.teacher?.takeIf { it.isNotBlank() }?.let { sb.append(" 教师:$it") }
                    sb.append("\n")
                }
            }
        }

        sb.append("【实际课表】当天最终要上：\n")
        if (snap.blocks.isEmpty()) {
            val reason = when {
                snap.holiday?.isHoliday == true && !snap.hasOverrides -> "当天放假，不上课"
                snap.suppressedByOverride -> "当天课程已被调走或停课"
                snap.baseBlocks.isEmpty() -> "当天原本就没有课"
                else -> "当天没有安排"
            }
            sb.append("- （无课：$reason）\n")
        } else {
            snap.blocks.forEach { b ->
                sb.append("- 第${b.startPeriod}-${b.endPeriod}节 ${b.name}")
                b.location?.takeIf { it.isNotBlank() }?.let { sb.append(" @$it") }
                b.teacher?.takeIf { it.isNotBlank() }?.let { sb.append(" 教师:$it") }
                sb.append("\n")
            }
        }

        if (snap.overrides.isNotEmpty()) {
            sb.append("【当天的调课记录】\n")
            snap.overrides.forEach { o ->
                val kind = when (o.overrideType) {
                    com.university.timetable.data.entity.OverrideType.CANCEL -> "停课"
                    com.university.timetable.data.entity.OverrideType.MOVE_OUT -> "调出"
                    com.university.timetable.data.entity.OverrideType.MOVE_IN -> "调入"
                    com.university.timetable.data.entity.OverrideType.ADD -> "临时加课"
                    com.university.timetable.data.entity.OverrideType.ROOM -> "换教室"
                    null -> "变更"
                }
                sb.append("- $kind：${o.name ?: ""}${o.note?.let { "（$it）" } ?: ""}\n")
            }
        }
        return sb.toString().trimEnd()
    }

    /**
     * 整学期的课表概览，注入系统提示。
     *
     * 只列**排课规则**（每周重复的条目）而不是把 140 天全部展开，
     * 既让模型能推算任意日期，又不会把上下文撑爆。
     */
    suspend fun semesterOverview(repo: ScheduleRepository, ctx: AiExecContext): String {
        val sb = StringBuilder()
        sb.append("【学期】起始周一 ${SemesterWeeks.mondayOf(ctx.semesterStart)}，共 ${ctx.totalWeeks} 周，")
        sb.append("今天是 ${ctx.today}（第 ${SemesterWeeks.weekOf(ctx.today, ctx.semesterStart).coerceAtLeast(1)} 周）\n")

        val occurrences = repo.allOccurrencesOnce()
        sb.append("【排课规则】每周重复，按星期 / 周次 / 单双周生效：\n")
        if (occurrences.isEmpty()) {
            sb.append("- （课表为空）\n")
        } else {
            occurrences
                .sortedWith(compareBy({ it.dayOfWeek }, { it.startPeriod }))
                .forEach { o ->
                    sb.append("- ${o.name}：${SemesterWeeks.dayLabel(o.dayOfWeek)} ")
                    sb.append("第${o.startPeriod}-${o.endPeriod}节")
                    o.location?.takeIf { it.isNotBlank() }?.let { sb.append(" @$it") }
                    o.teacher?.takeIf { it.isNotBlank() }?.let { sb.append(" 教师:$it") }
                    sb.append(" 周次${o.startWeek}-${o.endWeek}")
                    if (o.weekParity != WeekParity.EVERY) {
                        sb.append("（${WeekParity.label(o.weekParity)}）")
                    }
                    sb.append("\n")
                }
        }

        val holidays = repo.holidaysOnce()
        sb.append("【节假日与调休】\n")
        if (holidays.isEmpty()) {
            sb.append("- （无）\n")
        } else {
            holidays.sortedBy { it.date }.forEach { h ->
                sb.append("- ${h.date} ${h.name}")
                when {
                    h.isMakeupDay -> sb.append("：调休补课，按${SemesterWeeks.dayLabel(h.makeupDayOfWeek ?: 1)}课表上课")
                    h.isHoliday -> sb.append("：放假，当天不上课（但基础课表里原本要上的课仍然可以调课）")
                    else -> sb.append("：正常上课")
                }
                sb.append("\n")
            }
        }

        val overrides = repo.overridesOnce()
        sb.append("【已有的日期变更（调课 / 停课 / 补课 / 换教室）】\n")
        if (overrides.isEmpty()) {
            sb.append("- （无）\n")
        } else {
            overrides.groupBy { it.date }.toSortedMap().forEach { (date, list) ->
                val desc = list.joinToString("；") { o ->
                    val kind = when (o.overrideType) {
                        com.university.timetable.data.entity.OverrideType.CANCEL -> "停课"
                        com.university.timetable.data.entity.OverrideType.MOVE_OUT -> "调出"
                        com.university.timetable.data.entity.OverrideType.MOVE_IN -> "调入"
                        com.university.timetable.data.entity.OverrideType.ADD -> "临时加课"
                        com.university.timetable.data.entity.OverrideType.ROOM -> "换教室"
                        null -> "变更"
                    }
                    "$kind ${o.name ?: ""}".trim()
                }
                sb.append("- $date：$desc\n")
            }
        }
        return sb.toString().trimEnd()
    }

    /** 把动作说明序列化为 JSON 供记录（便于历史回放）。手写拼接，避免依赖 org.json。 */
    fun actionToJson(action: AiAction): String {
        fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
        val detail = AiActionParser.describe(action).joinToString(",") { "\"${esc(it)}\"" }
        val body = when (action) {
            is AiAction.AddCourse -> buildString {
                append("\"action\":\"add_course\",")
                append("\"name\":\"${esc(action.name)}\",")
                append("\"teacher\":\"${esc(action.teacher.orEmpty())}\",")
                append("\"location\":\"${esc(action.location.orEmpty())}\",")
                append("\"day_of_week\":${action.dayOfWeek},")
                append("\"start_period\":${action.startPeriod},")
                append("\"end_period\":${action.endPeriod},")
                append("\"start_week\":${action.startWeek},")
                append("\"end_week\":${action.endWeek},")
                append("\"parity\":${action.parity}")
            }

            is AiAction.ClearDay ->
                "\"action\":\"clear_day\",\"day_of_week\":${action.dayOfWeek}"

            AiAction.ClearAllCourses -> "\"action\":\"clear_all_courses\""

            is AiAction.AddTodo -> buildString {
                append("\"action\":\"add_todo\",")
                append("\"content\":\"${esc(action.content)}\",")
                append("\"date\":\"${esc(action.date)}\"")
            }

            is AiAction.UpdateFacilityHours -> buildString {
                append("\"action\":\"update_facility_hours\",")
                append("\"name\":\"${esc(action.facilityName)}\",")
                append("\"windows\":\"${esc(action.windows)}\",")
                append("\"note\":\"${esc(action.note.orEmpty())}\"")
            }

            is AiAction.StartLaundryTimer -> buildString {
                append("\"action\":\"start_laundry_timer\",")
                append("\"minutes\":${action.minutes},")
                append("\"label\":\"${esc(action.labelText.orEmpty())}\"")
            }

            is AiAction.QuerySchedule -> buildString {
                append("\"action\":\"query_schedule\",")
                append("\"date\":\"${esc(action.date)}\",")
                append("\"base\":${action.base}")
            }

            is AiAction.MoveDayCourses ->
                "\"action\":\"move_day\",\"from\":\"${esc(action.from)}\",\"to\":\"${esc(action.to)}\""

            is AiAction.MoveOneCourse -> buildString {
                append("\"action\":\"move_course\",")
                append("\"from\":\"${esc(action.from)}\",")
                append("\"to\":\"${esc(action.to)}\",")
                append("\"name\":\"${esc(action.courseName)}\"")
            }

            is AiAction.CancelDay -> buildString {
                append("\"action\":\"cancel_day\",")
                append("\"date\":\"${esc(action.date)}\",")
                append("\"reason\":\"${esc(action.reason.orEmpty())}\"")
            }

            is AiAction.AddOneOffCourse -> buildString {
                append("\"action\":\"add_one_off_course\",")
                append("\"date\":\"${esc(action.date)}\",")
                append("\"name\":\"${esc(action.name)}\",")
                append("\"start_period\":${action.startPeriod},")
                append("\"end_period\":${action.endPeriod},")
                append("\"location\":\"${esc(action.location.orEmpty())}\",")
                append("\"teacher\":\"${esc(action.teacher.orEmpty())}\"")
            }

            is AiAction.ChangeRoom -> buildString {
                append("\"action\":\"change_room\",")
                append("\"date\":\"${esc(action.date)}\",")
                append("\"name\":\"${esc(action.courseName)}\",")
                append("\"location\":\"${esc(action.location)}\"")
            }

            is AiAction.EditCourseOnDate -> buildString {
                append("\"action\":\"edit_course_on_date\",")
                append("\"date\":\"${esc(action.date)}\",")
                append("\"name\":\"${esc(action.courseName)}\"")
            }

            is AiAction.UndoDayChanges ->
                "\"action\":\"undo_day_changes\",\"date\":\"${esc(action.date)}\""

            is AiAction.UpdateCourseRule -> buildString {
                append("\"action\":\"update_course_rule\",")
                append("\"name\":\"${esc(action.name)}\"")
            }

            is AiAction.DeleteCourse ->
                "\"action\":\"delete_course\",\"name\":\"${esc(action.name)}\""
        }
        return "{\"label\":\"${esc(action.label)}\",\"detail\":[$detail],$body}"
    }
}
