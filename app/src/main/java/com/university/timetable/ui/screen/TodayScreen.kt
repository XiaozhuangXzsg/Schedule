package com.university.timetable.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.university.timetable.data.entity.TodoItem
import com.university.timetable.data.entity.WeekParity
import com.university.timetable.data.prefs.PastCourseMode
import com.university.timetable.data.prefs.UiPreferences
import com.university.timetable.data.repository.ClassBlock
import com.university.timetable.ui.MainViewModel
import com.university.timetable.ui.TodayUiState
import com.university.timetable.ui.component.AppTopBar
import com.university.timetable.ui.component.EmptyState
import com.university.timetable.ui.component.LifeAssistantCard
import com.university.timetable.ui.component.SectionHeader
import com.university.timetable.ui.component.waveProgress
import com.university.timetable.ui.tutorial.TutorialAnchors
import com.university.timetable.ui.tutorial.tutorialAnchor
import com.university.timetable.ui.theme.CoursePalette
import com.university.timetable.ui.theme.LocalIsDark
import com.university.timetable.ui.theme.LocalTimetableColors
import com.university.timetable.ui.theme.desaturated
import com.university.timetable.util.SemesterWeeks
import kotlinx.coroutines.delay
import java.time.LocalTime

/**
 * 今日课表：只列今天的课程与时间地点，不再画整条时间轴。
 *
 * 每行显示：课时序号 / 起止时间 / 课程名 / 地点与教师 / 当前状态。
 */
@Composable
fun TodayScreen(
    viewModel: MainViewModel,
    state: TodayUiState,
    prefs: UiPreferences,
    contentPadding: PaddingValues,
    onCourseClick: (ClassBlock) -> Unit,
    onCourseLongClick: (ClassBlock) -> Unit,
    onAddClick: () -> Unit = {},
    onImportClick: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenPeriodEditor: () -> Unit = {},
    onOpenWidgetHelp: () -> Unit = {},
    onOpenCampusLife: () -> Unit = {},
    onOpenTodo: () -> Unit = {},
    onOpenAi: () -> Unit = {},
    onOpenCalendar: () -> Unit = {}
) {
    val dark = LocalIsDark.current
    val laundryTimers by viewModel.laundryTimers.collectAsStateWithLifecycle()
    val campusState by viewModel.campusState.collectAsStateWithLifecycle()
    val todayTodos by viewModel.todayTodos.collectAsStateWithLifecycle()

    // 每 30 秒刷新一次「进行中 / 已结束」状态
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            tick++
        }
    }
    val nowMinutes = remember(tick) {
        val t = LocalTime.now()
        t.hour * 60 + t.minute
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + 72.dp
            )
        ) {
            item {
                AppTopBar(
                    week = state.selectedWeek,
                    date = state.date,
                    onAdd = onAddClick,
                    onImport = onImportClick,
                    onPreviousWeek = { viewModel.stepWeek(-1) },
                    onNextWeek = { viewModel.stepWeek(1) },
                    onJumpToday = { viewModel.jumpToCurrentWeek() },
                    onOpenSettings = onOpenSettings,
                    onOpenPeriodEditor = onOpenPeriodEditor,
                    onOpenWidgetHelp = onOpenWidgetHelp,
                    onToggleSimpleMode = { viewModel.setSimpleMode(!prefs.simpleMode) },
                    simpleMode = prefs.simpleMode,
                    totalWeeks = prefs.totalWeeks,
                    selectedWeek = state.selectedWeek,
                    onSelectWeek = { viewModel.selectWeek(it) },
                    onOpenCalendar = onOpenCalendar,
                    darkTheme = dark,
                    onToggleDarkTheme = { viewModel.setDarkTheme(!dark) },
                    onOpenTodo = onOpenTodo,
                    onOpenAi = onOpenAi,
                    todoCount = todayTodos.count { !it.done }
                )
            }

            // 放假 / 调休提示
            state.holiday?.let { holiday ->
                item {
                    HolidayBanner(
                        name = holiday.name,
                        isMakeup = state.isMakeupDay,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                    )
                }
            }

            item {
                TodaySummary(state = state, nowMinutes = nowMinutes)
            }

            item {
                SectionHeader(
                    title = "今日课程",
                    subtitle = if (state.hasClass) "${state.blocks.size} 节" else null,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }

            if (!state.hasClass) {
                item {
                    EmptyState(
                        title = if (state.isMakeupDay) {
                            "今天按${SemesterWeeks.dayLabel(state.date.dayOfWeek.value)}上课"
                        } else {
                            "今天没有课"
                        },
                        subtitle = state.tomorrowFirst?.let {
                            "明天 ${it.startTime} ${it.name} · ${it.location ?: "地点待定"}"
                        } ?: "好好休息一下～",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
                    )
                }
            } else {
                // 已上完的课：按设置置灰或隐藏
                val visibleBlocks = if (prefs.pastCourseMode == PastCourseMode.HIDE) {
                    state.blocks.filter { it.endMinutes >= nowMinutes }
                } else {
                    state.blocks
                }
                itemsIndexed(visibleBlocks, nowMinutes) { index, block ->
                    CourseListRow(
                        index = index + 1,
                        block = block,
                        prefs = prefs,
                        dark = dark,
                        nowMinutes = nowMinutes,
                        onClick = { onCourseClick(block) },
                        onLongClick = { onCourseLongClick(block) }
                    )
                }
            }

            // 今日待办
            item {
                SectionHeader(
                    title = "今日待办",
                    subtitle = if (todayTodos.isEmpty()) null else "${todayTodos.count { !it.done }} 项待完成",
                    trailing = {
                        Text(
                            text = "编辑",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onOpenTodo)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }

            item {
                TodoSummaryCard(
                    todos = todayTodos,
                    onOpen = onOpenTodo,
                    onToggle = { viewModel.toggleTodoDone(it) },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            if (prefs.showLifeAssistant) {
                item {
                    Spacer(Modifier.height(14.dp))
                    LifeAssistantCard(
                        statuses = campusState.statuses,
                        timers = laundryTimers,
                        nowMinutes = campusState.nowMinutes,
                        onOpenCampusLife = onOpenCampusLife,
                        onStartTimer = { minutes, label -> viewModel.startLaundryTimer(minutes, label) },
                        onCancelTimer = { id -> viewModel.cancelLaundryTimer(id) },
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** LazyColumn 的 itemsIndexed 简写（避免引入额外参数）。 */
private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexed(
    blocks: List<ClassBlock>,
    nowMinutes: Int,
    content: @Composable (Int, ClassBlock) -> Unit
) {
    items(blocks.size) { index ->
        content(index, blocks[index])
    }
}

/** 日期摘要 + 下一节课提示。 */
@Composable
private fun TodaySummary(state: TodayUiState, nowMinutes: Int) {
    val colors = LocalTimetableColors.current
    val next = state.nowBlock ?: state.nextBlock

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceCard)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = state.date.dayOfMonth.toString(),
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = SemesterWeeks.dayLabel(state.date.dayOfWeek.value),
                style = MaterialTheme.typography.labelSmall,
                color = colors.mutedText
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "第 ${state.week} 周 · ${state.date.monthValue} 月",
                style = MaterialTheme.typography.labelMedium,
                color = colors.mutedText
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = when {
                    !state.hasClass -> "今天没有课"
                    state.nowBlock != null -> "正在上：${state.nowBlock.name}"
                    next != null -> "下一节 ${next.startTime} ${next.name}"
                    else -> "今日课程已全部结束"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (next != null && state.nowBlock != null) {
                Text(
                    text = "接下来 ${next.startTime} ${next.name}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * 今日课程行：左侧课程序号与起止时间，右侧课程信息。
 * 正在上的课带主色描边，已结束的**置灰**（保留亮度、抽掉色相）。
 */
@Composable
private fun CourseListRow(
    index: Int,
    block: ClassBlock,
    prefs: UiPreferences,
    dark: Boolean,
    nowMinutes: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val (courseContainer, courseContent) = CoursePalette.colorsFor(block.courseId, block.color, dark)
    val colors = LocalTimetableColors.current
    val isNow = nowMinutes in block.startMinutes until block.endMinutes
    val isPast = block.endMinutes < nowMinutes

    // 已结束的课置灰：抽掉色相但保留亮度，于是仍看得清，又一眼能看出这门课上完了。
    val container = if (isPast) courseContainer.desaturated(0.9f) else courseContainer
    val content = if (isPast) courseContent.desaturated(0.78f) else courseContent

    // 正在上的这节课已过去的比例：真实时间算出来的，切页面回来也一定正确。
    val span = (block.endMinutes - block.startMinutes).coerceAtLeast(1)
    val nowProgressFraction = if (isNow) {
        ((nowMinutes - block.startMinutes).toFloat() / span).coerceIn(0f, 1f)
    } else {
        0f
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(container)
            .waveProgress(
                progress = if (prefs.nowProgress) nowProgressFraction else 0f,
                color = content,
                style = prefs.nowProgressStyle
            )
            .then(
                // 只让第一张课程卡上报锚点，否则多张卡互相覆盖，高亮位置会随机跳。
                if (index == 1) {
                    Modifier.tutorialAnchor(TutorialAnchors.COURSE_LONG_PRESS)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        // 左侧时间列
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(52.dp)
        ) {
            Text(
                text = block.startTime,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = content
            )
            Text(
                text = block.endTime,
                style = MaterialTheme.typography.labelSmall,
                color = content.copy(alpha = 0.72f)
            )
            if (isNow) {
                Spacer(Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "进行中",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        // 分隔竖线
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(38.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(content.copy(alpha = 0.85f))
        )

        Spacer(Modifier.width(12.dp))

        // 课程信息
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = block.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (prefs.showWeekTag && block.weekParity != WeekParity.EVERY) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(content.copy(alpha = 0.16f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = WeekParity.label(block.weekParity),
                            fontSize = 9.sp,
                            color = content
                        )
                    }
                }
            }

            val subLine = buildList {
                if (prefs.showLocation) block.location?.takeIf { it.isNotBlank() }?.let { add(it) }
                if (prefs.showTeacher) block.teacher?.takeIf { it.isNotBlank() }?.let { add(it) }
            }.joinToString(" · ")

            if (subLine.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (prefs.showLocation && !block.location.isNullOrBlank()) {
                        Icon(
                            Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = content.copy(alpha = 0.7f),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                    }
                    Text(
                        text = subLine,
                        style = MaterialTheme.typography.bodyMedium,
                        color = content.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        // 右侧课序 / 状态
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "第${index}节",
                style = MaterialTheme.typography.labelSmall,
                color = colors.mutedText
            )
            if (isPast) {
                Text(
                    text = "已结束",
                    fontSize = 9.sp,
                    color = colors.mutedText
                )
            }
        }
    }
}

/**
 * 今日待办摘要卡：显示内容与图片数，可勾选完成。
 * 没有待办时给出引导，点击进入编辑。
 */
@Composable
private fun TodoSummaryCard(
    todos: List<TodoItem>,
    onOpen: () -> Unit,
    onToggle: (TodoItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTimetableColors.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceCard)
            .padding(14.dp)
    ) {
        if (todos.isEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpen)
                    .padding(vertical = 4.dp)
            ) {
                Icon(
                    Icons.Filled.EditNote,
                    contentDescription = null,
                    tint = colors.mutedText,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "今天还没有待办，点击添加备忘事项",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.mutedText,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    tint = colors.mutedText,
                    modifier = Modifier.size(13.dp)
                )
            }
        } else {
            todos.sortedWith(compareBy({ it.done }, { it.createdAt })).forEachIndexed { index, item ->
                if (index > 0) Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(
                                if (item.done) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { onToggle(item) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (item.done) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "已完成",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = item.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (item.done) colors.mutedText
                        else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onOpen)
                    )
                    if (item.images.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Image,
                                contentDescription = null,
                                tint = colors.mutedText,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = item.images.size.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.mutedText
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HolidayBanner(name: String, isMakeup: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalTimetableColors.current
    val container = if (isMakeup) colors.makeupContainer else colors.holidayContainer
    val content = if (isMakeup) colors.onMakeupContainer else colors.onHolidayContainer
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(container)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = if (isMakeup) "调休补课" else "放假",
            style = MaterialTheme.typography.labelMedium,
            color = content,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = if (isMakeup) "$name（按调休课表上课）" else name,
            style = MaterialTheme.typography.bodyMedium,
            color = content
        )
    }
}
