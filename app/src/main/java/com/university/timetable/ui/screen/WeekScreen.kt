package com.university.timetable.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.university.timetable.data.entity.PeriodTime
import com.university.timetable.data.entity.WeekParity
import com.university.timetable.data.entity.endMinutes
import com.university.timetable.data.entity.startMinutes
import com.university.timetable.data.prefs.NowLineStyle
import com.university.timetable.data.prefs.UiPreferences
import com.university.timetable.data.repository.ClassBlock
import com.university.timetable.ui.MainViewModel
import com.university.timetable.ui.WeekSlice
import com.university.timetable.ui.WeekUiState
import com.university.timetable.ui.component.AppTopBar
import com.university.timetable.ui.component.CourseCard
import com.university.timetable.ui.theme.LocalIsDark
import com.university.timetable.ui.theme.LocalTimetableColors
import com.university.timetable.util.SemesterWeeks
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val TimeColumnWidth = 50.dp

/**
 * 时间轴上的时间标签，上下两行分别显示开始与结束时间，例如：
 * ```
 * 08:30
 * 09:15
 * ```
 * 不再使用连字符把两个时间挤在一行。
 *
 * 解析失败时退回单点时间，避免出现空标签。
 */
internal fun PeriodTime.rangeLabel(): String {
    val start = startTime.trim()
    val end = endTime.trim()
    return if (start.isNotEmpty() && end.isNotEmpty()) "$start\n$end" else start.ifEmpty { end }
}
private val DayHeaderHeight = 52.dp

/** 时间轴两侧与网格保持一致的右侧留白，保证纵向滚动条区域不占宽度。 */
private val GridEndPadding = 6.dp

/**
 * 网格底部留白：避免最后一节贴着提示文字，也让滚动到底有舒服的余量。
 *
 * 行高、时间轴位置的纯计算都在 `WeekGridMetrics.kt`（同一包，internal 可见），
 * 那边不依赖 Compose 运行时，可以直接被单元测试覆盖。
 */
private val GridBottomPadding = 72.dp

/**
 * 周视图的「当前时间轴条」：**只在今天那一列里**画一条线 + 左端圆球，模仿 iOS 日历的时间指示。
 *
 * 三点约定：
 * - 线不横贯整个星期，宽度由调用方按「今天那一列」算好后传进来（[WeekNowLineMetrics]）；
 * - 左端圆球压在课程卡左侧色条的**中线**上，看起来就是色条那一列的顶端封口；
 * - **圆球左边没有线**：整条线的左端就是圆球的左边缘。
 *
 * 颜色默认跟随主题的 [com.university.timetable.ui.theme.TimetableColors.nowIndicator]（正红），
 * 也可以在个性化里指定固定颜色或换成虚线/加粗样式。
 *
 * @param startX 今天那一列左边界的横坐标（相对本组件所在的父 Box）
 * @param columnWidth 今天那一列的宽度；线的右端就落在它的右边缘
 */
@Composable
private fun WeekNowLine(
    offsetY: Dp,
    startX: Dp,
    columnWidth: Dp,
    style: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    val thickness = when (style) {
        NowLineStyle.BOLD -> 3.dp
        else -> 1.6.dp
    }
    // 圆球直径比课程卡左侧色条略宽（见 WeekNowLineMetrics.BallSize），
    // 但圆心仍压在色条中线上；没有圆球的细线样式由调用方按 0 处理。
    val dotSize = when (style) {
        NowLineStyle.PLAIN -> 0.dp
        else -> WeekNowLineMetrics.BallSize
    }
    val dashed = style == NowLineStyle.DASHED

    // 整条线从左端（= 圆球左边缘）拉到日列右边缘；圆球左边不再露出一小截线。
    val lineStartX = startX + WeekNowLineMetrics.lineStartFromDayStart(dotSize)
    val lineWidth = WeekNowLineMetrics.lineWidthFromDayStart(columnWidth, dotSize)
    val lineHeight = dotSize.coerceAtLeast(thickness)

    Box(
        modifier = modifier
            // 让线（以及圆球）的**中线**正好落在 offsetY 上。
            // 以前是让这段高度的上边缘贴住 offsetY，于是球越大、线就被压得越往下
            // （7dp 球时偏 3.5dp，改成 5dp 球仍偏 2.5dp）——「现在」那条线理应在现在这个点上。
            .offset(x = lineStartX, y = offsetY - lineHeight / 2)
            .width(lineWidth)
            .height(lineHeight),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(thickness)
                .drawBehind {
                    val y = size.height / 2f
                    if (dashed) {
                        drawLine(
                            color = color,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = size.height,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f))
                        )
                    } else {
                        drawRect(color = color)
                    }
                }
        )
        if (dotSize > 0.dp) {
            Box(
                modifier = Modifier
                    // 不额外偏移：圆球的左边缘就贴在线的起点上，所以球左边不会先冒出一截线。
                    .size(dotSize)
                    .background(color, CircleShape)
            )
        }
    }
}

@Composable
fun WeekScreen(
    viewModel: MainViewModel,
    state: WeekUiState,
    prefs: UiPreferences,
    contentPadding: PaddingValues,
    onCourseClick: (ClassBlock) -> Unit,
    onCourseLongClick: (ClassBlock) -> Unit,
    onAddClick: () -> Unit = {},
    onImportClick: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenPeriodEditor: () -> Unit = {},
    onOpenWidgetHelp: () -> Unit = {},
    onOpenQuickMenu: () -> Unit = {},
    onOpenAi: () -> Unit = {},
    /** 长按空白格：在该位置新增课程 */
    onCreateAt: (dayOfWeek: Int, period: Int) -> Unit = { _, _ -> }
) {
    val colors = LocalTimetableColors.current
    val dark = LocalIsDark.current

    val visibleDays = if (prefs.showWeekend) (1..7).toList() else (1..5).toList()
    val baseRowHeight = if (prefs.simpleMode) 56.dp else 64.dp
    var clearDayConfirm by remember { mutableStateOf<Int?>(null) }

    // 当前时间（分钟）：每 30 秒走一格。
    // 必须用会自己推进的状态：只在进入页面时读一次的话，
    // 页面停留期间时间轴条会一直钉在打开那一刻的位置上。
    val nowMinutes by produceState(initialValue = currentMinutesOfDay()) {
        while (true) {
            kotlinx.coroutines.delay(30_000L)
            value = currentMinutesOfDay()
        }
    }

    // 当前节次（仅本周高亮）
    val nowPeriod = if (state.selectedWeek != state.currentWeek) {
        null
    } else {
        state.periods.firstOrNull { nowMinutes in it.startMinutes until it.endMinutes }?.period
    }

    // 各节高度（轴与网格共用，保证任何滚动位置都严格对齐）
    val rowHeights = remember(state.periods, baseRowHeight) {
        state.periods.map { rowHeightFor(it, baseRowHeight) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding())
    ) {
        AppTopBar(
            week = state.selectedWeek,
            date = state.today,
            onAdd = onAddClick,
            onImport = onImportClick,
            onPreviousWeek = { viewModel.stepWeek(-1) },
            onNextWeek = { viewModel.stepWeek(1) },
            onJumpToday = { viewModel.jumpToCurrentWeek() },
            onOpenSettings = onOpenSettings,
            onOpenPeriodEditor = onOpenPeriodEditor,
            onToggleSimpleMode = { viewModel.setSimpleMode(!prefs.simpleMode) },
            simpleMode = prefs.simpleMode,
            totalWeeks = prefs.totalWeeks,
            selectedWeek = state.selectedWeek,
            onSelectWeek = { viewModel.selectWeek(it) },
            darkTheme = dark,
            onToggleDarkTheme = { viewModel.setDarkTheme(!dark) },
            onOpenAi = onOpenAi,
            onOpenMenu = onOpenQuickMenu
        )

        Spacer(Modifier.height(6.dp))

        SwipeableWeekGrid(
            state = state,
            prefs = prefs,
            dark = dark,
            visibleDays = visibleDays,
            periods = state.periods,
            rowHeights = rowHeights,
            nowPeriod = nowPeriod,
            nowMinutes = nowMinutes,
            onSwipeWeek = { delta -> viewModel.stepWeek(delta) },
            onCourseClick = onCourseClick,
            onCourseLongClick = onCourseLongClick,
            onCreateAt = onCreateAt,
            onEditAxis = onOpenPeriodEditor,
            modifier = Modifier.weight(1f)
        )

        // 操作提示
        WeekHint(hasCourse = state.blocks.isNotEmpty(), color = colors.mutedText)
    }

    clearDayConfirm?.let { day ->
        AlertDialog(
            onDismissRequest = { clearDayConfirm = null },
            title = { Text("清空${SemesterWeeks.dayLabel(day)}的课程？") },
            text = { Text("将删除该日所有上课时段，且不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearDay(day)
                    clearDayConfirm = null
                }) { Text("清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { clearDayConfirm = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun WeekHint(hasCourse: Boolean, color: Color) {
    Text(
        text = if (hasCourse) {
            "左右滑动切换周次 · 长按空白格添加课程 · 长按课程可编辑 · 长按左侧时间轴改作息"
        } else {
            "长按任意空白格添加课程 · 长按左侧时间轴编辑作息"
        },
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

@Composable
private fun DayHeader(
    dayOfWeek: Int,
    date: LocalDate,
    isToday: Boolean,
    isWeekend: Boolean,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTimetableColors.current
    Column(
        modifier = modifier
            .height(DayHeaderHeight)
            .clickable(onClick = onLongClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = SemesterWeeks.dayChar(dayOfWeek),
            style = MaterialTheme.typography.labelMedium,
            color = when {
                isToday -> MaterialTheme.colorScheme.primary
                isWeekend -> colors.mutedText.copy(alpha = 0.7f)
                else -> colors.mutedText
            },
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium
        )
        Spacer(Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                fontSize = 12.sp,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = if (isToday) MaterialTheme.colorScheme.onPrimary else colors.mutedText
            )
        }
    }
}

/**
 * 周网格。
 *
 * 结构要点（历次问题的根因都在这三件事上）：
 * 1. **左侧时间轴固定在 [HorizontalPager] 之外**——左右滑动切周时只让右侧的
 *    日期栏与课程网格水平滑动，时间轴保持不动；
 * 2. 时间轴与网格共用**同一个 `ScrollState`**，两者的内容总高度均等于
 *    「各行高度之和 + 相同底部留白」，因此最大滚动量一致，
 *    滚动到顶部/底部边界也不会出现一先一后停住的错位；
 * 3. 每一行的纵坐标由同一份 `rowHeights` 累加得到（轴用 Column + height，
 *    网格用 Box + offset），不存在两套布局各自计算导致的漂移。
 */
@Composable
private fun SwipeableWeekGrid(
    state: WeekUiState,
    prefs: UiPreferences,
    dark: Boolean,
    visibleDays: List<Int>,
    periods: List<PeriodTime>,
    rowHeights: List<Dp>,
    nowPeriod: Int?,
    nowMinutes: Int,
    onSwipeWeek: (Int) -> Unit,
    onCourseClick: (ClassBlock) -> Unit,
    onCourseLongClick: (ClassBlock) -> Unit,
    onCreateAt: (dayOfWeek: Int, period: Int) -> Unit,
    onEditAxis: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 单一纵向滚动状态：时间轴与所有周页面共享，纵向滚动永远同步
    val gridScroll = rememberScrollState()

    val maxWeek = state.totalWeeks.coerceAtLeast(1)
    // 页索引 0 对应第 1 周，便于用 clamp 处理首尾边界
    val pagerState = rememberPagerState(initialPage = 0) { maxWeek }

    // 外部切周（顶栏箭头 / 三点菜单 / 回到本周）时同步分页
    var syncingFromState by remember { mutableStateOf(false) }
    LaunchedEffect(state.selectedWeek, maxWeek) {
        val target = (state.selectedWeek - 1).coerceIn(0, maxWeek - 1)
        if (pagerState.currentPage != target) {
            syncingFromState = true
            pagerState.animateScrollToPage(target)
            syncingFromState = false
        }
    }

    // 分页停稳后把结果写回 ViewModel。
    // 直接让 Pager 自己处理手势（userScrollEnabled = true）：
    // 拖动过程中页面跟手移动，松手后自动吸附到整页，因此既连续又不会闪烁。
    val settledPage by remember { derivedStateOf<Int> { pagerState.settledPage } }
    LaunchedEffect(settledPage) {
        if (syncingFromState) return@LaunchedEffect
        val week = settledPage + 1
        if (week != state.selectedWeek) onSwipeWeek(if (week > state.selectedWeek) 1 else -1)
    }

    /** 取某一周的切片：当前周直接用 state 里的数据，前后周用预热好的切片。 */
    fun sliceOf(week: Int): WeekSlice = when (week) {
        state.selectedWeek -> WeekSlice(week, state.dates, state.blocks)
        state.previous?.week -> state.previous
        state.next?.week -> state.next
        else -> WeekSlice(week = week)
    }

    /** 该周的星期几对应的日期；缺失时退回今天，避免空标题。 */
    fun dateOf(week: Int, day: Int): LocalDate =
        sliceOf(week).dates.getOrNull(day - 1) ?: state.today

    Column(modifier = modifier.fillMaxSize()) {
        // ---- 日期栏 ----
        // 这里刻意不用第二个 HorizontalPager：
        // 两个 Pager 共用同一个 PagerState 时，第二个 Pager 的 LazyLayout 只会组合
        // 初始页（page 0/1），拿不到 animateScrollToPage 之后的数据，
        // 结果 7 个日期格全部回退成「今天」（曾表现为一整排 26）。
        // 改为用 currentPage 推导当前周，用普通 Row 渲染，彻底消除该风险。
        // （currentPage 在拖动过半时切换，视觉上只是日期文字换一下，位置仍由网格承担。）
        val headerWeek = pagerState.currentPage + 1
        Row(modifier = Modifier.fillMaxWidth().height(DayHeaderHeight)) {
            // 与左侧时间轴等宽的占位，保证日期列与下方网格列严格对齐
            Spacer(Modifier.width(TimeColumnWidth))
            visibleDays.forEach { day ->
                val date = dateOf(headerWeek, day)
                DayHeader(
                    dayOfWeek = day,
                    date = date,
                    isToday = date == state.today,
                    isWeekend = day >= 6,
                    onLongClick = { },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        HorizontalDivider(color = LocalTimetableColors.current.gridLine)

        // ---- 时间轴（固定） + 网格（可横向滑动） ----
        // 用 BoxWithConstraints 是为了拿到网格区宽度：当前时间轴条要按「今天那一列」的
        // 左右边界来画（列宽 = 网格区宽度 / 显示列数，与 WeekDayGrid 里的算法一致）。
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
            val gridWidth = (maxWidth - GridEndPadding - TimeColumnWidth).coerceAtLeast(0.dp)
            Row(modifier = Modifier.fillMaxSize().padding(end = GridEndPadding)) {
                // 左侧时间轴：固定在 Pager 外面，切周时纹丝不动。
                // 与网格共用同一个 scrollState，纵向前进与分隔线完全同步。
                TimeAxisColumn(
                    periods = periods,
                    rowHeights = rowHeights,
                    scrollState = gridScroll,
                    nowPeriod = nowPeriod,
                    onEditAxis = onEditAxis
                )

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    beyondViewportPageCount = 1,
                    pageSpacing = 0.dp
                ) { page ->
                    val week = page + 1
                    WeekDayGrid(
                        slice = sliceOf(week),
                        isSelected = week == state.selectedWeek,
                        prefs = prefs,
                        dark = dark,
                        visibleDays = visibleDays,
                        periods = periods,
                        rowHeights = rowHeights,
                        scrollState = gridScroll,
                        nowPeriod = if (week == state.selectedWeek) nowPeriod else null,
                        today = state.today,
                        onCourseClick = onCourseClick,
                        onCourseLongClick = onCourseLongClick,
                        onCreateAt = onCreateAt
                    )
                }
            }

            // ---- 当前时间轴条 ----
            // 只有正在看「本周」时才画：翻到别的周时它没有意义，还会误导。
            // 纵坐标 = 内容坐标 - 纵向滚动偏移，让它跟着网格一起滚。
            // 横坐标只覆盖「今天」那一列；今天不在显示范围里（例如隐藏了周末又正好是周末）就不画。
            if (prefs.weekNowLine && state.selectedWeek == state.currentWeek && periods.isNotEmpty()) {
                val density = LocalDensity.current
                val scrollDp = with(density) { gridScroll.value.toDp() }
                val contentOffset = nowLineOffset(periods, rowHeights, nowMinutes)
                // 今天在第几列。刻意直接用本周的 dates 而不是 dateOf()：
                // dateOf 在数据没加载出来时会退回「今天」，那会让 7 天全都等于今天、
                // 红条错误地画到第 0 列上。这里 dates 里找不到就返回 -1（不画）。
                val todayDateIndex = sliceOf(state.currentWeek).dates.indexOfFirst { it == state.today }
                val todayIndex =
                    if (todayDateIndex >= 0) visibleDays.indexOf(todayDateIndex + 1) else -1
                if (todayIndex >= 0) {
                    val columns = visibleDays.size
                    val columnWidth = WeekNowLineMetrics.dayWidth(gridWidth, columns)
                    val columnStart = TimeColumnWidth +
                        WeekNowLineMetrics.dayStart(todayIndex, gridWidth, columns)
                    WeekNowLine(
                        offsetY = contentOffset - scrollDp,
                        startX = columnStart,
                        columnWidth = columnWidth,
                        style = prefs.weekNowLineStyle,
                        color = if (prefs.weekNowLineColor != UiPreferences.UNSET) {
                            Color(prefs.weekNowLineColor)
                        } else {
                            LocalTimetableColors.current.nowIndicator
                        }
                    )
                }
            }
        }
    }
}

/**
 * 左侧固定的时间轴。
 *
 * 只有内容随 [scrollState] 纵向滚动；水平方向不属于任何 Pager，因此切周时保持固定。
 * 每一行的高度来自与网格同一份 [rowHeights]，行末的分隔线与日列贯通。
 */
@Composable
private fun TimeAxisColumn(
    periods: List<PeriodTime>,
    rowHeights: List<Dp>,
    scrollState: androidx.compose.foundation.ScrollState,
    nowPeriod: Int?,
    onEditAxis: () -> Unit
) {
    val colors = LocalTimetableColors.current

    Column(
        modifier = Modifier
            .width(TimeColumnWidth)
            .fillMaxHeight()
            .verticalScroll(scrollState)
            .clipToBounds()
    ) {
        periods.forEachIndexed { index, period ->
            val isCurrent = period.period == nowPeriod
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeights.getOrElse(index) { MinRowHeight })
                    // 长按时间轴进入作息编辑
                    .pointerInput(period.period) {
                        detectTapGestures(onLongPress = { onEditAxis() })
                    }
            ) {
                Spacer(Modifier.weight(1f))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = period.period.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else colors.mutedText
                    )
                    // 开始时间与结束时间分两行显示，不再用连字符挤在一行
                    Text(
                        text = period.rangeLabel(),
                        fontSize = 7.5.sp,
                        lineHeight = 9.sp,
                        maxLines = 2,
                        softWrap = false,
                        textAlign = TextAlign.Center,
                        color = colors.mutedText.copy(alpha = 0.8f)
                    )
                }
                Spacer(Modifier.weight(1f))
                // 与右侧日列同一条横向分隔线，保证左右贯通
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.8.dp)
                        .background(
                            if (isCurrent) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                            } else {
                                colors.gridLine
                            }
                        )
                )
            }
        }
        Spacer(Modifier.height(GridBottomPadding))
    }
}

/**
 * 单周的课程网格（不含时间轴，也不含日期栏）。
 *
 * 时间轴已在 [SwipeableWeekGrid] 中固定，因此这里只负责日期列与课程卡片；
 * 纵向与时间轴共用 [scrollState]，横向由外层的 Pager 负责。
 */
@Composable
private fun WeekDayGrid(
    slice: WeekSlice,
    isSelected: Boolean,
    prefs: UiPreferences,
    dark: Boolean,
    visibleDays: List<Int>,
    periods: List<PeriodTime>,
    rowHeights: List<Dp>,
    scrollState: androidx.compose.foundation.ScrollState,
    nowPeriod: Int?,
    today: LocalDate,
    onCourseClick: (ClassBlock) -> Unit,
    onCourseLongClick: (ClassBlock) -> Unit,
    onCreateAt: (dayOfWeek: Int, period: Int) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .clipToBounds()
    ) {
        val dayWidth = maxWidth / visibleDays.size

        Column {
            Row {
                visibleDays.forEach { day ->
                    DayColumn(
                        day = day,
                        blocks = slice.blocksOf(day),
                        periods = periods,
                        rowHeights = rowHeights,
                        isToday = isSelected && slice.dates.getOrNull(day - 1) == today,
                        nowPeriod = nowPeriod,
                        prefs = prefs,
                        dark = dark,
                        width = dayWidth,
                        onCreateAt = onCreateAt,
                        onCourseClick = onCourseClick,
                        onCourseLongClick = onCourseLongClick
                    )
                }
            }
            Spacer(Modifier.height(GridBottomPadding))
        }
    }
}

/**
 * 单日列：背景横向分隔线 + 按跨节数绝对定位的课程卡片。
 *
 * 所有行的 `top` 由 [rowHeights] 累加得到，与左侧时间轴同源。
 */
@Composable
private fun DayColumn(
    day: Int,
    blocks: List<ClassBlock>,
    periods: List<PeriodTime>,
    rowHeights: List<Dp>,
    isToday: Boolean,
    nowPeriod: Int?,
    prefs: UiPreferences,
    dark: Boolean,
    width: Dp,
    onCreateAt: (dayOfWeek: Int, period: Int) -> Unit,
    onCourseClick: (ClassBlock) -> Unit,
    onCourseLongClick: (ClassBlock) -> Unit
) {
    val colors = LocalTimetableColors.current
    val totalHeight = rowHeights.fold(0.dp) { acc, h -> acc + h }

    // 每行的起始纵坐标（与左侧时间轴完全一致）
    val tops = remember(rowHeights) {
        val list = ArrayList<Dp>(rowHeights.size)
        var acc = 0.dp
        rowHeights.forEach { h ->
            list.add(acc)
            acc += h
        }
        list
    }

    Box(modifier = Modifier.width(width).height(totalHeight)) {
        // 今天所在列：整列一层的淡色底
        if (isToday) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
            )
        }

        // 背景网格：只画横向分隔线
        Column(modifier = Modifier.fillMaxSize()) {
            periods.forEachIndexed { index, period ->
                val isCurrent = period.period == nowPeriod
                val occupied = blocks.any {
                    period.period >= it.startPeriod && period.period <= it.endPeriod
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeights.getOrElse(index) { MinRowHeight })
                        // 长按空白格：在该天该节次新增课程
                        .pointerInput(day, period.period) {
                            detectTapGestures(
                                onLongPress = {
                                    if (!occupied) onCreateAt(day, period.period)
                                }
                            )
                        }
                ) {
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.8.dp)
                            .background(
                                if (isToday && isCurrent) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                } else {
                                    colors.gridLine
                                }
                            )
                    )
                }
            }
        }

        // 课程卡片（跨节），位置与左侧时间轴同源
        blocks.forEach { block ->
            val index = periods.indexOfFirst { it.period == block.startPeriod }
            if (index < 0) return@forEach
            val span = (block.endPeriod - block.startPeriod).coerceAtLeast(0)
            val top = tops.getOrElse(index) { 0.dp }
            val measured = rowHeights.drop(index).take(span + 1).fold(0.dp) { a, b -> a + b }
            val height = (measured - 4.dp).coerceAtLeast(MinRowHeight - 6.dp)
            CourseCard(
                block = block,
                prefs = prefs,
                dark = dark,
                compact = true,
                isNow = block.startPeriod == nowPeriod && isToday,
                onClick = { onCourseClick(block) },
                onLongClick = { onCourseLongClick(block) },
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = top + 2.dp)
                    .height(height)
                    // 与 WeekNowLineMetrics.CardInset 同源：时间轴条圆球要靠这个留白算色条位置
                    .padding(horizontal = WeekNowLineMetrics.CardInset)
            )
        }
    }
}

/** 一节课在卡片上显示的信息行（周次 / 教师）。 */
internal fun infoLineOf(block: ClassBlock, prefs: UiPreferences): String = buildList {
    if (prefs.showWeekTag && block.weekParity != 0) {
        add(WeekParity.label(block.weekParity).removePrefix("周").let { "第${it}周" })
    }
    if (prefs.showTeacher && !block.teacher.isNullOrBlank()) add(block.teacher.orEmpty())
}.joinToString(" · ")
