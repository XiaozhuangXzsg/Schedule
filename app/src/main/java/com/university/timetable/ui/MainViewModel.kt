package com.university.timetable.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.university.timetable.ServiceLocator
import com.university.timetable.data.entity.CampusFacility
import com.university.timetable.data.entity.Course
import com.university.timetable.data.entity.CourseSession
import com.university.timetable.data.entity.DormService
import com.university.timetable.data.entity.Holiday
import com.university.timetable.data.entity.PeriodTime
import com.university.timetable.data.entity.TodoItem
import com.university.timetable.data.prefs.PreferencesRepository
import com.university.timetable.data.prefs.UiPreferences
import com.university.timetable.data.repository.ClassBlock
import com.university.timetable.data.repository.DayResolver
import com.university.timetable.data.repository.DaySnapshot
import com.university.timetable.data.repository.FacilityScheduleResolver
import com.university.timetable.data.repository.FacilityStatus
import com.university.timetable.data.repository.MovePlan
import com.university.timetable.data.repository.MoveResult
import com.university.timetable.data.repository.ScheduleRepository
import com.university.timetable.data.repository.weekBlocksOf
import com.university.timetable.data.seed.DefaultFacilities
import com.university.timetable.data.seed.SeedSchedule
import com.university.timetable.data.entity.DefaultPeriodTimes
import com.university.timetable.notification.FluidCloudNotification
import com.university.timetable.notification.FluidCloudRefresher
import com.university.timetable.notification.NextClassResolver
import com.university.timetable.util.SemesterWeeks
import com.university.timetable.widget.WidgetRefresher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** 今日页状态。 */
data class TodayUiState(
    val date: LocalDate = LocalDate.now(),
    val week: Int = 1,
    /**
     * 当前展示的周次（可被「上一周 / 下一周」改变）。
     * [week] 始终是"今天所处的自然周"，而这里跟随用户的周次选择，
     * 顶栏标题与灰色左右箭头都读这个值，否则按钮点了标题不动。
     */
    val selectedWeek: Int = 1,
    val blocks: List<ClassBlock> = emptyList(),
    val nowBlock: ClassBlock? = null,
    val nextBlock: ClassBlock? = null,
    val tomorrowFirst: ClassBlock? = null,
    val holiday: Holiday? = null,
    val isMakeupDay: Boolean = false,
    val periods: List<PeriodTime> = emptyList()
) {
    val hasClass: Boolean get() = blocks.isNotEmpty()
}

/** 单个周次的数据（周视图分页滑动时并行持有相邻周）。 */
data class WeekSlice(
    val week: Int = 1,
    val dates: List<LocalDate> = emptyList(),
    val blocks: List<ClassBlock> = emptyList()
) {
    fun blocksOf(dayOfWeek: Int): List<ClassBlock> =
        blocks.filter { it.dayOfWeek == dayOfWeek }.sortedBy { it.startPeriod }
}

/** 周视图状态。 */
data class WeekUiState(
    val selectedWeek: Int = 1,
    val currentWeek: Int = 1,
    val dates: List<LocalDate> = emptyList(),
    val periods: List<PeriodTime> = emptyList(),
    val blocks: List<ClassBlock> = emptyList(),
    val today: LocalDate = LocalDate.now(),
    /** 上一周（用于左右滑动时的连续动画）；第 1 周时为 null。 */
    val previous: WeekSlice? = null,
    /** 下一周；最后一周时为 null。 */
    val next: WeekSlice? = null,
    /** 总周数，供分页边界判断。 */
    val totalWeeks: Int = 1
) {
    fun blocksOf(dayOfWeek: Int): List<ClassBlock> =
        blocks.filter { it.dayOfWeek == dayOfWeek }.sortedBy { it.startPeriod }
}

/** 洗衣机倒计时。 */
data class LaundryTimer(
    val id: String,
    val label: String,
    val endAtMillis: Long
)

/** 校园生活页面状态。 */
data class CampusUiState(
    val date: LocalDate = LocalDate.now(),
    val nowMinutes: Int = 0,
    val isHoliday: Boolean = false,
    val holidayName: String? = null,
    val statuses: List<FacilityStatus> = emptyList()
) {
    val openCount: Int get() = statuses.count { it.isOpen && !it.closedAllDay }
}

@kotlin.OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val repo: ScheduleRepository,
    private val prefsRepo: PreferencesRepository,
    private val appContext: Context
) : ViewModel() {

    val prefs: StateFlow<UiPreferences> = prefsRepo.preferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiPreferences())

    /** 手动选中的周次（用于周视图左右滑动与三点菜单拖动）。 */
    private val _selectedWeek = MutableStateFlow<Int?>(null)

    /**
     * 一次性提示（写入失败等）。
     *
     * 页面用 Snackbar 消费它。**只在真的失败时发消息**，
     * 成功时不提示，也就不存在「显示虚假的成功」的问题。
     */
    private val _messages = kotlinx.coroutines.flow.MutableSharedFlow<String>(
        extraBufferCapacity = 8
    )
    val messages: kotlinx.coroutines.flow.SharedFlow<String> = _messages.asSharedFlow()
    /**
     * 统一的数据写入入口。
     *
     * - 协程跑在 [Dispatchers.IO] 上，Room 的挂起写方法返回即代表事务已提交；
     * - 失败只提示，**不会**把界面上的乐观状态留在错误位置（这些写入都不是乐观更新，
     *   界面数据一律来自 Room 的 Flow，所以失败时界面自然还是数据库里的旧值）；
     * - 失败后不重放业务写入，只提示用户重试。
     */
    private fun launchWrite(what: String, block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { block() }.onFailure { error ->
                android.util.Log.e("MainViewModel", "$what 失败", error)
                _messages.tryEmit("${what}失败，数据未改动，请重试")
            }
        }
    }

    private val _currentDate = MutableStateFlow(LocalDate.now())
    val currentDate: StateFlow<LocalDate> = _currentDate.asStateFlow()

    private val _laundryTimers = MutableStateFlow<List<LaundryTimer>>(emptyList())
    val laundryTimers: StateFlow<List<LaundryTimer>> = _laundryTimers.asStateFlow()

    val dormServices: StateFlow<List<DormService>> = repo.dormServices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 校园生活设施原始数据（可编辑）。 */
    val campusFacilities: StateFlow<List<CampusFacility>> = repo.campusFacilities
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 节假日 / 调休安排（供日历页标注节日使用）。 */
    val holidays: StateFlow<List<com.university.timetable.data.entity.Holiday>> = repo.holidays
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 纯时间脉冲（每 30 秒一次）。
     *
     * 只驱动「现在几点 / 第几节 / 是否跨日」这类与墙上时钟有关的内容，
     * **不读取数据库**，因此不是对数据的轮询。
     *
     * 声明位置必须在 [campusState] 之前：Kotlin 的属性初始化是按声明顺序执行的，
     * 后声明的属性不能在先声明的属性初始化表达式里被引用。
     */
    private val timeTick: Flow<LocalDateTime> = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(30_000L)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LocalDateTime.now())

    /** 校园生活页面状态：按今天日期与节假日实时计算开放情况。 */
    val campusState: StateFlow<CampusUiState> = combine(
        _currentDate, repo.campusFacilities, repo.holidays, timeTick
    ) { date, facilities, holidays, now ->
        // nowMinutes 取自 timeTick 而不是 LocalTime.now()：
        // 否则「开放中 / 未开放」只会在数据变化时才重算，停留在这个页面上跨过开放时间点时不会变。
        val nowMinutes = now.hour * 60 + now.minute
        // 节假日直接来自 Room 的 Flow（而不是一次性查询），
        // 这样修改 / 删除节假日之后这里会立刻重新发射。
        val holiday = DayResolver.holidayOn(date, holidays)
        val isHoliday = holiday?.isHoliday == true
        CampusUiState(
            date = date,
            nowMinutes = nowMinutes,
            isHoliday = isHoliday,
            holidayName = holiday?.takeIf { it.isHoliday }?.name,
            statuses = facilities
                .filter { it.enabled }
                .sortedBy { it.sortOrder }
                .map { FacilityScheduleResolver.resolve(it, date, nowMinutes, isHoliday) }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CampusUiState())

    /** 今日待办事项。 */
    val todayTodos: StateFlow<List<TodoItem>> = _currentDate
        .flatMapLatest { repo.todosOn(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 全部待办（用于日历打点与统计）。 */
    val allTodos: StateFlow<List<TodoItem>> = repo.allTodos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val periodTimes: StateFlow<List<PeriodTime>> = repo.periodTimes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DefaultPeriodTimes.ALL)

    val courses: StateFlow<List<Course>> = repo.courses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 今日页的输入参数：把它们打包成一个对象，方便在 flatMapLatest 里一次性拿到。 */
    private data class TodayInput(
        val date: LocalDate,
        val prefs: UiPreferences,
        val periods: List<PeriodTime>,
        val selectedWeek: Int?
    )

    /**
     * 今日页状态。
     *
     * 课程数据来自 [ScheduleRepository.daySnapshot]——它由 `observeOccurrences()` /
     * `observePeriods()` / `observeHolidays()` 三个 Room Flow 组合而成，
     * 所以**新增、修改、删除课程或作息后，今日页会自动重新发射**，
     * 不再依赖写入方手动「戳一下」。
     *
     * 时间相关的字段（nowBlock / nextBlock）额外与 [timeTick] 组合，
     * 保证课程开始、结束的瞬间会自动切换「正在上 / 接下来」。
     */
    val todayState: StateFlow<TodayUiState> = combine(
        _currentDate, prefs, periodTimes, _selectedWeek
    ) { date, p, periods, manual ->
        TodayInput(date, p, periods, manual)
    }.flatMapLatest { input ->
        val start = LocalDate.parse(input.prefs.semesterStart)
        val currentWeek = SemesterWeeks.weekOf(input.date, start)
        val week = (input.selectedWeek ?: currentWeek).coerceIn(1, input.prefs.totalWeeks)

        val tomorrow = input.date.plusDays(1)
        val tomorrowWeek = SemesterWeeks.weekOf(tomorrow, start)
        val tomorrowFlow = if (tomorrowWeek in 1..input.prefs.totalWeeks) {
            repo.daySnapshot(tomorrow, tomorrowWeek)
        } else {
            flowOf(null)
        }

        combine(repo.daySnapshot(input.date, week), tomorrowFlow, timeTick) { todaySnap, tomorrowSnap, now ->
            buildTodayState(input, week, currentWeek, todaySnap, tomorrowSnap, now)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TodayUiState())

    val weekState: StateFlow<WeekUiState> = combine(
        _selectedWeek, prefs, repo.plannerInputs, periodTimes, _currentDate
    ) { manual, p, inputs, periods, date ->
        val start = LocalDate.parse(p.semesterStart)
        val currentWeek = SemesterWeeks.weekOf(date, start).coerceIn(1, p.totalWeeks)
        val week = (manual ?: currentWeek).coerceIn(1, p.totalWeeks)

        /**
         * 取某一周的全部上课块（含放假、停课、调课、补课、换教室与单双周过滤）。
         *
         * 直接复用 [weekBlocksOf]，也就是今日页 / 桌面卡片 / AI 用的同一条
         * 「基础课表 → 实际课表」路径，避免「周视图认为有课、今日页认为没课」
         * 或者「AI 调完课周视图看不见」这类各算各的分叉。
         */
        fun sliceOf(w: Int): WeekSlice {
            if (w !in 1..p.totalWeeks) return WeekSlice(week = w)
            return WeekSlice(
                week = w,
                dates = SemesterWeeks.weekDates(w, start),
                blocks = weekBlocksOf(w, start, p.totalWeeks, inputs)
            )
        }

        val current = sliceOf(week)
        WeekUiState(
            selectedWeek = week,
            currentWeek = currentWeek,
            dates = current.dates,
            periods = periods,
            blocks = current.blocks,
            today = date,
            // 相邻周：只为滑动动画预热，避免翻页时看到空白
            previous = if (week > 1) sliceOf(week - 1) else null,
            next = if (week < p.totalWeeks) sliceOf(week + 1) else null,
            totalWeeks = p.totalWeeks
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WeekUiState())

    init {
        viewModelScope.launch {
            bootstrap()
        }
        // 时间脉冲：跨日时才做「换天」处理（刷新日期、逾期状态与桌面卡片）。
        // 应用长时间在前台运行时，30 秒的时间脉冲保证跨零点后自动切到新的一天；
        // 应用回到前台时的核对由 TimetableApp 的应用级生命周期回调负责。
        viewModelScope.launch {
            timeTick.collect { now ->
                val today = now.toLocalDate()
                if (today != _currentDate.value) {
                    _currentDate.value = today
                    refreshFluidCloud()
                    // 日期真的变了才请求刷新，不做固定轮询式的卡片刷新
                    WidgetRefresher.request(appContext, "跨日：$today")
                }
            }
        }
    }

    /**
     * 应用回到前台时调用：重新核对日期与教学周，并刷新流体云。
     *
     * 桌面卡片的前台刷新由 [com.university.timetable.TimetableApp] 的应用级生命周期回调负责，
     * 这里只处理与「当前页面」有关的时间状态，避免每次页面切换都重复请求刷新。
     */
    fun onEnterForeground() {
        val today = LocalDate.now()
        if (today != _currentDate.value) _currentDate.value = today
        refreshFluidCloud()
    }

    /** 首次启动：写入默认作息、示例课表、节假日与生活服务信息。 */
    private suspend fun bootstrap() {
        // 必须直接向 DataStore 要一次真实值，不能用 prefs.value：
        // prefs 是 stateIn 出来的 StateFlow，在 DataStore 首次发射之前它的值是
        // UiPreferences() 默认值（seeded = false）。用它判断会把「已有数据的正常启动」
        // 误判成「首次启动」，于是重复执行 replaceHolidays(示例节假日)，
        // 把用户自己添加的节假日清掉，并多发一次没有必要的卡片刷新。
        val p = prefsRepo.preferences.first()
        if (!p.seeded) {
            withContext(Dispatchers.IO) {
                repo.ensurePeriodTimes(DefaultPeriodTimes.ALL)
                repo.ensureFacilities(DefaultFacilities.ALL)
                if (repo.coursesOnce().isEmpty()) {
                    SeedSchedule.entries.forEach { entry ->
                        repo.addCourseWithSessions(
                            entry.course,
                            SeedSchedule.courseSessions(0L, entry.specs)
                        )
                    }
                }
                repo.replaceHolidays(SeedSchedule.holidays)
                SeedSchedule.dormServices.forEach { repo.upsertDormService(it) }
                prefsRepo.setSeeded(true)
                prefsRepo.setSemesterStart(SeedSchedule.SEMESTER_START)
                prefsRepo.setTotalWeeks(SeedSchedule.TOTAL_WEEKS)
            }
        } else {
            repo.ensurePeriodTimes(DefaultPeriodTimes.ALL)
            repo.ensureFacilities(DefaultFacilities.ALL)
        }
        refreshFluidCloud()
    }

    /**
     * 由 Room Flow 产出的当日/次日快照组装今日页状态。
     *
     * 因为 [todaySnap] / [tomorrowSnap] 已经由数据层算好（含节假日、调休与单双周过滤），
     * 这里只做纯计算，不再发起任何一次性数据库查询——这正是「课表改了今日页却不动」的修复点。
     */
    private fun buildTodayState(
        input: TodayInput,
        week: Int,
        currentWeek: Int,
        todaySnap: DaySnapshot,
        tomorrowSnap: DaySnapshot?,
        now: LocalDateTime
    ): TodayUiState {
        val blocks = todaySnap.blocks
        val nowMinutes = now.hour * 60 + now.minute

        return TodayUiState(
            date = input.date,
            week = currentWeek,
            selectedWeek = week,
            blocks = blocks,
            nowBlock = blocks.firstOrNull { nowMinutes in it.startMinutes until it.endMinutes },
            nextBlock = blocks.filter { it.startMinutes > nowMinutes }.minByOrNull { it.startMinutes },
            tomorrowFirst = tomorrowSnap?.blocks?.minByOrNull { it.startMinutes },
            holiday = todaySnap.holiday,
            isMakeupDay = todaySnap.holiday?.isMakeupDay == true,
            periods = input.periods
        )
    }

    // ---------------- 周次操作 ----------------

    fun selectWeek(week: Int) {
        val total = prefs.value.totalWeeks
        _selectedWeek.value = week.coerceIn(1, total)
    }

    fun stepWeek(delta: Int) = selectWeek((weekState.value.selectedWeek + delta))

    fun resetWeekSelection() {
        _selectedWeek.value = null
    }

    fun jumpToCurrentWeek() = resetWeekSelection()

    // ---------------- 课程增删改 ----------------

    fun saveCourse(
        existing: Course?,
        name: String,
        teacher: String?,
        location: String?,
        color: Int?,
        category: String,
        dayOfWeek: Int,
        startPeriod: Int,
        endPeriod: Int,
        startWeek: Int,
        endWeek: Int,
        parity: Int
    ) {
        launchWrite("保存课程") {
            val session = CourseSession(
                courseId = existing?.id ?: 0L,
                dayOfWeek = dayOfWeek,
                startPeriod = startPeriod,
                endPeriod = endPeriod,
                location = location,
                startWeek = startWeek,
                endWeek = endWeek,
                weekParity = parity
            )
            if (existing == null) {
                val course = Course(
                    name = name,
                    teacher = teacher,
                    color = color,
                    category = category
                )
                repo.addCourseWithSessions(course, listOf(session))
            } else {
                // 编辑主时段：更新课程信息与首个时段，其余时段保持不变
                val sessions = repo.sessionsOfCourse(existing.id).toMutableList()
                val updatedCourse = existing.copy(
                    name = name,
                    teacher = teacher,
                    color = color,
                    category = category
                )
                if (sessions.isEmpty()) {
                    sessions.add(session)
                } else {
                    val first = sessions.first().copy(
                        dayOfWeek = dayOfWeek,
                        startPeriod = startPeriod,
                        endPeriod = endPeriod,
                        location = location,
                        startWeek = startWeek,
                        endWeek = endWeek,
                        weekParity = parity
                    )
                    sessions[0] = first
                }
                repo.updateCourseWithSessions(updatedCourse, sessions)
            }
            afterMutation()
        }
    }

    /** 更新单个时段（长按拖拽 / 编辑某个时段）。 */
    fun updateSession(session: CourseSession) {
        launchWrite("修改上课时段") {
            repo.updateSession(session)
            afterMutation()
        }
    }

    fun deleteCourse(courseId: Long) {
        launchWrite("删除课程") {
            repo.deleteCourse(courseId)
            afterMutation()
        }
    }

    fun deleteSession(sessionId: Long) {
        launchWrite("删除上课时段") {
            val target = findSessionById(sessionId)
            if (target != null) repo.deleteSession(target)
            afterMutation()
        }
    }

    private suspend fun findSessionById(sessionId: Long): CourseSession? {
        repo.coursesOnce().forEach { c ->
            repo.sessionsOfCourse(c.id).firstOrNull { it.id == sessionId }?.let { return it }
        }
        return null
    }

    fun clearDay(dayOfWeek: Int) {
        launchWrite("清空当天课程") {
            repo.clearDay(dayOfWeek)
            afterMutation()
        }
    }

    fun clearAllCourses() {
        launchWrite("清空全部课程") {
            repo.clearAll()
            afterMutation()
        }
    }

    // ---------------- 快捷调休 ----------------

    private fun semesterStartValue(): LocalDate =
        runCatching { LocalDate.parse(prefs.value.semesterStart) }.getOrDefault(LocalDate.now())

    /**
     * 预演「把 [from] 当天应上的全部课程移到 [to]」。
     *
     * 只读，不写库；用于确认前展示将要移动的课程与时间冲突。
     * 源日期按**它自己**的周次 / 单双周 / 有效周次筛选，放假当天也能取到原本应上的课。
     */
    suspend fun planMakeup(from: LocalDate, to: LocalDate): MovePlan =
        withContext(Dispatchers.IO) {
            repo.planMoveDay(from, to, semesterStartValue(), prefs.value.totalWeeks)
        }

    /** 真正执行调休；完成后立即刷新界面、桌面卡片与提醒。 */
    suspend fun applyMakeup(from: LocalDate, to: LocalDate): MoveResult =
        withContext(Dispatchers.IO) {
            val result = repo.moveDay(from, to, semesterStartValue(), prefs.value.totalWeeks)
            afterMutation()
            result
        }

    /** 撤销某天的调课 / 停课改动。 */
    suspend fun undoDayChanges(date: LocalDate): Int =
        withContext(Dispatchers.IO) {
            val removed = repo.clearOverridesOn(date)
            afterMutation()
            removed
        }

    fun replaceSchedule(entries: List<Pair<Course, List<CourseSession>>>) {
        launchWrite("导入课表") {
            repo.replaceSchedule(entries)
            afterMutation()
        }
    }

    fun savePeriodTimes(items: List<PeriodTime>) {
        launchWrite("保存作息时间") {
            repo.savePeriodTimes(items)
            // 作息变化会改变时间轴与课程块，必须与课程数据一起刷新
            afterMutation()
        }
    }

    fun upsertDormService(service: DormService) {
        viewModelScope.launch(Dispatchers.IO) { repo.upsertDormService(service) }
    }

    fun deleteDormService(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { repo.deleteDormService(id) }
    }

    // ---------------- 校园生活设施 ----------------

    fun upsertFacility(facility: CampusFacility) {
        viewModelScope.launch(Dispatchers.IO) { repo.upsertFacility(facility) }
    }

    fun deleteFacility(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { repo.deleteFacility(id) }
    }

    /** 恢复校园生活默认数据。 */
    fun resetFacilities() {
        viewModelScope.launch(Dispatchers.IO) {
            repo.resetFacilities(DefaultFacilities.ALL)
        }
    }

    // ---------------- 待办事项 ----------------
    //
    // 页面数据来自 Room 的 Flow（todayTodos / allTodos / todayState），写入后会自动重新发射；
    // 桌面卡片则由仓库写入成功后发布的 DataChangeBus 事件驱动刷新。
    // 因此这里**不再**逐个写入点手动调用刷新，避免漏掉某一条路径。

    fun saveTodo(
        existing: TodoItem?,
        date: LocalDate,
        content: String,
        imageUris: List<String>,
        done: Boolean = false
    ) {
        launchWrite("保存待办") {
            val item = TodoItem(
                id = existing?.id ?: 0L,
                date = date.toString(),
                content = content.trim(),
                imageUris = TodoItem.encodeImages(imageUris),
                done = existing?.done ?: done,
                sortOrder = existing?.sortOrder ?: 0,
                createdAt = existing?.createdAt ?: System.currentTimeMillis()
            )
            repo.upsertTodo(item)
            refreshFluidCloud()
        }
    }

    fun deleteTodo(id: Long) {
        launchWrite("删除待办") {
            repo.deleteTodo(id)
            refreshFluidCloud()
        }
    }

    fun toggleTodoDone(item: TodoItem) {
        launchWrite("更新待办状态") {
            repo.upsertTodo(item.copy(done = !item.done))
            refreshFluidCloud()
        }
    }

    fun clearTodosOn(date: LocalDate) {
        launchWrite("清空待办") {
            repo.clearTodosOn(date)
            refreshFluidCloud()
        }
    }

    fun upsertHoliday(holiday: Holiday) {
        launchWrite("保存节假日") {
            repo.upsertHoliday(holiday)
            refreshFluidCloud()
        }
    }

    /**
     * 让桌面卡片重新读取数据。
     *
     * 通过 [WidgetRefresher] 请求，它自带应用级作用域与请求合并，
     * 因此不绑定页面、连续多次调用也不会把刷新任务取消或丢事件。
     */
    private fun refreshWidgets(reason: String) {
        WidgetRefresher.request(appContext, reason)
    }

    /**
     * 立刻刷新桌面卡片并等待结果。
     *
     * 只用于「用户主动点了刷新」这种需要给出确定反馈的场景；
     * 正常的数据变更走 [WidgetRefresher.request] 的合并路径即可。
     */
    suspend fun refreshWidgetsNow(): Boolean = runCatching {
        WidgetRefresher.refreshNow(appContext, "用户手动刷新")
    }.isSuccess

    /**
     * 课程 / 课表相关的写入之后调用。
     *
     * 页面更新由 Room Flow 自动完成，不需要（也无法）靠给 `_currentDate` 赋同一个值来「戳」——
     * `MutableStateFlow` 对相等的值会直接忽略，同一天内的这种赋值是空操作。
     */
    private suspend fun afterMutation() {
        refreshFluidCloud()
    }

    // ---------------- 偏好设置 ----------------
    //
    // 其中「主题」「深浅色」「哥伦比娅风格」「学期开始 / 总周数」会直接改变桌面卡片的
    // 配色与周次文案，所以写入后要请求刷新卡片；头像、昵称等与卡片无关的偏好不刷新。

    fun setDarkTheme(v: Boolean) = viewModelScope.launch {
        prefsRepo.setDarkTheme(v)
        refreshWidgets("切换深浅色")
    }

    fun setFollowSystem(v: Boolean) = viewModelScope.launch {
        prefsRepo.setFollowSystem(v)
        refreshWidgets("跟随系统深浅色")
    }

    fun setThemePreset(i: Int) = viewModelScope.launch {
        prefsRepo.setThemePreset(i)
        refreshWidgets("切换主题色")
    }

    fun setSimpleMode(v: Boolean) = viewModelScope.launch { prefsRepo.setSimpleMode(v) }
    fun setShowTeacher(v: Boolean) = viewModelScope.launch { prefsRepo.setShowTeacher(v) }
    fun setShowLocation(v: Boolean) = viewModelScope.launch { prefsRepo.setShowLocation(v) }
    fun setShowWeekTag(v: Boolean) = viewModelScope.launch { prefsRepo.setShowWeekTag(v) }
    fun setShowWeekend(v: Boolean) = viewModelScope.launch { prefsRepo.setShowWeekend(v) }
    fun setShowNowIndicator(v: Boolean) = viewModelScope.launch { prefsRepo.setShowNowIndicator(v) }
    fun setShowLifeAssistant(v: Boolean) = viewModelScope.launch { prefsRepo.setShowLifeAssistant(v) }

    /** 周视图「当前时间轴条」开关。 */
    fun setWeekNowLine(v: Boolean) = viewModelScope.launch { prefsRepo.setWeekNowLine(v) }

    /** 时间轴条样式：细线+圆球 / 加粗 / 仅细线 / 虚线。 */
    fun setWeekNowLineStyle(style: Int) = viewModelScope.launch { prefsRepo.setWeekNowLineStyle(style) }

    /** 时间轴条颜色；传 `UiPreferences.UNSET` 恢复为跟随主题强调色。 */
    fun setWeekNowLineColor(color: Int) = viewModelScope.launch { prefsRepo.setWeekNowLineColor(color) }

    /** 今日页「正在上的课」是否显示进度。 */
    fun setNowProgress(v: Boolean) = viewModelScope.launch { prefsRepo.setNowProgress(v) }

    /** 进度样式：从左到右 / 从下到上。 */
    fun setNowProgressStyle(style: Int) = viewModelScope.launch { prefsRepo.setNowProgressStyle(style) }

    /** 桌面卡片背景透明度。卡片底色由这里决定，所以写入后要请求刷新卡片。 */
    fun setWidgetBgAlpha(alpha: Float) = viewModelScope.launch {
        prefsRepo.setWidgetBgAlpha(alpha)
        refreshWidgets("修改卡片透明度")
    }

    /** 新手引导完成或主动跳过。 */
    fun setTutorialSeen(v: Boolean) = viewModelScope.launch { prefsRepo.setTutorialSeen(v) }

    /** 已上完课程的显示方式：置灰 / 隐藏。 */
    fun setPastCourseMode(mode: Int) = viewModelScope.launch { prefsRepo.setPastCourseMode(mode) }

    /** 子页面转场动画风格：交叉淡化 / 缩放 / 流体滑入。 */
    fun setTransitionStyle(style: Int) = viewModelScope.launch { prefsRepo.setTransitionStyle(style) }

    /** 触感反馈（柔性反馈）开关。 */
    fun setHapticFeedback(value: Boolean) = viewModelScope.launch { prefsRepo.setHapticFeedback(value) }

    /** 关闭首次启动的软件说明后写入标记，之后不再自动弹出。 */
    fun dismissIntro() = viewModelScope.launch { prefsRepo.setIntroSeen(true) }

    /** 设置页顶部昵称（留空则恢复默认「娅娅」）。 */
    fun setNickname(value: String) = viewModelScope.launch { prefsRepo.setNickname(value) }

    /** 自定义头像；传空串恢复默认头像（应用图标）。 */
    fun setAvatarUri(uri: String) = viewModelScope.launch { prefsRepo.setAvatarUri(uri) }

    /** 哥伦比娅主题风格开关。桌面卡片共用同一套配色，所以也要刷新卡片。 */
    fun setColumbinaStyle(value: Boolean) = viewModelScope.launch {
        prefsRepo.setColumbinaStyle(value)
        refreshWidgets("切换哥伦比娅风格")
    }

    fun setSemesterStart(iso: String) = viewModelScope.launch {
        prefsRepo.setSemesterStart(iso)
        refreshWidgets("修改学期开始日期")
        afterMutation()
    }

    fun setTotalWeeks(weeks: Int) = viewModelScope.launch {
        prefsRepo.setTotalWeeks(weeks)
        refreshWidgets("修改总周数")
        afterMutation()
    }

    fun setReminderEnabled(v: Boolean) = viewModelScope.launch { prefsRepo.setReminderEnabled(v) }
    fun setRemindBefore(minutes: Int) = viewModelScope.launch { prefsRepo.setRemindBefore(minutes) }

    fun setFluidCloudEnabled(v: Boolean) = viewModelScope.launch {
        prefsRepo.setFluidCloudEnabled(v)
        if (!v) FluidCloudNotification.cancel(appContext) else refreshFluidCloud()
    }

    fun setPageBackground(page: String, color: Int) =
        viewModelScope.launch { prefsRepo.setPageBackground(page, color) }

    fun setPageBackgroundImage(page: String, uri: String) =
        viewModelScope.launch { prefsRepo.setPageBackgroundImage(page, uri) }

    fun setBackgroundImageAlpha(a: Float) = viewModelScope.launch { prefsRepo.setBackgroundImageAlpha(a) }

    fun setBackgroundImageBlur(b: Float) = viewModelScope.launch { prefsRepo.setBackgroundImageBlur(b) }

    fun setAutoCheckUpdates(v: Boolean) = viewModelScope.launch { prefsRepo.setAutoCheckUpdates(v) }

    // ---------------- 流体云 / 提醒 ----------------

    fun refreshFluidCloud() {
        // 统一交给应用级刷新器：它读的是**实际课表**，并且与 AI 直接改库的路径共用同一入口，
        // 避免「界面写入会刷新、AI 写入不刷新」这种两条链路不一致的问题。
        FluidCloudRefresher.request(appContext, "页面写入")
    }

    suspend fun nextClassInfo(): NextClassResolver.NextClassInfo? =
        NextClassResolver.resolve(appContext, prefs.value)

    // ---------------- 洗衣机倒计时 ----------------

    fun startLaundryTimer(minutes: Int, label: String = "洗衣机") {
        val id = System.currentTimeMillis().toString()
        val endAt = System.currentTimeMillis() + minutes * 60_000L
        _laundryTimers.value = _laundryTimers.value + LaundryTimer(id, label, endAt)
        LaundryTimerScheduler.schedule(appContext, id, label, minutes)
        com.university.timetable.notification.LaundryNotifier.notifyProgress(
            appContext, id, label, endAt
        )
    }

    fun cancelLaundryTimer(id: String) {
        _laundryTimers.value = _laundryTimers.value.filterNot { it.id == id }
        LaundryTimerScheduler.cancel(appContext, id)
        com.university.timetable.notification.LaundryNotifier.cancelProgress(appContext)
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = context.applicationContext
                return MainViewModel(
                    ServiceLocator.schedule(app),
                    ServiceLocator.preferences(app),
                    app
                ) as T
            }
        }
    }
}
