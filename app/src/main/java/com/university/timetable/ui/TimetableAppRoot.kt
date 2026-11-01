package com.university.timetable.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.university.timetable.ui.component.CapsuleTab
import com.university.timetable.ui.component.CapsuleTabRow
import com.university.timetable.ui.component.SubPageTransition
import com.university.timetable.ui.screen.SettingsScreen
import com.university.timetable.ui.screen.TodayScreen
import com.university.timetable.ui.screen.WeekScreen
import com.university.timetable.ui.screen.dialog.CourseEditorSheet
import com.university.timetable.ui.screen.dialog.FullScreenOverlay
import com.university.timetable.ui.screen.dialog.ImportDialog
import com.university.timetable.ui.screen.dialog.UpdateDialog
import com.university.timetable.ui.screen.sheet.ClassActionsSheet
import com.university.timetable.update.UpdateViewModel
import kotlinx.coroutines.delay
import com.university.timetable.ui.tutorial.TutorialAnchors
import com.university.timetable.ui.tutorial.TutorialHost
import com.university.timetable.ui.tutorial.rememberTutorialController
import kotlinx.coroutines.launch

/** 三个主页面在 pager 中的顺序。 */
object Pages {
    const val TODAY = 0
    const val WEEK = 1
    const val SETTINGS = 2
}

@Composable
fun TimetableAppRoot(
    viewModel: MainViewModel,
    aiViewModel: com.university.timetable.ui.ai.AiViewModel,
    updateViewModel: UpdateViewModel,
    darkTheme: Boolean,
    startOnToday: Boolean = false
) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val todayState by viewModel.todayState.collectAsStateWithLifecycle()
    val weekState by viewModel.weekState.collectAsStateWithLifecycle()
    val campusState by viewModel.campusState.collectAsStateWithLifecycle()
    val courses by viewModel.courses.collectAsStateWithLifecycle()
    val allTodos by viewModel.allTodos.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val tutorial = rememberTutorialController()
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()

    // 启动后延迟一小会儿再检查更新：不抢占首屏渲染，也不和首启引导弹窗同时出现。
    LaunchedEffect(Unit) {
        delay(2500)
        updateViewModel.autoCheckIfDue()
    }

    LaunchedEffect(updateViewModel) {
        updateViewModel.events.collect { snackbarHostState.showSnackbar(it) }
    }

    // 首次启动引导：等「软件说明」弹窗关掉之后再弹，两者不会同时出现。
    LaunchedEffect(prefs.introSeen, prefs.tutorialSeen) {
        if (prefs.introSeen && !prefs.tutorialSeen && !tutorial.state.active) {
            tutorial.start()
        }
    }

    // 写入失败提示：只在真的失败时才有消息，成功不会弹任何东西。
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val tabs = remember {
        listOf(
            CapsuleTab("今日", Icons.Filled.WbSunny),
            CapsuleTab("本周", Icons.Filled.CalendarMonth),
            CapsuleTab("设置", Icons.Filled.Settings, TutorialAnchors.SETTINGS_ENTRY)
        )
    }

    val pagerState = rememberPagerState(initialPage = Pages.TODAY) { tabs.size }

    // ---------------- 弹窗 / 覆盖层状态 ----------------
    var editorTarget by remember { mutableStateOf<EditorTarget?>(null) }
    var importDialogVisible by remember { mutableStateOf(false) }
    var actionsTarget by remember {
        mutableStateOf<com.university.timetable.data.repository.ClassBlock?>(null)
    }
    var periodEditorVisible by remember { mutableStateOf(false) }
    var settingsVisible by remember { mutableStateOf(false) }
    var widgetHelpVisible by remember { mutableStateOf(false) }
    var makeupVisible by remember { mutableStateOf(false) }
    var backgroundVisible by remember { mutableStateOf(false) }
    var weekMenuVisible by remember { mutableStateOf(false) }
    var addedCoursesVisible by remember { mutableStateOf(false) }
    var todoVisible by remember { mutableStateOf(false) }
    // 「联系我们」独立弹窗（不再跳转校园生活）
    var contactVisible by remember { mutableStateOf(false) }
    var easterEggVisible by remember { mutableStateOf(false) }

    // 走进第 3 步（导入课表）之后，说明弹窗/小组件帮助弹窗会盖住引导层，必须先关掉。
    LaunchedEffect(tutorial.state.active, tutorial.state.index, tutorial.state.showWelcome) {
        if (tutorial.state.active && !tutorial.state.showWelcome && tutorial.state.index >= 2) {
            importDialogVisible = false
            widgetHelpVisible = false
        }
    }

    // 子页面（校园生活 / AI）：先挂载，再播放转场
    var campusLifeVisible by remember { mutableStateOf(false) }
    var campusLifeMounted by remember { mutableStateOf(false) }
    var calendarVisible by remember { mutableStateOf(false) }
    var calendarMounted by remember { mutableStateOf(false) }
    var aiVisible by remember { mutableStateOf(false) }
    var aiMounted by remember { mutableStateOf(false) }
    var aiConfigVisible by remember { mutableStateOf(false) }

    fun openCampusLife() {
        campusLifeMounted = true
        campusLifeVisible = true
    }

    fun openAi() {
        aiMounted = true
        aiVisible = true
    }

    fun openCalendar() {
        calendarMounted = true
        calendarVisible = true
    }

    LaunchedEffect(startOnToday) {
        if (startOnToday) pagerState.scrollToPage(Pages.TODAY)
    }

    TutorialHost(
        controller = tutorial,
        hasCourseData = courses.isNotEmpty(),
        onSeen = { viewModel.setTutorialSeen(true) }
    ) {
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            // containerColor 是 Transparent 时 contentColorFor() 返回 Unspecified，
            // 会把主题根节点的 LocalContentColor 覆盖掉，页面内默认文字又会变黑。
            contentColor = MaterialTheme.colorScheme.onBackground,
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { scaffoldPadding ->
            PageBackground(
                prefs = prefs,
                page = when (pagerState.currentPage) {
                    Pages.WEEK -> com.university.timetable.data.prefs.PageKeys.WEEK
                    Pages.SETTINGS -> com.university.timetable.data.prefs.PageKeys.SETTINGS
                    else -> com.university.timetable.data.prefs.PageKeys.TODAY
                }
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                    pageSpacing = 0.dp
                ) { page ->
                    val pagePadding = PaddingValues(
                        top = scaffoldPadding.calculateTopPadding(),
                        bottom = 96.dp
                    )
                    when (page) {
                        Pages.TODAY -> TodayScreen(
                            viewModel = viewModel,
                            state = todayState,
                            prefs = prefs,
                            contentPadding = pagePadding,
                            onCourseClick = { block -> actionsTarget = block },
                            onCourseLongClick = { block -> actionsTarget = block },
                            onAddClick = {
                                val day = todayState.date.dayOfWeek.value
                                editorTarget = EditorTarget.New(day, firstFreePeriod(todayState))
                            },
                            onImportClick = { importDialogVisible = true },
                            onOpenSettings = { settingsVisible = true },
                            onOpenPeriodEditor = { periodEditorVisible = true },
                            onOpenCampusLife = { openCampusLife() },
                            onOpenTodo = { todoVisible = true },
                            onOpenAi = { openAi() },
                            onOpenWidgetHelp = { widgetHelpVisible = true },
                            onOpenCalendar = { openCalendar() }
                        )

                        Pages.WEEK -> WeekScreen(
                            viewModel = viewModel,
                            state = weekState,
                            prefs = prefs,
                            contentPadding = pagePadding,
                            onCourseClick = { block -> actionsTarget = block },
                            onCourseLongClick = { block -> actionsTarget = block },
                            onAddClick = { editorTarget = EditorTarget.New(1, 1) },
                            onImportClick = { importDialogVisible = true },
                            onOpenSettings = { settingsVisible = true },
                            onOpenPeriodEditor = { periodEditorVisible = true },
                            onOpenQuickMenu = { weekMenuVisible = true },
                            onOpenAi = { openAi() },
                            onCreateAt = { day, period ->
                                editorTarget = EditorTarget.New(day, period)
                            }
                        )

                        else -> SettingsScreen(
                            viewModel = viewModel,
                            prefs = prefs,
                            contentPadding = pagePadding,
                            onOpenImport = { importDialogVisible = true },
                            onOpenPeriodEditor = { periodEditorVisible = true },
                            onOpenWidgetHelp = { widgetHelpVisible = true },
                            onOpenBackground = { backgroundVisible = true },
                            onOpenAi = { openAi() },
                            onPlayEgg = { easterEggVisible = true },
                            onOpenTutorial = { tutorial.start() },
                            onCheckUpdate = { updateViewModel.checkManually() },
                            updateChecking = updateState.checking
                        )
                    }
                }
            }
        }

        // 悬浮胶囊导航
        CapsuleTabRow(
            tabs = tabs,
            position = pagerState.currentPage + pagerState.currentPageOffsetFraction,
            onTabClick = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 22.dp)
                .padding(horizontal = 40.dp)
                .fillMaxWidth()
        )
    }

    // ---------------- 课程编辑 ----------------

    editorTarget?.let { target ->
        CourseEditorSheet(
            target = target,
            periods = todayState.periods,
            onDismiss = { editorTarget = null },
            onSave = { data ->
                viewModel.saveCourse(
                    existing = data.existing,
                    name = data.name,
                    teacher = data.teacher,
                    location = data.location,
                    color = data.color,
                    category = data.category,
                    dayOfWeek = data.dayOfWeek,
                    startPeriod = data.startPeriod,
                    endPeriod = data.endPeriod,
                    startWeek = data.startWeek,
                    endWeek = data.endWeek,
                    parity = data.parity
                )
                editorTarget = null
                scope.launch { snackbarHostState.showSnackbar("已保存「${data.name}」") }
            }
        )
    }

    actionsTarget?.let { block ->
        ClassActionsSheet(
            block = block,
            onDismiss = { actionsTarget = null },
            onEdit = {
                editorTarget = EditorTarget.Existing(block)
                actionsTarget = null
            },
            onDelete = {
                viewModel.deleteSession(block.sessionId)
                actionsTarget = null
                scope.launch { snackbarHostState.showSnackbar("已删除该时段") }
            },
            onClearDay = {
                viewModel.clearDay(block.dayOfWeek)
                actionsTarget = null
                scope.launch {
                    snackbarHostState.showSnackbar(
                        "已清空" +
                            com.university.timetable.util.SemesterWeeks.dayLabel(block.dayOfWeek) +
                            "全部课程"
                    )
                }
            }
        )
    }

    if (importDialogVisible) {
        ImportDialog(
            viewModel = viewModel,
            onDismiss = { importDialogVisible = false },
            onDone = { count ->
                importDialogVisible = false
                scope.launch { snackbarHostState.showSnackbar("已导入 $count 门课程") }
            }
        )
    }

    if (periodEditorVisible) {
        com.university.timetable.ui.screen.dialog.PeriodTimeEditor(
            periods = todayState.periods,
            onDismiss = { periodEditorVisible = false },
            onSave = { updated ->
                viewModel.savePeriodTimes(updated)
                periodEditorVisible = false
                scope.launch { snackbarHostState.showSnackbar("作息时间已更新") }
            }
        )
    }

    if (settingsVisible) {
        com.university.timetable.ui.screen.dialog.TimetableSettingsDialog(
            prefs = prefs,
            viewModel = viewModel,
            onDismiss = { settingsVisible = false }
        )
    }

    if (widgetHelpVisible) {
        com.university.timetable.ui.screen.dialog.WidgetHelpDialog(
            onDismiss = { widgetHelpVisible = false },
            viewModel = viewModel
        )
    }

    if (backgroundVisible) {
        com.university.timetable.ui.screen.dialog.BackgroundSettingsDialog(
            viewModel = viewModel,
            prefs = prefs,
            onDismiss = { backgroundVisible = false }
        )
    }

    if (weekMenuVisible) {
        com.university.timetable.ui.component.WeekQuickMenu(
            selectedWeek = weekState.selectedWeek,
            totalWeeks = prefs.totalWeeks,
            onSelectWeek = { viewModel.selectWeek(it) },
            onJumpToCurrentWeek = { viewModel.jumpToCurrentWeek() },
            simpleMode = prefs.simpleMode,
            darkTheme = darkTheme,
            onToggleSimpleMode = { viewModel.setSimpleMode(!prefs.simpleMode) },
            onToggleDarkTheme = { viewModel.setDarkTheme(!darkTheme) },
            onOpenPeriodEditor = {
                weekMenuVisible = false
                periodEditorVisible = true
            },
            onOpenSettings = {
                weekMenuVisible = false
                settingsVisible = true
            },
            onOpenAddedCourses = {
                weekMenuVisible = false
                addedCoursesVisible = true
            },
            onOpenMakeup = {
                weekMenuVisible = false
                makeupVisible = true
            },
            onOpenReminderSettings = {
                weekMenuVisible = false
                settingsVisible = true
            },
            onContact = {
                weekMenuVisible = false
                contactVisible = true
            },
            onDismiss = { weekMenuVisible = false }
        )
    }

    if (addedCoursesVisible) {
        com.university.timetable.ui.screen.dialog.AddedCoursesDialog(
            courses = courses,
            onDismiss = { addedCoursesVisible = false },
            onDeleteCourse = { viewModel.deleteCourse(it) }
        )
    }

    // ---------------- 快捷调休 ----------------
    if (makeupVisible) {
        com.university.timetable.ui.screen.dialog.QuickMakeupDialog(
            today = todayState.date,
            semesterStart = runCatching { java.time.LocalDate.parse(prefs.semesterStart) }
                .getOrDefault(todayState.date),
            totalWeeks = prefs.totalWeeks,
            scope = scope,
            onPlan = { from, to -> viewModel.planMakeup(from, to) },
            onConfirm = { from, to ->
                val result = viewModel.applyMakeup(from, to)
                scope.launch { snackbarHostState.showSnackbar(result.message) }
                result
            },
            onDismiss = { makeupVisible = false }
        )
    }

    // ---------------- 联系我们 ----------------
    if (contactVisible) {
        com.university.timetable.ui.screen.dialog.ContactDialog(
            onDismiss = { contactVisible = false }
        )
    }

    if (easterEggVisible) {
        com.university.timetable.ui.screen.dialog.EasterEggDialog(
            onDismiss = { easterEggVisible = false }
        )
    }

    // ---------------- 应用内更新（GitHub Releases） ----------------
    if (updateState.release != null || updateState.phase != com.university.timetable.update.UpdateUiState.Phase.Idle) {
        UpdateDialog(
            state = updateState,
            onDownload = { updateViewModel.startDownload() },
            onInstall = { updateViewModel.installNow() },
            onGrantPermission = { updateViewModel.openPermissionSettings() },
            onPostpone = { updateViewModel.postpone() },
            onDismiss = { updateViewModel.dismiss() }
        )
    }

    // ---------------- 首次启动的软件说明（只自动弹出一次） ----------------
    if (!prefs.introSeen) {
        com.university.timetable.ui.screen.dialog.IntroDialog(
            onDismiss = { viewModel.dismissIntro() }
        )
    }

    if (todoVisible) {
        com.university.timetable.ui.screen.dialog.TodoEditorDialog(
            today = todayState.date,
            todos = allTodos,
            onDismiss = { todoVisible = false },
            onSave = { existing, date, content, images ->
                viewModel.saveTodo(existing, date, content, images)
                scope.launch { snackbarHostState.showSnackbar("已保存待办：$content") }
            },
            onDelete = { id -> viewModel.deleteTodo(id) },
            onToggleDone = { item -> viewModel.toggleTodoDone(item) }
        )
    }

    // ---------------- 模型配置 ----------------
    // 注意：必须比 AI 助手子页面更靠上。
    // AI 助手是全屏覆盖层，而模型配置可以从 AI 助手内部打开，
    // 因此这里显式提升 zIndex，否则配置页会被 AI 页面盖住（表现为点了没反应）。
    if (aiConfigVisible) {
        val aiConfigs by aiViewModel.configs.collectAsStateWithLifecycle()
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f)
        ) {
            com.university.timetable.ui.ai.AiConfigScreen(
                viewModel = aiViewModel,
                configs = aiConfigs,
                activeId = aiConfigs.firstOrNull()?.id.orEmpty(),
                onBack = { aiConfigVisible = false }
            )
        }
    }

    // ---------------- AI 助手 ----------------

    if (aiMounted) {
        SubPageTransition(
            visible = aiVisible,
            style = prefs.transitionStyle,
            onExited = { aiMounted = false }
        ) {
            com.university.timetable.ui.ai.AiChatOverlay(
                viewModel = aiViewModel,
                onBack = { aiVisible = false },
                onOpenConfig = { aiConfigVisible = true },
                onStartLaundry = { minutes, label -> viewModel.startLaundryTimer(minutes, label) }
            )
        }
    }

    // ---------------- 日历 ----------------

    if (calendarMounted) {
        val holidayList by viewModel.holidays.collectAsStateWithLifecycle()
        SubPageTransition(
            visible = calendarVisible,
            style = prefs.transitionStyle,
            onExited = { calendarMounted = false }
        ) {
            FullScreenOverlay(onBack = { calendarVisible = false }) {
                com.university.timetable.ui.screen.CalendarPage(
                    onBack = { calendarVisible = false },
                    onOpenSettings = { calendarVisible = false; settingsVisible = true },
                    holidays = holidayList
                )
            }
        }
    }

    // ---------------- 校园生活 ----------------

    if (campusLifeMounted) {
        SubPageTransition(
            visible = campusLifeVisible,
            style = prefs.transitionStyle,
            onExited = { campusLifeMounted = false }
        ) {
            FullScreenOverlay(onBack = { campusLifeVisible = false }) {
                com.university.timetable.ui.screen.CampusLifePage(
                    viewModel = viewModel,
                    state = campusState,
                    onBack = { campusLifeVisible = false }
                )
            }
        }
    }
    }
}

/** 课程编辑目标：新增某个空位，或编辑已有课程。 */
sealed interface EditorTarget {
    data class New(val dayOfWeek: Int, val startPeriod: Int) : EditorTarget
    data class Existing(val block: com.university.timetable.data.repository.ClassBlock) : EditorTarget
}

/** 找出今天第一个空闲节次，作为「添加课程」的默认位置。 */
private fun firstFreePeriod(state: TodayUiState): Int {
    val occupied = state.blocks.flatMap { it.startPeriod..it.endPeriod }.toSet()
    val maxPeriod = state.periods.maxOfOrNull { it.period } ?: 12
    return (1..maxPeriod).firstOrNull { it !in occupied } ?: 1
}

/** 课程编辑保存的数据载体。 */
data class CourseEditorData(
    val existing: com.university.timetable.data.entity.Course?,
    val name: String,
    val teacher: String?,
    val location: String?,
    val color: Int?,
    val category: String,
    val dayOfWeek: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val startWeek: Int,
    val endWeek: Int,
    val parity: Int
)
