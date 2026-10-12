package com.university.timetable.ui.screen

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.university.timetable.data.calendar.CalendarEvent
import com.university.timetable.data.calendar.hasPermission
import com.university.timetable.data.calendar.queryEvents
import com.university.timetable.data.entity.Holiday
import com.university.timetable.ui.theme.LocalTimetableColors
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 日历子页面：读取并展示本机系统日历（CalendarContract.Instances）的日程。
 *
 * 交互：
 *  - 未授权时展示说明卡片 + 「允许读取日历」按钮，并提示也可去系统设置授权；
 *  - 已授权时展示 6×7 月视图，有日程的日期带小圆点，节日/调休在日期下方标出名称；
 *  - **上下滑动切换月份**：向上滑进入下一个月，向下滑返回上一个月，
 *    月份标题与网格内容一起更新；也可用标题两侧的箭头按钮切换；
 *  - 选中日期的日程按开始时间排序列在下方，显示时间段、标题、地点与所属日历。
 *
 * 只读：本页不会写入或修改系统日历。
 */
@Composable
fun CalendarPage(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {},
    /** 应用内的节假日 / 调休安排，用于在日期上标注节日名称。 */
    holidays: List<Holiday> = emptyList()
) {
    val context = LocalContext.current
    val colors = LocalTimetableColors.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now() }

    var granted by remember { mutableStateOf(hasPermission(context)) }
    // 月份改用 VerticalPager 承载：拖动过程中相邻月份会连续跟随手指，
    // 松手后按速度/位置就近吸附，实现「上下平滑连贯翻阅」。
    // pagerState 的 page 是相对 CALENDAR_BASE_MONTH 的偏移量。
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = monthIndexOf(YearMonth.from(today))
    ) { CALENDAR_TOTAL_MONTHS }
    val visibleMonth = monthFromIndex(pagerState.currentPage)
    var selectedDate by remember { mutableStateOf(today) }
    var events by remember { mutableStateOf<List<CalendarEvent>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    // 翻页停稳后，把选中日期收进当前月份（避免停留在不存在的 31 号）
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val month = monthFromIndex(page)
            if (YearMonth.from(selectedDate) != month) {
                selectedDate = selectedDate.coerceInto(month)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { result ->
        granted = result
    }

    // 从系统设置页返回时重新检查权限
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = hasPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 月视图覆盖的 42 天（含上下月补齐格），一次性查询，切换月份时重新查询
    val gridStart = remember(visibleMonth) { gridStartOf(visibleMonth) }
    LaunchedEffect(granted, gridStart) {
        if (!granted) {
            events = emptyList()
            loading = false
            return@LaunchedEffect
        }
        loading = true
        events = queryEvents(context, gridStart, gridStart.plusDays(GRID_DAYS - 1L))
        loading = false
    }

    val eventDays = remember(events, zone) {
        events.flatMap { it.coveredDates(zone) }.toSet()
    }
    val selectedEvents = remember(events, selectedDate, zone) {
        events.filter { it.overlapsDay(selectedDate, zone) }.sortedBy { it.startMillis }
    }

    /**
     * 节日名称查询：应用内登记的**休假**节日优先，其次回退到公历固定节日。
     * 调休补课日（isHoliday = false）不算节日，只在今日页的横幅里提示，
     * 不在这里标名——否则「国庆调休补课」会被截成「国庆」，
     * 看起来像是 9/26 放国庆假，反而误导。
     * 农历节日（春节、端午等）年份不同日期也不同，不在此硬编码，
     * 由系统日历同步过来的日程负责展示。
     */
    val holidayNames = remember(holidays) {
        holidays.filter { it.isHoliday }.associate { it.date to it.name }
    }
    fun holidayOf(date: LocalDate): String? =
        holidayNames[date.toString()] ?: FIXED_HOLIDAYS[MonthDay.from(date)]

    /** 月份摘要：本月有多少个日程，以及本月的节日。 */
    val monthSummary = remember(visibleMonth, selectedEvents, eventDays, holidays) {
        monthSummaryOf(visibleMonth, events, holidayNames, zone)
    }

    /** 切换月份并同步选中日期，避免选中到不存在的 31 号。 */
    fun goToMonth(month: YearMonth) {
        selectedDate = selectedDate.coerceInto(month)
        scope.launch { pagerState.animateScrollToPage(monthIndexOf(month)) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶栏（顶部留出状态栏安全区）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(WindowInsets.statusBars.asPaddingValues())
                .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 4.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBackIos,
                    contentDescription = "返回",
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "日历",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = if (granted) {
                        monthSummary
                    } else {
                        "读取手机系统日历"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.mutedText,
                    maxLines = 1
                )
            }
            TextButton(onClick = {
                selectedDate = today
                scope.launch { pagerState.animateScrollToPage(monthIndexOf(today)) }
            }) {
                Text(
                    text = "回到今天",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (!granted) {
            PermissionEmptyState(
                onRequest = { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) },
                onOpenSettings = onOpenSettings
            )
        } else {
            // ---- 上下滑动切换月份 ----
            // 由 MonthCard 内部的 VerticalPager 承载：向上拖 → 下一个月跟随进场，
            // 向下拖 → 上一个月跟随进场，松手按位置与速度吸附，全程连续不打断。
            Column(modifier = Modifier.fillMaxSize()) {
                    MonthCard(
                        month = visibleMonth,
                        pagerState = pagerState,
                        selectedDate = selectedDate,
                        today = today,
                        eventDays = eventDays,
                        holidayOf = ::holidayOf,
                        onPrevMonth = { goToMonth(visibleMonth.minusMonths(1)) },
                        onNextMonth = { goToMonth(visibleMonth.plusMonths(1)) },
                        onSelectDay = { day ->
                            selectedDate = day
                            if (YearMonth.from(day) != visibleMonth) {
                                scope.launch { pagerState.animateScrollToPage(monthIndexOf(day)) }
                            }
                        }
                    )

                    Spacer(Modifier.height(10.dp))

                    Text(
                        text = "上下滑动可平滑切换月份",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.mutedText.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    ) {
                        Text(
                            text = "%d 月 %d 日".format(selectedDate.monthValue, selectedDate.dayOfMonth) +
                                weekdaySuffix(selectedDate),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (selectedEvents.isEmpty()) "无日程" else "${selectedEvents.size} 个日程",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.mutedText
                        )
                        holidayOf(selectedDate)?.let { name ->
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        if (loading) {
                            Spacer(Modifier.width(8.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    if (loading && events.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(26.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (selectedEvents.isEmpty()) {
                                item {
                                    DayEmptyState(loading = loading)
                                }
                            } else {
                                items(selectedEvents, key = { "${it.id}-${it.startMillis}" }) { event ->
                                    EventCard(event = event, zone = zone)
                                }
                            }
                            item { Spacer(Modifier.height(12.dp)) }
                        }
                    }
            }
        }
    }
}

/** 月视图可以翻阅的总月数：2000-01 起算，共 100 年。 */
private const val CALENDAR_TOTAL_MONTHS = 1200

private val CALENDAR_BASE_MONTH: YearMonth = YearMonth.of(2000, 1)

/** 月份 → VerticalPager 页码。 */
private fun monthIndexOf(month: YearMonth): Int =
    ((month.year - CALENDAR_BASE_MONTH.year) * 12 + (month.monthValue - 1))
        .coerceIn(0, CALENDAR_TOTAL_MONTHS - 1)

/** VerticalPager 页码 → 月份。 */
private fun monthFromIndex(index: Int): YearMonth =
    CALENDAR_BASE_MONTH.plusMonths(index.coerceIn(0, CALENDAR_TOTAL_MONTHS - 1).toLong())

private fun monthIndexOf(date: LocalDate): Int =
    monthIndexOf(YearMonth.from(date))

/** 月视图固定 6 行 × 7 列。 */
private const val GRID_DAYS = 42L

private val weekdayLabels = listOf("一", "二", "三", "四", "五", "六", "日")

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private const val MONTH_TITLE_FORMAT = "%d 年 %d 月"

/** 左侧留白的统一值：日历卡片与下方日程列表共用，保证视觉对齐。 */
private val PageHorizontalPadding = 18.dp

/**
 * 公历固定日期的法定节日。
 *
 * 这些节日每年的公历日期恒定，可以安全内置；农历节日（春节、清明、端午、
 * 中秋等）逐年不同，不在此硬编码，交给系统日历同步的日程展示。
 */
private val FIXED_HOLIDAYS: Map<MonthDay, String> = buildMap {
    put(MonthDay.of(1, 1), "元旦")
    put(MonthDay.of(5, 1), "劳动节")
    put(MonthDay.of(10, 1), "国庆节")
}

/** 月视图第一天（周一为首列），可能落在上个月。 */
private fun gridStartOf(month: YearMonth): LocalDate =
    month.atDay(1).minusDays((month.atDay(1).dayOfWeek.value - 1).toLong())

/** 切换月份时把选中日期搬到该月内，避免选到不存在的 31 号。 */
private fun LocalDate.coerceInto(month: YearMonth): LocalDate =
    month.atDay(minOf(this.dayOfMonth, month.lengthOfMonth()))

private fun weekdaySuffix(date: LocalDate): String =
    " 周" + weekdayLabels[date.dayOfWeek.value - 1]

/** 顶栏副标题：本月日程数量 + 本月节日（最多列出两个）。 */
private fun monthSummaryOf(
    month: YearMonth,
    events: List<CalendarEvent>,
    holidayNames: Map<String, String>,
    zone: ZoneId
): String {
    val dayCount = events.flatMap { it.coveredDates(zone) }
        .count { YearMonth.from(it) == month }
    val festivals = buildList {
        for (day in 1..month.lengthOfMonth()) {
            val date = month.atDay(day)
            holidayNames[date.toString()]?.let { if (it !in this) add(it) }
            FIXED_HOLIDAYS[MonthDay.from(date)]?.let { if (it !in this) add(it) }
        }
    }
    return buildString {
        append(MONTH_TITLE_FORMAT.format(month.year, month.monthValue))
        append(" · ")
        append(if (dayCount == 0) "无日程" else "$dayCount 个日程")
        if (festivals.isNotEmpty()) {
            append(" · ")
            append(festivals.take(2).joinToString("、"))
        }
    }
}

private fun formatTime(millis: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalTime().format(timeFormatter)

/** 事件是否覆盖某一天（END 为开区间，全天事件 END 落在次日零点）。 */
private fun CalendarEvent.overlapsDay(day: LocalDate, zone: ZoneId): Boolean {
    val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
    val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val effectiveEnd = if (endMillis > startMillis) endMillis else startMillis + 1L
    return startMillis < dayEnd && effectiveEnd > dayStart
}

/** 事件覆盖到的所有日期（跨天事件会展开，最多展开 400 天以防御脏数据）。 */
private fun CalendarEvent.coveredDates(zone: ZoneId): List<LocalDate> {
    val firstDay = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
    val effectiveEnd = if (endMillis > startMillis) endMillis else startMillis + 1L
    val lastDay = Instant.ofEpochMilli(effectiveEnd - 1L).atZone(zone).toLocalDate()
    val days = ArrayList<LocalDate>()
    var cursor = firstDay
    var guard = 0
    while (!cursor.isAfter(lastDay) && guard < 400) {
        days += cursor
        cursor = cursor.plusDays(1)
        guard++
    }
    return days
}

/** 未授权时的说明卡片。 */
@Composable
private fun PermissionEmptyState(
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val colors = LocalTimetableColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(colors.surfaceCard)
                .padding(20.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = "需要读取日历权限",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "开启后，这里会显示手机自带日历里的课程、会议和纪念日，" +
                    "并按月份整理。应用只读取日历，不会新增、修改或删除任何日程。",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.mutedText
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onRequest,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("允许读取日历", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = "如果你之前拒绝过该权限，弹窗可能不再出现；" +
                    "可以点下面的按钮到系统「设置 → 应用 → 权限」中手动开启。",
                style = MaterialTheme.typography.labelSmall,
                color = colors.mutedText
            )

            Spacer(Modifier.height(6.dp))

            TextButton(
                onClick = onOpenSettings,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "去系统设置授权 →",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * 6×7 月视图卡片：标题栏与星期表头固定，日期网格由 [VerticalPager] 承载。
 *
 * 之所以把月份做成垂直分页而不是「累计拖动距离过阈值就换月」，是为了让手指
 * 拖动过程中相邻月份真实跟随位移，松手后平滑吸附，形成连贯的翻页手感。
 */
@Composable
private fun MonthCard(
    month: YearMonth,
    pagerState: PagerState,
    selectedDate: LocalDate,
    today: LocalDate,
    eventDays: Set<LocalDate>,
    holidayOf: (LocalDate) -> String?,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDay: (LocalDate) -> Unit
) {
    val colors = LocalTimetableColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PageHorizontalPadding)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceCard)
            .padding(horizontal = 10.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevMonth, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.ChevronLeft,
                    contentDescription = "上个月",
                    tint = colors.mutedText
                )
            }
            Text(
                text = MONTH_TITLE_FORMAT.format(month.year, month.monthValue),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onNextMonth, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = "下个月",
                    tint = colors.mutedText
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            weekdayLabels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.mutedText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        VerticalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(DayCellHeight * 6),
            beyondViewportPageCount = 1
        ) { page ->
            MonthDaysGrid(
                month = monthFromIndex(page),
                selectedDate = selectedDate,
                today = today,
                eventDays = eventDays,
                holidayOf = holidayOf,
                onSelectDay = onSelectDay
            )
        }
    }
}

/** 单个月份的 6×7 日期网格（不含标题栏与星期表头）。 */
@Composable
private fun MonthDaysGrid(
    month: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    eventDays: Set<LocalDate>,
    holidayOf: (LocalDate) -> String?,
    onSelectDay: (LocalDate) -> Unit
) {
    val firstDay = remember(month) { gridStartOf(month) }

    Column(modifier = Modifier.fillMaxWidth()) {
        for (row in 0 until 6) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (column in 0 until 7) {
                    val date = firstDay.plusDays((row * 7 + column).toLong())
                    DayCell(
                        date = date,
                        inMonth = YearMonth.from(date) == month,
                        isToday = date == today,
                        isSelected = date == selectedDate,
                        hasEvents = eventDays.contains(date),
                        holidayName = holidayOf(date),
                        onClick = { onSelectDay(date) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    hasEvents: Boolean,
    holidayName: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTimetableColors.current
    val accent = MaterialTheme.colorScheme.primary
    val numberColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isToday -> accent
        inMonth -> MaterialTheme.colorScheme.onSurface
        else -> colors.mutedText.copy(alpha = 0.5f)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
        modifier = modifier
            .height(DayCellHeight)
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isSelected -> accent
                        isToday -> accent.copy(alpha = 0.12f)
                        else -> Color.Transparent
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                fontSize = 13.sp,
                fontWeight = if (isSelected || isToday) FontWeight.SemiBold else FontWeight.Normal,
                color = numberColor
            )
        }
        // 日期下方的标记行：优先显示节日名称，其次显示日程小圆点
        Box(
            modifier = Modifier.padding(top = 1.dp).height(12.dp),
            contentAlignment = Alignment.Center
        ) {
            if (holidayName != null) {
                Text(
                    // 只取前两个汉字，保证在窄格里不折行
                    text = holidayName.take(2),
                    fontSize = 8.sp,
                    lineHeight = 9.sp,
                    maxLines = 1,
                    softWrap = false,
                    color = if (inMonth) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                    }
                )
            } else if (hasEvents) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = if (inMonth) 0.85f else 0.4f))
                )
            }
        }
    }
}

/** 日期格高度：为「数字 + 节日/圆点」两行内容留出位置。 */
private val DayCellHeight = 48.dp

@Composable
private fun DayEmptyState(loading: Boolean) {
    val colors = LocalTimetableColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surfaceCard)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.CalendarMonth,
            contentDescription = null,
            tint = colors.mutedText,
            modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (loading) "正在读取系统日历…" else "这一天没有日程",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.mutedText
        )
    }
}

@Composable
private fun EventCard(event: CalendarEvent, zone: ZoneId) {
    val colors = LocalTimetableColors.current
    val accent = event.colorArgb
        ?.takeIf { (it ushr 24) != 0 }
        ?.let { Color(it) }
        ?: MaterialTheme.colorScheme.primary

    val timeLabel = if (event.allDay) {
        "全天"
    } else {
        "%s–%s".format(formatTime(event.startMillis, zone), formatTime(event.endMillis, zone))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surfaceCard)
            .padding(14.dp)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = timeLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = accent
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = event.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2
            )
            event.location?.let { location ->
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Place,
                        contentDescription = null,
                        tint = colors.mutedText,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = location,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.mutedText,
                        maxLines = 1
                    )
                }
            }
            event.calendarName?.let { name ->
                Spacer(Modifier.height(2.dp))
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.mutedText,
                    maxLines = 1
                )
            }
        }
    }
}
