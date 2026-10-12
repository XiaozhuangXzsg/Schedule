package com.university.timetable.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.university.timetable.data.AppDatabase
import com.university.timetable.data.entity.CampusFacility
import com.university.timetable.data.entity.ClassOccurrence
import com.university.timetable.data.entity.Course
import com.university.timetable.data.entity.CourseSession
import com.university.timetable.data.entity.DormService
import com.university.timetable.data.entity.DefaultPeriodTimes
import com.university.timetable.data.entity.Holiday
import com.university.timetable.data.entity.PeriodTime
import com.university.timetable.data.entity.ScheduleOverride
import com.university.timetable.data.entity.TodoItem
import com.university.timetable.data.entity.OverrideType
import com.university.timetable.data.entity.WeekParity
import com.university.timetable.data.entity.endMinutes
import com.university.timetable.data.entity.startMinutes
import com.university.timetable.util.SemesterWeeks
import com.university.timetable.util.WeekPatternParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 一次真实发生的课（已叠加节次时间），供时间轴 / 周视图 / 通知使用。
 */
data class ClassBlock(
    val sessionId: Long,
    val courseId: Long,
    val name: String,
    val teacher: String?,
    val location: String?,
    val category: String,
    val color: Int?,
    /** 1=周一 ... 7=周日 */
    val dayOfWeek: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val startTime: String,
    val endTime: String,
    val startMinutes: Int,
    val endMinutes: Int,
    val weekParity: Int,
    /** 生效起始周 */
    val startWeek: Int,
    /** 生效结束周 */
    val endWeek: Int,
    /** 命中的日期（结合周次换算） */
    val date: LocalDate
) {
    val periodLabel: String
        get() = if (startPeriod == endPeriod) "$startPeriod" else "$startPeriod-$endPeriod"

    val timeLabel: String get() = "$startTime-$endTime"
}

/**
 * 调课预览：执行前告诉用户「将要移动哪些课、目标日期会变成什么样」。
 *
 * 这是「先展示影响范围再执行」的数据来源，对话框与 AI 确认框共用。
 */
data class MovePlan(
    val from: LocalDate,
    val to: LocalDate,
    val fromWeek: Int,
    val toWeek: Int,
    /** 源日期**基础课表**里要移动的课（按源日期自己的周次 / 单双周筛出）。 */
    val courses: List<ClassBlock>,
    /** 目标日期执行前已有的实际课表（会被保留，不会被覆盖）。 */
    val targetExisting: List<ClassBlock>,
    /** 与目标日期已有课程时间重叠的课（只提示，不阻止）。 */
    val conflicts: List<ClassBlock>,
    /** 这批调课此前是否已经执行过（用于提示「重复执行不会重复添加」）。 */
    val alreadyApplied: Boolean
) {
    val hasConflicts: Boolean get() = conflicts.isNotEmpty()
}

/** 调课执行结果。 */
data class MoveResult(
    val moved: Int,
    val conflicts: List<ClassBlock>,
    val movedNames: List<String>,
    val message: String
)

/**
 * 课表仓库：统一封装 Room 访问、节假日判定与周次换算。
 */
class ScheduleRepository(
    context: Context,
    private val db: AppDatabase = AppDatabase.get(context)
) {

    private val courseDao = db.courseDao()
    private val periodDao = db.periodTimeDao()
    private val holidayDao = db.holidayDao()
    private val dormDao = db.dormServiceDao()
    private val facilityDao = db.campusFacilityDao()
    private val todoDao = db.todoDao()
    private val overrideDao = db.scheduleOverrideDao()

    // ---------------- Flow 数据源 ----------------

    val periodTimes: Flow<List<PeriodTime>> = periodDao.observeAll()

    val holidays: Flow<List<Holiday>> = holidayDao.observeAll()

    val dormServices: Flow<List<DormService>> = dormDao.observeAll()

    /** 校园生活设施（含时间规则）。 */
    val campusFacilities: Flow<List<CampusFacility>> = facilityDao.observeAll()

    /** 全部待办事项。 */
    val allTodos: Flow<List<TodoItem>> = todoDao.observeAll()

    /** 指定日期的待办事项。 */
    fun todosOn(date: LocalDate): Flow<List<TodoItem>> = todoDao.observeByDate(date.toString())

    val courses: Flow<List<Course>> = courseDao.observeCourses()

    /** 带时间的上课块（不含周次过滤）。 */
    val allBlocks: Flow<List<ClassBlock>> =
        combine(courseDao.observeOccurrences(), periodDao.observeAll()) { occ, periods ->
            buildBlocks(occ, periods, LocalDate.now())
        }

    /**
     * 周视图 / 今日页共用的排课原料：课程规则、作息、节假日、日期变更记录四张表的当前快照。
     *
     * 全部由数据库 Flow 组合而来，**不涉及挂起调用**，
     * 因此可以在 ViewModel 的 `combine` 里安全地同步算出「上一周 / 下一周」，
     * 供左右滑动切周时预渲染，避免翻页瞬间出现空白。
     *
     * 注意：这里刻意**不**预先按「星期几」切片。放假、调休补课、调课都会改变
     * 「某一天实际上什么课」，所以周视图必须按**日期**逐天走 [DayPlanner]，
     * 才能和今日页 / 桌面卡片 / AI 得到完全一致的结果。
     */
    val plannerInputs: Flow<PlannerInputs> = combine(
        courseDao.observeOccurrences(),
        periodDao.observeAll(),
        holidayDao.observeAll(),
        overrideDao.observeAll()
    ) { occurrences, periods, holidays, overrides ->
        PlannerInputs(
            occurrences = occurrences,
            periods = periods,
            holidays = holidays,
            overrides = overrides
        )
    }

    /** 全部节假日 / 调休安排（用于同步判断某天是否上课）。 */
    val allHolidays: Flow<List<Holiday>> = holidayDao.observeAll()

    /**
     * 某一天的完整快照，**纯 Flow**。
     *
     * 课程表、作息时间、节假日、日期变更记录任意一张表发生变化，这个流都会重新发射，
     * 因此今日页 / 校园生活页只要观察它就能自动跟随数据库更新，
     * 不再需要「写完之后手动戳一下让它重算」。
     */
    fun daySnapshot(date: LocalDate, week: Int): Flow<DaySnapshot> = combine(
        courseDao.observeOccurrences(),
        periodDao.observeAll(),
        holidayDao.observeAll(),
        overrideDao.observeAll()
    ) { occurrences, periods, holidays, overrides ->
        snapshotOf(date, week, occurrences, periods, holidays, overrides)
    }

    /**
     * 纯函数版本的快照计算，便于单元测试与一次性查询复用。
     *
     * 计算分两步，也就是「基础课表 → 实际课表」：
     *
     * 1. **基础课表 [DaySnapshot.baseBlocks]**：按调休补课改写的星期（放假**不**参与），
     *    叠加单双周 / 起止周规则。放假日照样返回原本要上的课，
     *    这样 AI 才能查到「10 月 7 日原本应该上的课」并据此调课。
     * 2. **实际课表 [DaySnapshot.blocks]**：在「该日是否真的上课」判定之后，
     *    依次应用停课（CANCEL）、调出（MOVE_OUT）、调入（MOVE_IN）、临时加课（ADD）、换教室（ROOM）。
     */
    internal fun snapshotOf(
        date: LocalDate,
        week: Int,
        occurrences: List<ClassOccurrence>,
        periods: List<PeriodTime>,
        holidays: List<Holiday>,
        overrides: List<ScheduleOverride> = emptyList()
    ): DaySnapshot = DayPlanner.snapshotOf(date, week, occurrences, periods, holidays, overrides)

    /** 一次性读取的当天快照（供后台任务 / 小组件使用，页面请用 [daySnapshot]）。 */
    suspend fun daySnapshotOnce(date: LocalDate, week: Int): DaySnapshot = snapshotOf(
        date = date,
        week = week,
        occurrences = courseDao.getOccurrences(),
        periods = periodDao.getAll(),
        holidays = holidayDao.getAll(),
        overrides = overrideDao.getAll()
    )

    /**
     * 一次性读取整周的实际课表，**与周视图 [weekBlocksOf] 走同一条链路**。
     *
     * 之所以要有这个方法，是因为「本周课表」以前在 AI 侧还有第二条实现
     * （逐日 `classesOn`），那条路径既不读节假日也不读日期变更，
     * 会造成「界面已经调课了、AI 说的本周还是旧的」。现在两处共用本方法。
     */
    suspend fun weekBlocksOnce(week: Int, semesterStart: LocalDate, totalWeeks: Int): List<ClassBlock> =
        weekBlocksOf(
            week = week,
            semesterStart = semesterStart,
            totalWeeks = totalWeeks,
            inputs = PlannerInputs(
                occurrences = courseDao.getOccurrences(),
                periods = periodDao.getAll(),
                holidays = holidayDao.getAll(),
                overrides = overrideDao.getAll()
            )
        )

    // ---------------- 查询 ----------------

    suspend fun periodTimesOnce(): List<PeriodTime> {
        val existing = periodDao.getAll()
        return existing
    }

    suspend fun coursesOnce(): List<Course> = courseDao.getCourses()

    /** 全部「课程 + 时段」条目（含星期、节次、周次规则），供课表概览使用。 */
    suspend fun allOccurrencesOnce(): List<ClassOccurrence> = courseDao.getOccurrences()

    /** 全部节假日 / 调休安排。 */
    suspend fun holidaysOnce(): List<Holiday> = holidayDao.getAll()

    /** 全部日期变更记录（调课 / 停课 / 补课 / 换教室）。 */
    suspend fun overridesOnce(): List<ScheduleOverride> = overrideDao.getAll()

    suspend fun overridesOn(date: LocalDate): List<ScheduleOverride> = overrideDao.getByDate(date.toString())

    suspend fun sessionsOfCourse(courseId: Long): List<CourseSession> =
        courseDao.getSessionsOfCourse(courseId)

    suspend fun courseWithSessions(courseId: Long): Pair<Course, List<CourseSession>>? {
        val c = courseDao.getCourse(courseId) ?: return null
        return c to courseDao.getSessionsOfCourse(courseId)
    }

    /** 取某一天实际要上的课（已考虑单双周与节假日调休）。 */
    suspend fun classesOn(
        date: LocalDate,
        week: Int,
        periods: List<PeriodTime>? = null
    ): List<ClassBlock> {
        val effectiveDay = effectiveDayOfWeek(date) ?: return emptyList()
        val times = periods ?: periodDao.getAll()
        val sessions = courseDao.getSessionsOfDay(effectiveDay)
        val courseMap = courseDao.getCourses().associateBy { it.id }
        val occurrences = sessions.mapNotNull { s ->
            val c = courseMap[s.courseId] ?: return@mapNotNull null
            ClassOccurrence(
                sessionId = s.id,
                courseId = c.id,
                name = c.name,
                teacher = c.teacher,
                location = s.location,
                category = c.category,
                color = c.color,
                dayOfWeek = s.dayOfWeek,
                startPeriod = s.startPeriod,
                endPeriod = s.endPeriod,
                startMinutes = 0,
                endMinutes = 0,
                weekParity = s.weekParity,
                startWeek = s.startWeek,
                endWeek = s.endWeek
            )
        }
        return buildBlocks(occurrences, times, date)
            .filter { WeekPatternParser.matches(week, it.startWeek, it.endWeek, it.weekParity) }
            .sortedBy { it.startMinutes }
    }

    /**
     * 今天是否有课（含放假判断）。
     */
    suspend fun isHoliday(date: LocalDate): Holiday? = holidayDao.getByDate(date.toString())

    /**
     * 返回该日期实际生效的星期（处理调休补课），放假则返回 null。
     */
    suspend fun effectiveDayOfWeek(date: LocalDate): Int? =
        DayResolver.effectiveDayOfWeek(date, holidayDao.getByDate(date.toString()))

    // ---------------- 基础课表 / 实际课表 ----------------
    //
    // 「基础课表」= 只按排课规则算，放假不影响（AI 查询与调课以它为准）。
    // 「实际课表」= 基础课表叠加放假、停课、调课、补课、换教室后的最终结果。

    /**
     * 某一天的基础课表：**不受放假影响**。
     *
     * 这是「这一天原本应该上什么课」，也是调课的源数据。
     * 国庆放假的 10-07（周三）在这里依然会返回周三的课。
     */
    suspend fun baseBlocksOnce(date: LocalDate, week: Int): List<ClassBlock> =
        daySnapshotOnce(date, week).baseBlocks

    /** 某一天的实际课表：基础课表叠加放假与全部日期变更记录。 */
    suspend fun effectiveBlocksOnce(date: LocalDate, week: Int): List<ClassBlock> =
        daySnapshotOnce(date, week).blocks

    /**
     * 调课预览：把 [from] 当天的**基础课表**列出来，并标出会落到 [to] 的哪些时段、是否冲突。
     *
     * 供「快捷调休」对话框与 AI 动作确认框在执行前展示影响范围。
     */
    suspend fun planMoveDay(
        from: LocalDate,
        to: LocalDate,
        semesterStart: LocalDate,
        totalWeeks: Int
    ): MovePlan {
        val fromWeek = SemesterWeeks.weekOf(from, semesterStart).coerceIn(1, totalWeeks.coerceAtLeast(1))
        val toWeek = SemesterWeeks.weekOf(to, semesterStart).coerceIn(1, totalWeeks.coerceAtLeast(1))

        val source = baseBlocksOnce(from, fromWeek)
        val targetExisting = effectiveBlocksOnce(to, toWeek)

        val conflicts = source.filter { s ->
            targetExisting.any { t -> s.startPeriod <= t.endPeriod && t.startPeriod <= s.endPeriod }
        }
        val alreadyDone = source.isNotEmpty() && source.all { s ->
            overrideDao.find(to.toString(), OverrideType.MOVE_IN.name, s.sessionId) != null
        }

        return MovePlan(
            from = from,
            to = to,
            fromWeek = fromWeek,
            toWeek = toWeek,
            courses = source,
            targetExisting = targetExisting,
            conflicts = conflicts,
            alreadyApplied = alreadyDone
        )
    }

    /**
     * 把 [from] 当天**基础课表**里的全部课程移到 [to]。
     *
     * 规则：
     * - 只写日期变更记录，**不碰 `course_sessions` 里的整学期排课规则**；
     * - 源日期按**它自己**的周次 / 单双周 / 有效周次筛选，目标日期的周次规则不参与筛选，
     *   因此跨周调课不会漏课；
     * - 幂等：同一天同来源时段的记录用 [ScheduleOverrideDao.find] 判重，重复执行不会产生重复课程；
     * - 目标日期已有课程**保留**，只做时间冲突检测并如实回报。
     */
    suspend fun moveDay(from: LocalDate, to: LocalDate, semesterStart: LocalDate, totalWeeks: Int): MoveResult {
        if (from == to) return MoveResult(0, emptyList(), emptyList(), "原日期与目标日期相同，未做修改")

        val plan = planMoveDay(from, to, semesterStart, totalWeeks)
        if (plan.courses.isEmpty()) {
            return MoveResult(
                moved = 0,
                conflicts = emptyList(),
                movedNames = emptyList(),
                message = "${from} 原本就没有课，未做修改"
            )
        }

        val stamp = System.currentTimeMillis()
        val added = mutableListOf<ScheduleOverride>()
        var skipped = 0

        db.withTransaction {
            plan.courses.forEach { block ->
                val outKey = overrideDao.find(from.toString(), OverrideType.MOVE_OUT.name, block.sessionId)
                if (outKey == null) {
                    overrideDao.upsert(
                        ScheduleOverride(
                            date = from.toString(),
                            type = OverrideType.MOVE_OUT.name,
                            sourceSessionId = block.sessionId,
                            courseId = block.courseId,
                            name = block.name,
                            teacher = block.teacher,
                            location = block.location,
                            color = block.color,
                            startPeriod = block.startPeriod,
                            endPeriod = block.endPeriod,
                            note = "已调至 $to",
                            createdAt = stamp
                        )
                    )
                } else {
                    skipped++
                }

                val inKey = overrideDao.find(to.toString(), OverrideType.MOVE_IN.name, block.sessionId)
                if (inKey == null) {
                    added += ScheduleOverride(
                        date = to.toString(),
                        type = OverrideType.MOVE_IN.name,
                        sourceSessionId = block.sessionId,
                        courseId = block.courseId,
                        name = block.name,
                        teacher = block.teacher,
                        location = block.location,
                        color = block.color,
                        startPeriod = block.startPeriod,
                        endPeriod = block.endPeriod,
                        note = "由 $from 调入",
                        createdAt = stamp
                    )
                }
            }
            if (added.isNotEmpty()) overrideDao.upsertAll(added)
        }

        DataChangeBus.publish(DataKind.OVERRIDE, null, "调课 $from → $to")

        val names = plan.courses.map { it.name }
        val message = buildString {
            if (added.isEmpty()) {
                append("$from → $to 的调课此前已经执行过，本次未新增课程")
            } else {
                append("已把 $from 的 ${added.size} 门课调到 $to：${names.joinToString("、")}")
            }
            if (plan.conflicts.isNotEmpty()) {
                append("。注意：${plan.conflicts.joinToString("、") { it.name }} 在 $to 与已有课程时间重叠")
            }
        }
        return MoveResult(
            moved = added.size,
            conflicts = plan.conflicts,
            movedNames = names,
            message = message
        )
    }

    /** 停课：让某一天不上课，但不删除整学期的排课规则。 */
    suspend fun cancelDay(date: LocalDate, semesterStart: LocalDate, reason: String? = null): Int {
        val week = SemesterWeeks.weekOf(date, semesterStart).coerceAtLeast(1)
        val snapshot = daySnapshotOnce(date, week)
        if (snapshot.baseBlocks.isEmpty()) return 0
        if (snapshot.overrides.any { it.overrideType == OverrideType.CANCEL }) return 0

        overrideDao.upsert(
            ScheduleOverride(
                date = date.toString(),
                type = OverrideType.CANCEL.name,
                name = reason ?: "停课",
                note = reason
            )
        )
        DataChangeBus.publish(DataKind.OVERRIDE, null, "停课 $date")
        return snapshot.baseBlocks.size
    }

    /** 撤销某一天的全部日期变更（把这一天恢复成基础课表）。 */
    suspend fun clearOverridesOn(date: LocalDate): Int {
        val count = overrideDao.getByDate(date.toString()).size
        if (count == 0) return 0
        overrideDao.deleteByDate(date.toString())
        DataChangeBus.publish(DataKind.OVERRIDE, null, "撤销 $date 的调课")
        return count
    }

    /**
     * 临时加课 / 补课：只在 [date] 这一天加一节课，不改动整学期排课规则。
     *
     * 幂等：同一天同名同节次只保留一条。
     */
    suspend fun addOneOff(
        date: LocalDate,
        name: String,
        startPeriod: Int,
        endPeriod: Int,
        location: String? = null,
        teacher: String? = null,
        note: String? = null
    ): Long {
        val existing = overrideDao.findAdded(date.toString(), name, startPeriod, endPeriod)
        if (existing != null) return existing.id

        val id = overrideDao.upsert(
            ScheduleOverride(
                date = date.toString(),
                type = OverrideType.ADD.name,
                name = name,
                teacher = teacher,
                location = location,
                startPeriod = startPeriod,
                endPeriod = endPeriod,
                note = note ?: "临时加课"
            )
        )
        DataChangeBus.publish(DataKind.OVERRIDE, id, "在 $date 加课")
        return id
    }

    /** 换教室：只改某一天的教室，不改排课规则。 */
    suspend fun changeRoomOn(date: LocalDate, courseName: String, location: String, sessionId: Long? = null): Boolean {
        val existing = sessionId?.let { overrideDao.find(date.toString(), OverrideType.ROOM.name, it) }
        val id = overrideDao.upsert(
            (existing ?: ScheduleOverride(date = date.toString(), type = OverrideType.ROOM.name))
                .copy(
                    sourceSessionId = sessionId ?: existing?.sourceSessionId,
                    name = courseName,
                    location = location,
                    note = "换教室"
                )
        )
        DataChangeBus.publish(DataKind.OVERRIDE, id, "换教室 $date")
        return true
    }

    /** 删除单条日期变更记录（撤销用）。 */
    suspend fun deleteOverride(id: Long) {
        overrideDao.deleteById(id)
        DataChangeBus.publish(DataKind.OVERRIDE, id, "删除调课记录")
    }

    /**
     * 只把一门课从 [from] 调到 [to]。
     *
     * 与 [moveDay] 的区别只是「移动哪些课」，幂等与「不碰整学期规则」的保证完全一致。
     */
    suspend fun moveOneCourse(
        from: LocalDate,
        to: LocalDate,
        block: ClassBlock,
        semesterStart: LocalDate,
        totalWeeks: Int
    ): MoveResult {
        if (from == to) return MoveResult(0, emptyList(), emptyList(), "原日期与目标日期相同，未做修改")

        val toWeek = SemesterWeeks.weekOf(to, semesterStart).coerceIn(1, totalWeeks.coerceAtLeast(1))
        val targetExisting = effectiveBlocksOnce(to, toWeek)
        val conflicts = targetExisting.filter {
            block.startPeriod <= it.endPeriod && it.startPeriod <= block.endPeriod
        }

        val stamp = System.currentTimeMillis()
        var added = 0
        db.withTransaction {
            if (overrideDao.find(from.toString(), OverrideType.MOVE_OUT.name, block.sessionId) == null) {
                overrideDao.upsert(
                    ScheduleOverride(
                        date = from.toString(),
                        type = OverrideType.MOVE_OUT.name,
                        sourceSessionId = block.sessionId,
                        courseId = block.courseId,
                        name = block.name,
                        teacher = block.teacher,
                        location = block.location,
                        color = block.color,
                        startPeriod = block.startPeriod,
                        endPeriod = block.endPeriod,
                        note = "已调至 $to",
                        createdAt = stamp
                    )
                )
            }
            if (overrideDao.find(to.toString(), OverrideType.MOVE_IN.name, block.sessionId) == null) {
                overrideDao.upsert(
                    ScheduleOverride(
                        date = to.toString(),
                        type = OverrideType.MOVE_IN.name,
                        sourceSessionId = block.sessionId,
                        courseId = block.courseId,
                        name = block.name,
                        teacher = block.teacher,
                        location = block.location,
                        color = block.color,
                        startPeriod = block.startPeriod,
                        endPeriod = block.endPeriod,
                        note = "由 $from 调入",
                        createdAt = stamp
                    )
                )
                added = 1
            }
        }

        DataChangeBus.publish(DataKind.OVERRIDE, null, "调课 $from → $to（${block.name}）")

        val message = buildString {
            if (added == 0) {
                append("「${block.name}」$from → $to 的调课此前已经执行过，本次未新增")
            } else {
                append("已把「${block.name}」从 $from 调到 $to（第 ${block.startPeriod}-${block.endPeriod} 节）")
            }
            if (conflicts.isNotEmpty()) {
                append("。注意：与 ${conflicts.joinToString("、") { it.name }} 时间重叠")
            }
        }
        return MoveResult(added, conflicts, listOf(block.name), message)
    }

    /** 撤销「把某门课从 [from] 调到 [to]」。 */
    suspend fun undoMoveOne(from: LocalDate, to: LocalDate, sessionId: Long): String {
        var removed = 0
        db.withTransaction {
            overrideDao.find(from.toString(), OverrideType.MOVE_OUT.name, sessionId)?.let {
                overrideDao.deleteById(it.id)
                removed++
            }
            overrideDao.find(to.toString(), OverrideType.MOVE_IN.name, sessionId)?.let {
                overrideDao.deleteById(it.id)
                removed++
            }
        }
        if (removed > 0) DataChangeBus.publish(DataKind.OVERRIDE, null, "撤销调课 $from → $to")
        return if (removed == 0) "没有找到对应的调课记录" else "已撤销：$from → $to 的调课记录已删除"
    }

    /**
     * 只修改某一天某节课的节次 / 教室 / 教师，**整学期规则不变**。
     *
     * 实现方式：给这一天补一条「调出原时段」+「调入修改后时段」的记录。
     * 同一门课重复修改会复用同一条记录（按 sessionId 判重后覆盖），因此不会越改越多。
     */
    suspend fun replaceCourseOnDate(
        date: LocalDate,
        block: ClassBlock,
        startPeriod: Int,
        endPeriod: Int,
        location: String?,
        teacher: String?
    ): String {
        val key = date.toString()
        val stamp = System.currentTimeMillis()

        db.withTransaction {
            if (overrideDao.find(key, OverrideType.MOVE_OUT.name, block.sessionId) == null) {
                overrideDao.upsert(
                    ScheduleOverride(
                        date = key,
                        type = OverrideType.MOVE_OUT.name,
                        sourceSessionId = block.sessionId,
                        courseId = block.courseId,
                        name = block.name,
                        teacher = block.teacher,
                        location = block.location,
                        color = block.color,
                        startPeriod = block.startPeriod,
                        endPeriod = block.endPeriod,
                        note = "本日改为第 $startPeriod-$endPeriod 节",
                        createdAt = stamp
                    )
                )
            }
            val existing = overrideDao.find(key, OverrideType.MOVE_IN.name, block.sessionId)
            overrideDao.upsert(
                (existing ?: ScheduleOverride(date = key, type = OverrideType.MOVE_IN.name))
                    .copy(
                        sourceSessionId = block.sessionId,
                        courseId = block.courseId,
                        name = block.name,
                        teacher = teacher,
                        location = location,
                        color = block.color,
                        startPeriod = startPeriod,
                        endPeriod = endPeriod,
                        note = "本日调整",
                        createdAt = existing?.createdAt ?: stamp
                    )
            )
        }

        DataChangeBus.publish(DataKind.OVERRIDE, null, "修改 $date 的 ${block.name}")
        return "已把 $date 的「${block.name}」改为第 $startPeriod-$endPeriod 节" +
            (location?.let { "，教室 $it" } ?: "") +
            "（其它周次不变）"
    }

    /**
     * 修改整学期的排课规则（**高风险**：只有用户明确要求「以后每周都…」时才应调用）。
     *
     * 返回一段说明实际改了什么的文字；找不到课程时也返回原因，绝不静默修改。
     */
    @Suppress("LongParameterList")
    suspend fun updateCourseRule(
        name: String,
        startPeriod: Int? = null,
        endPeriod: Int? = null,
        dayOfWeek: Int? = null,
        location: String? = null,
        teacher: String? = null,
        startWeek: Int? = null,
        endWeek: Int? = null,
        parity: Int? = null
    ): String {
        val course = courseDao.getCourses().firstOrNull {
            it.name.contains(name) || name.contains(it.name)
        } ?: return "没有找到名为「$name」的课程，未做修改"

        val sessions = courseDao.getSessionsOfCourse(course.id)
        if (sessions.isEmpty()) return "「${course.name}」没有任何上课时段，未做修改"

        val before = sessions.joinToString("、") { s ->
            "${SemesterWeeks.dayLabel(s.dayOfWeek)}第${s.startPeriod}-${s.endPeriod}节"
        }

        db.withTransaction {
            if (teacher != null) courseDao.updateCourse(course.copy(teacher = teacher))
            sessions.forEach { s ->
                courseDao.updateSession(
                    s.copy(
                        dayOfWeek = dayOfWeek ?: s.dayOfWeek,
                        startPeriod = startPeriod ?: s.startPeriod,
                        endPeriod = endPeriod ?: s.endPeriod,
                        location = location ?: s.location,
                        startWeek = startWeek ?: s.startWeek,
                        endWeek = endWeek ?: s.endWeek,
                        weekParity = parity ?: s.weekParity
                    )
                )
            }
        }

        DataChangeBus.publish(DataKind.COURSE, course.id, "修改整学期规则")
        val after = courseDao.getSessionsOfCourse(course.id).joinToString("、") { s ->
            "${SemesterWeeks.dayLabel(s.dayOfWeek)}第${s.startPeriod}-${s.endPeriod}节"
        }
        return "已修改「${course.name}」的整学期排课规则：$before → $after"
    }

    // ---------------- 写入 ----------------
    //
    // 约定：每个写方法在 DAO 调用**返回之后**（事务已提交）调用 DataChangeBus.publish，
    // 订阅者（桌面小组件等）据此刷新。这样不会出现「界面/卡片刷新了但数据还没落库」的假更新。

    suspend fun addCourseWithSessions(course: Course, sessions: List<CourseSession>): Long {
        val id = courseDao.insertCourseWithSessions(course, sessions)
        DataChangeBus.publish(DataKind.COURSE, id, "新增课程")
        return id
    }

    suspend fun updateCourseWithSessions(course: Course, sessions: List<CourseSession>) {
        courseDao.replaceCourseWithSessions(course, sessions)
        DataChangeBus.publish(DataKind.COURSE, course.id, "修改课程")
    }

    suspend fun updateCourse(course: Course) {
        courseDao.updateCourse(course)
        DataChangeBus.publish(DataKind.COURSE, course.id, "修改课程")
    }

    suspend fun deleteCourse(courseId: Long) {
        courseDao.deleteSessionsOfCourse(courseId)
        val c = courseDao.getCourse(courseId)
        if (c != null) courseDao.deleteCourse(c)
        DataChangeBus.publish(DataKind.COURSE, courseId, "删除课程")
    }

    suspend fun clearDay(dayOfWeek: Int) {
        val sessions = courseDao.getSessionsOfDay(dayOfWeek)
        courseDao.deleteSessionsOfDay(dayOfWeek)
        // 清理已无任何时段的课程
        sessions.map { it.courseId }.distinct().forEach { cid ->
            if (courseDao.getSessionsOfCourse(cid).isEmpty()) {
                courseDao.getCourse(cid)?.let { courseDao.deleteCourse(it) }
            }
        }
        DataChangeBus.publish(DataKind.COURSE, null, "清空周$dayOfWeek")
    }

    suspend fun clearAll() {
        courseDao.clearSessions()
        courseDao.clearCourses()
        DataChangeBus.publish(DataKind.COURSE, null, "清空全部课程")
    }

    suspend fun updateSession(session: CourseSession) {
        courseDao.updateSession(session)
        DataChangeBus.publish(DataKind.COURSE, session.courseId, "修改上课时段")
    }

    suspend fun deleteSession(session: CourseSession) {
        courseDao.deleteSession(session)
        if (courseDao.getSessionsOfCourse(session.courseId).isEmpty()) {
            courseDao.getCourse(session.courseId)?.let { courseDao.deleteCourse(it) }
        }
        DataChangeBus.publish(DataKind.COURSE, session.courseId, "删除上课时段")
    }

    suspend fun savePeriodTimes(items: List<PeriodTime>) {
        periodDao.upsertAll(items)
        // 保证作息表严格连续：删除超出范围的节次，避免导入后残留旧节次
        val maxPeriod = items.maxOfOrNull { it.period } ?: 0
        if (maxPeriod > 0) periodDao.deleteFrom(maxPeriod + 1)
        // 节次被缩短时，超出范围的课程时段必须一并处理，否则时间轴会「有课无格」
        pruneSessionsOutside(maxPeriod)
        DataChangeBus.publish(DataKind.PERIOD, null, "修改作息时间")
    }

    /**
     * 处理节次范围之外的上课时段：
     * - 开始节次超出范围 → 删除整条时段
     * - 结束节次超出范围 → 收缩到最后一节
     * - 时段因此失去全部课程时，连带删除课程本身
     */
    private suspend fun pruneSessionsOutside(maxPeriod: Int) {
        if (maxPeriod <= 0) return
        val sessions = courseDao.getSessions()
        val affected = sessions.filter { it.endPeriod > maxPeriod }
        if (affected.isEmpty()) return
        affected.forEach { s ->
            if (s.startPeriod > maxPeriod) {
                courseDao.deleteSession(s)
            } else {
                courseDao.updateSession(s.copy(endPeriod = maxPeriod))
            }
        }
        // 清理已无任何时段的课程
        affected.map { it.courseId }.distinct().forEach { cid ->
            if (courseDao.getSessionsOfCourse(cid).isEmpty()) {
                courseDao.getCourse(cid)?.let { courseDao.deleteCourse(it) }
            }
        }
    }

    suspend fun upsertHoliday(holiday: Holiday) {
        holidayDao.upsert(holiday)
        DataChangeBus.publish(DataKind.HOLIDAY, null, "修改节假日")
    }

    suspend fun deleteHoliday(date: String) {
        holidayDao.deleteByDate(date)
        DataChangeBus.publish(DataKind.HOLIDAY, null, "删除节假日")
    }

    suspend fun replaceHolidays(items: List<Holiday>) {
        holidayDao.clear()
        holidayDao.upsertAll(items)
        DataChangeBus.publish(DataKind.HOLIDAY, null, "替换节假日")
    }

    suspend fun upsertDormService(service: DormService) = dormDao.upsert(service)

    suspend fun deleteDormService(id: Long) = dormDao.delete(id)

    // ---------------- 校园生活设施 ----------------

    suspend fun upsertFacility(facility: CampusFacility): Long = facilityDao.upsert(facility)

    suspend fun deleteFacility(id: Long) = facilityDao.deleteById(id)

    suspend fun facilitiesOnce(): List<CampusFacility> = facilityDao.getAll()

    /** 仅在为空时写入默认设施数据。 */
    suspend fun ensureFacilities(defaults: List<CampusFacility>) {
        if (facilityDao.count() == 0) facilityDao.upsertAll(defaults)
    }

    /** 恢复默认设施数据（覆盖）。 */
    suspend fun resetFacilities(defaults: List<CampusFacility>) {
        facilityDao.clear()
        facilityDao.upsertAll(defaults)
    }

    // ---------------- 待办事项 ----------------

    suspend fun upsertTodo(item: TodoItem): Long {
        val id = todoDao.upsert(item)
        // upsert 对「已有记录」返回的也是同一个主键（TodoItem.id 非 0），日志里用它定位记录
        DataChangeBus.publish(DataKind.TODO, if (item.id != 0L) item.id else id, if (item.id != 0L) "修改待办" else "新增待办")
        return id
    }

    suspend fun deleteTodo(id: Long) {
        todoDao.deleteById(id)
        DataChangeBus.publish(DataKind.TODO, id, "删除待办")
    }

    suspend fun clearTodosOn(date: LocalDate) {
        todoDao.deleteByDate(date.toString())
        DataChangeBus.publish(DataKind.TODO, null, "清空当日待办")
    }

    suspend fun todosOnOnce(date: LocalDate): List<TodoItem> = todoDao.getByDate(date.toString())

    /** 从某天起（含）的所有待办，用于按日期分组展示。 */
    suspend fun todosFrom(fromDate: LocalDate): List<TodoItem> = todoDao.getFromDate(fromDate.toString())

    /** 有安排的日期（用于日历打点），最多取 60 天。 */
    suspend fun datesWithTodos(fromDate: LocalDate): List<LocalDate> =
        todoDao.datesWithTodo(fromDate.toString())
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .take(60)

    /**
     * 导入：用解析结果整体替换课表。
     *
     * **整个替换过程在一个事务里完成**：否则「先清空、再逐条插入」的中间态会被
     * 正在观察 `observeOccurrences()` 的页面和桌面小组件读到，
     * 界面上就会闪一下「课表全空」。
     */
    suspend fun replaceSchedule(courses: List<Pair<Course, List<CourseSession>>>) {
        db.withTransaction {
            courseDao.clearSessions()
            courseDao.clearCourses()
            courses.forEach { (course, sessions) ->
                courseDao.insertCourseWithSessions(course, sessions)
            }
        }
        // 事务提交之后才发布，保证订阅者读到的已经是完整的课表
        DataChangeBus.publish(DataKind.IMPORT, null, "导入课表（${courses.size} 门）")
    }

    /** 初始化默认作息表（仅当为空时）。 */
    suspend fun ensurePeriodTimes(defaults: List<PeriodTime>) {
        if (periodDao.count() == 0) periodDao.upsertAll(defaults)
    }

    // ---------------- 内部工具 ----------------

}

/**
 * 周视图 / 今日页共用的排课原料快照（四张表各取一份当前内容）。
 */
data class PlannerInputs(
    val occurrences: List<ClassOccurrence> = emptyList(),
    val periods: List<PeriodTime> = emptyList(),
    val holidays: List<Holiday> = emptyList(),
    val overrides: List<ScheduleOverride> = emptyList()
)

/**
 * 纯函数：算出某一周每一天「实际要上」的课，供周视图渲染。
 *
 * 与 [DayPlanner.snapshotOf] 走同一条路径（基础排课 → 放假 / 停课 / 调课 / 补课 / 换教室），
 * 因此周视图与今日页、桌面卡片、AI 查询看到的永远是同一个结果——这正是
 * 「界面能看到的，AI 一定能查到；AI 改过的，界面一定立刻反映」的前提。
 */
internal fun weekBlocksOf(
    week: Int,
    semesterStart: LocalDate,
    totalWeeks: Int,
    inputs: PlannerInputs
): List<ClassBlock> {
    if (week !in 1..totalWeeks) return emptyList()
    return SemesterWeeks.weekDates(week, semesterStart)
        .flatMap { date ->
            DayPlanner.snapshotOf(
                date = date,
                week = week,
                occurrences = inputs.occurrences,
                periods = inputs.periods,
                holidays = inputs.holidays,
                overrides = inputs.overrides
            ).blocks
        }
        .sortedWith(compareBy({ it.dayOfWeek }, { it.startPeriod }))
}

/**
 * 「某一天到底上什么课」的**纯函数**实现，不依赖 Room / Context，便于单元测试。
 *
 * 拆成独立对象的原因：基础课表与实际课表的区分最容易出 bug（放假把原始排课也抹掉、
 * 调课套用了目标日期的周次规则等），而这些判定必须能被离线测试覆盖。
 */
internal object DayPlanner {

    /**
     * 组装一天的快照。
     *
     * 1. **基础课表 [DaySnapshot.baseBlocks]**：按调休补课改写的星期（放假**不**参与），
     *    叠加单双周 / 起止周规则。放假日照样返回原本要上的课，
     *    这样 AI 才能查到「10 月 7 日原本应该上的课」并据此调课。
     * 2. **实际课表 [DaySnapshot.blocks]**：在「该日是否真的上课」判定之后，
     *    依次应用停课（CANCEL）、调出（MOVE_OUT）、调入（MOVE_IN）、临时加课（ADD）、换教室（ROOM）。
     */
    fun snapshotOf(
        date: LocalDate,
        week: Int,
        occurrences: List<ClassOccurrence>,
        periods: List<PeriodTime>,
        holidays: List<Holiday>,
        overrides: List<ScheduleOverride> = emptyList()
    ): DaySnapshot {
        val holiday = DayResolver.holidayOn(date, holidays)
        val effectiveDay = DayResolver.effectiveDayOfWeek(date, holiday)
        val baseDay = DayResolver.baseDayOfWeek(date, holiday)

        val dayOverrides = overrides.filter { it.date == date.toString() }

        val baseBlocks = rulesOn(occurrences, periods, date, baseDay, week)
        val taughtBlocks = if (effectiveDay == null) {
            emptyList()
        } else {
            rulesOn(occurrences, periods, date, effectiveDay, week)
        }

        val movedOut = dayOverrides
            .filter { it.overrideType == OverrideType.MOVE_OUT }
            .mapNotNull { it.sourceSessionId }
            .toSet()
        val canceled = dayOverrides.any { it.overrideType == OverrideType.CANCEL }
        val roomChanges = dayOverrides.filter { it.overrideType == OverrideType.ROOM }

        val kept = if (canceled) {
            emptyList()
        } else {
            taughtBlocks.filter { it.sessionId !in movedOut }
        }

        val incoming = dayOverrides
            .filter { it.overrideType == OverrideType.MOVE_IN || it.overrideType == OverrideType.ADD }
            .mapNotNull { it.toClassBlock(date, periods) }

        // 课块的「星期几」统一改写成**日期本身**的星期几。
        //
        // 规则里的 dayOfWeek 只在筛选阶段有意义（调休补课日会用别的星期规则，
        // 例如周六补周一的课），但一旦落到具体某一天，界面就必须按那一天的列来摆。
        // 否则周六补的课会画进周一的格子里。
        val actualDayOfWeek = date.dayOfWeek.value
        fun List<ClassBlock>.pinToDate(): List<ClassBlock> = map {
            if (it.dayOfWeek == actualDayOfWeek) it else it.copy(dayOfWeek = actualDayOfWeek)
        }

        val blocks = (kept.map { block -> block.applyRoomChange(roomChanges) } + incoming)
            .pinToDate()
            .sortedBy { it.startMinutes }

        return DaySnapshot(
            date = date,
            week = week,
            holiday = holiday,
            effectiveDayOfWeek = effectiveDay,
            blocks = blocks,
            baseBlocks = baseBlocks.pinToDate(),
            overrides = dayOverrides
        )
    }

    /** 按「某一天用星期几的规则」筛出当天的课，并套用单双周 / 起止周过滤。 */
    private fun rulesOn(
        occurrences: List<ClassOccurrence>,
        periods: List<PeriodTime>,
        date: LocalDate,
        dayOfWeek: Int,
        week: Int
    ): List<ClassBlock> {
        if (week < 1) return emptyList()
        return buildBlocks(occurrences.filter { it.dayOfWeek == dayOfWeek }, periods, date)
            .filter { WeekPatternParser.matches(week, it.startWeek, it.endWeek, it.weekParity) }
            .sortedBy { it.startMinutes }
    }

    /**
     * 把日期变更记录变成当天的一节课。
     *
     * 教室缺省时用 `null`（界面会显示「教室待定」），节次缺省时用第 1 节。
     */
    private fun ScheduleOverride.toClassBlock(
        date: LocalDate,
        periods: List<PeriodTime>
    ): ClassBlock? {
        val blockName = name?.takeIf { it.isNotBlank() } ?: return null
        val start = startPeriod ?: 1
        val end = endPeriod ?: start
        val times = periods.ifEmpty { DefaultPeriodTimes.ALL }
        val startTime = times.firstOrNull { it.period == start }?.startTime ?: ""
        val endTime = times.firstOrNull { it.period == end }?.endTime ?: ""
        return ClassBlock(
            sessionId = sourceSessionId ?: -id,
            courseId = courseId ?: 0L,
            name = blockName,
            teacher = teacher,
            location = location,
            category = "调课",
            color = color,
            dayOfWeek = date.dayOfWeek.value,
            startPeriod = start,
            endPeriod = end,
            startTime = startTime,
            endTime = endTime,
            startMinutes = times.firstOrNull { it.period == start }?.startMinutes ?: 0,
            endMinutes = times.firstOrNull { it.period == end }?.endMinutes ?: 0,
            weekParity = WeekParity.EVERY,
            startWeek = 1,
            endWeek = 99,
            date = date
        )
    }

    /** 只替换教室，其余字段保持不变。 */
    private fun ClassBlock.applyRoomChange(rooms: List<ScheduleOverride>): ClassBlock {
        val change = rooms.firstOrNull {
            (it.sourceSessionId != null && it.sourceSessionId == sessionId) ||
                (it.name != null && it.name == name)
        } ?: return this
        return copy(location = change.location ?: location)
    }
}

/** 把「课程 + 时段」条目按给定日期的节次时间展开成当天的课块。 */
internal fun buildBlocks(
    occurrences: List<ClassOccurrence>,
    periods: List<PeriodTime>,
    date: LocalDate
): List<ClassBlock> {
    val map = periods.associateBy { it.period }
    return occurrences.mapNotNull { o ->
        val start = map[o.startPeriod] ?: return@mapNotNull null
        val end = map[o.endPeriod] ?: start
        ClassBlock(
            sessionId = o.sessionId,
            courseId = o.courseId,
            name = o.name,
            teacher = o.teacher,
            location = o.location,
            category = o.category,
            color = o.color,
            dayOfWeek = o.dayOfWeek,
            startPeriod = o.startPeriod,
            endPeriod = o.endPeriod,
            startTime = start.startTime,
            endTime = end.endTime,
            startMinutes = start.startMinutes,
            endMinutes = end.endMinutes,
            weekParity = o.weekParity,
            startWeek = o.startWeek,
            endWeek = o.endWeek,
            date = date
        )
    }
}

/** 依据节次时间判断当前是否在某一节课内。 */
fun List<ClassBlock>.currentBlock(now: LocalDateTime): ClassBlock? {
    val minutes = now.hour * 60 + now.minute
    return firstOrNull { minutes in it.startMinutes until it.endMinutes }
}

/** 今天接下来的第一节课。 */
fun List<ClassBlock>.nextBlock(now: LocalDateTime): ClassBlock? {
    val minutes = now.hour * 60 + now.minute
    return filter { it.startMinutes > minutes }.minByOrNull { it.startMinutes }
}

/** 距离某节课开始的分钟数。 */
fun ClassBlock.minutesUntil(now: LocalDateTime): Int {
    val minutes = now.hour * 60 + now.minute
    return startMinutes - minutes
}

/** 单双周标签。 */
val ClassBlock.parityLabel: String get() = WeekParity.label(weekParity)

/** 便捷：把周次换算成日期。 */
fun ClassBlock.dateInWeek(week: Int, semesterStart: LocalDate): LocalDate =
    SemesterWeeks.dateOf(week, dayOfWeek, semesterStart)
