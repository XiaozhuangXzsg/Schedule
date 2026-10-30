package com.university.timetable.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
// 注意：Glance 里「类型」和「工厂函数」不在同一个包：
//   - androidx.glance.unit.ColorProvider        —— 类型
//   - androidx.glance.color.ColorProvider(day, night) —— 用 ARGB 字面量构造的工厂函数
// 两者同名，必须给类型起别名，否则工厂函数会被类型的构造器遮蔽，报
// 「No parameter with name 'day' found」。
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider as GlanceColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.university.timetable.MainActivity
import com.university.timetable.ServiceLocator
import com.university.timetable.data.entity.TodoItem
import com.university.timetable.data.entity.endMinutes
import com.university.timetable.data.entity.startMinutes
import com.university.timetable.util.ImageLoader
import com.university.timetable.util.SemesterWeeks
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * 用 ARGB 字面量构造一个「白天/夜间同色」的 [ColorProvider]。
 *
 * **切勿**写成 Glance 自带的 `ColorProvider(0xFF4C7DFF.toInt())`：那个重载把 Int 当作
 * **颜色资源 id**（`@ColorRes`），运行期会执行 `context.getColor(id)`，对普通 ARGB 常量必然抛
 * `Resources.NotFoundException`。异常发生在 RemoteViews 应用阶段，启动器只会显示
 * 「载入窗口小部件时出现问题」，且**没有任何堆栈**，极难排查。
 * 只有 `ColorProvider(day = Color(...), night = Color(...))` 才是真正的字面量颜色。
 */
private fun FixedColorProvider(argb: Int) =
    ColorProvider(day = Color(argb), night = Color(argb))

/** Glance 的 ImageProvider 工厂（避免与 ColorProvider 混淆）。 */
private fun bitmapProvider(bitmap: Bitmap) = androidx.glance.ImageProvider(bitmap)

// ---------------------------------------------------------------------------
// 数据模型
// ---------------------------------------------------------------------------

/**
 * 一条课程（用于卡片渲染，已格式化）。
 *
 * [time] 用于紧凑的单行展示（如「08:30 · 天山堂A303」），
 * [ranges] 用于「明日课程」格子：按节次拆成若干段起止时间，
 * 中间有课间休息的多节连堂课会拆成多行（例如 10:10-11:00 / 11:10-12:00）。
 * 注意多段时整条字符串是**带 `\n` 的**，单行展示的场合要先取首尾拼整体区间。
 */
data class WidgetClass(
    val time: String,
    val name: String,
    val location: String,
    val accentArgb: Int,
    val isNow: Boolean,
    val isPast: Boolean,
    val ranges: List<String> = emptyList(),
    /**
     * 「色条」用的课程色。注意它和 [accentArgb] 不是一回事：
     * [accentArgb] 取的是 `CoursePalette.colorsFor(...)` 的**前景色**（深色卡片上接近纯白），
     * 拿它画色条会得到一条白条；这里要的是课程本身的彩色。
     */
    val barArgb: Int = accentArgb
)

/** 一条待办（用于 2×4 卡片左侧）。 */
data class WidgetTodo(
    val content: String,
    val done: Boolean,
    val imageCount: Int
)

/**
 * 「明日课程」快照：当天既没有课也没有待办时，整张卡片改成预告明天的安排。
 */
data class WidgetTomorrow(
    /** 「明日周一」 */
    val dayLabel: String,
    /** 明天日期，例如「9.28」 */
    val dateLabel: String,
    /** 「第4周」 */
    val weekLabel: String,
    val classes: List<WidgetClass>
)

/**
 * 卡片数据快照。在 provideGlance 的挂起上下文中加载，
 * composition 阶段只做纯渲染（Glance 组合内不能调用挂起函数）。
 */
data class WidgetData(
    val title: String = "今日课表",
    val subtitle: String = "",
    val classes: List<WidgetClass> = emptyList(),
    val todos: List<WidgetTodo> = emptyList(),
    /** 无待办时用于展示的图片（最多 3 张） */
    val images: List<Bitmap> = emptyList(),
    /** 当前 / 下一节课（2×2 卡片用） */
    val now: WidgetClass? = null,
    val next: WidgetClass? = null,
    /** 今天既没课也没待办时的「明日预告」；否则为 null */
    val tomorrow: WidgetTomorrow? = null,
    /**
     * 卡片配色。由应用内同一套主题算出，保证设置里换主题后卡片同步变化；
     * 加载失败时退回 [FallbackWidgetTheme]。
     */
    val theme: WidgetTheme = FallbackWidgetTheme
)

// ---------------------------------------------------------------------------
// 数据加载
// ---------------------------------------------------------------------------

object WidgetDataLoader {

    suspend fun loadToday(context: Context): WidgetData {
        val repo = ServiceLocator.schedule(context)
        val prefs = ServiceLocator.preferences(context).preferences.first()
        Log.d(
            "WidgetRefresh",
            "loadToday 读取偏好：alpha=${prefs.widgetBgAlpha} 主题=${prefs.themePreset}"
        )
        val today = LocalDate.now()
        // 深浅色按「应用内偏好」解析（跟随系统 / 手动），而不是只看系统设置，
        // 否则用户在应用里手动切夜间模式时卡片不会跟着变。
        val night = com.university.timetable.ui.theme.resolveDark(
            context, prefs.followSystemTheme, prefs.darkTheme
        )
        val theme = widgetThemeOf(prefs, context)
        val start = LocalDate.parse(prefs.semesterStart)
        val week = SemesterWeeks.weekOf(today, start)
        val periods = repo.periodTimesOnce()

        val todos = repo.todosOnOnce(today)

        if (week < 1) {
            return WidgetData(
                title = "今日课表",
                subtitle = "学期尚未开始",
                todos = todos.map { it.toWidgetTodo() },
                theme = theme
            )
        }

        // 与应用内今日页使用同一个「当日快照」函数：放假停课 / 调休补课 / 单双周的判定完全一致。
        val blocks = repo.daySnapshotOnce(today, week).blocks
        val now = java.time.LocalTime.now()
        val nowMinutes = now.hour * 60 + now.minute

        val allClasses = blocks.map { b ->
            WidgetClass(
                time = b.startTime,
                name = b.name,
                location = b.location.orEmpty(),
                accentArgb = accentOf(b.courseId, b.color, night),
                isNow = nowMinutes in b.startMinutes until b.endMinutes,
                isPast = b.endMinutes < nowMinutes,
                ranges = rangesOf(b, periods),
                barArgb = barOf(b.courseId, b.color, night)
            )
        }

        val widgetTodos = todos.map { it.toWidgetTodo() }

        // 上完的课不再占卡片版面：只留「正在上」和「还没上」的。
        // 卡片只有 2×4 格，把空间留给接下来的课比留一列灰掉的记录有用；
        // 需要回看已经上过的课，应用内的今日页仍然完整列出。
        val classes = allClasses.filter { !it.isPast }

        // 以下两种情况整张卡片改为预告明天：
        // ① 今天既没有课、也没有待办；
        // ② 今天的课已经全部结束，且没有「未完成」的待办（已完成的待办不占版面）。
        // 注意这里必须用**完整**的 allClasses 来判断「已全部结束」，
        // 不能用过滤后的 classes——那样它恒为空，预告版式就再也不会出现。
        val allClassesEnded = allClasses.isNotEmpty() && allClasses.all { it.isPast }
        val hasPendingTodo = widgetTodos.any { !it.done }
        val tomorrow = if ((allClasses.isEmpty() || allClassesEnded) && !hasPendingTodo) {
            loadTomorrow(context, repo, start, periods, today, night)
        } else {
            null
        }

        return WidgetData(
            title = "%d/%02d/%02d 第 %d 周".format(
                today.year, today.monthValue, today.dayOfMonth, week
            ),
            subtitle = when {
                allClasses.isEmpty() -> "今天没有课"
                classes.isEmpty() -> "今天的课已结束"
                else -> "共 ${classes.size} 节课"
            },
            classes = classes,
            todos = widgetTodos,
            images = loadTodoImages(context, todos),
            now = classes.firstOrNull { it.isNow },
            next = classes.firstOrNull { !it.isNow },
            tomorrow = tomorrow,
            theme = theme
        )
    }

    /**
     * 明天的课程快照。
     * 周次由明天所在的自然周决定（跨周时会自动变成下一周，与周视图一致）。
     * `base` 为「今天」，与 [loadToday] 使用同一个日期，避免跨零点时两侧不一致。
     */
    private suspend fun loadTomorrow(
        context: Context,
        repo: com.university.timetable.data.repository.ScheduleRepository,
        start: LocalDate,
        periods: List<com.university.timetable.data.entity.PeriodTime>,
        base: LocalDate,
        night: Boolean
    ): WidgetTomorrow? {
        val tomorrow = base.plusDays(1)
        val week = SemesterWeeks.weekOf(tomorrow, start)
        val classes = if (week < 1) {
            emptyList()
        } else {
            repo.daySnapshotOnce(tomorrow, week).blocks.map { b ->
                WidgetClass(
                    time = b.startTime,
                    name = b.name,
                    location = b.location.orEmpty(),
                    accentArgb = accentOf(b.courseId, b.color, night),
                    isNow = false,
                    isPast = false,
                    ranges = rangesOf(b, periods),
                    barArgb = barOf(b.courseId, b.color, night)
                )
            }
        }
        return WidgetTomorrow(
            dayLabel = "明日${SemesterWeeks.dayLabel(tomorrow.dayOfWeek.value)}",
            dateLabel = "${tomorrow.monthValue}.${tomorrow.dayOfMonth}",
            weekLabel = if (week < 1) "假期" else "第${week}周",
            classes = classes
        )
    }

    /**
     * 把一个上课时段拆成若干「起止时间」：
     * 单独一节 → 一条；连堂且节次之间没有间隙 → 一条合并区间；
     * 连堂但中间隔了课间休息（例如 3-4 节之间 11:00-11:10）→ 拆成两条。
     */
    private fun rangesOf(
        block: com.university.timetable.data.repository.ClassBlock,
        periods: List<com.university.timetable.data.entity.PeriodTime>
    ): List<String> {
        val ordered = periods.sortedBy { it.period }
        val mine = ordered.filter { it.period in block.startPeriod..block.endPeriod }
        if (mine.isEmpty()) return listOf(block.timeLabel)

        val result = mutableListOf<String>()
        var groupStart = mine.first()
        var previous = mine.first()
        for (index in 1 until mine.size) {
            val current = mine[index]
            val gap = current.startMinutes - previous.endMinutes
            if (gap > 0) {
                result += "${groupStart.startTime}-${previous.endTime}"
                groupStart = current
            }
            previous = current
        }
        result += "${groupStart.startTime}-${previous.endTime}"

        // 同一段区间被拆成两行时，用换行让它在上方名字下面自然折行
        return if (result.size == 1) result else listOf(result.joinToString("\n"))
    }

    suspend fun loadWeek(context: Context): WidgetData {
        val repo = ServiceLocator.schedule(context)
        val prefs = ServiceLocator.preferences(context).preferences.first()
        val today = LocalDate.now()
        val start = LocalDate.parse(prefs.semesterStart)
        val week = SemesterWeeks.weekOf(today, start).coerceIn(1, prefs.totalWeeks)

        val classes = mutableListOf<WidgetClass>()
        val night = com.university.timetable.ui.theme.resolveDark(
            context, prefs.followSystemTheme, prefs.darkTheme
        )
        val theme = widgetThemeOf(prefs, context)
        for (day in 1..7) {
            val date = SemesterWeeks.dateOf(week, day, start)
            repo.daySnapshotOnce(date, week).blocks.sortedBy { it.startMinutes }.forEach { b ->
                classes += WidgetClass(
                    time = "${SemesterWeeks.dayChar(day)} ${b.startTime}",
                    name = b.name,
                    location = b.location.orEmpty(),
                    accentArgb = accentOf(b.courseId, b.color, night),
                    isNow = false,
                    isPast = date < today,
                    barArgb = barOf(b.courseId, b.color, night)
                )
            }
        }

        val todos = repo.todosOnOnce(today)

        return WidgetData(
            title = "第 $week 周课表",
            subtitle = if (classes.isEmpty()) "本周没有课" else "共 ${classes.size} 节课",
            classes = classes,
            todos = todos.map { it.toWidgetTodo() },
            images = loadTodoImages(context, todos),
            theme = theme
        )
    }

    /**
     * 无待办文字时，读取待办里的图片（最多 3 张）用于卡片展示。
     *
     * 必须按 [WidgetImageBudget] 算出的更小边长解码：Android 17 起 RemoteViews 内
     * Bitmap/Icon 的总内存有硬上限，用应用内的 1440px 上限解码 3 张会超限并**致命崩溃**。
     */
    private suspend fun loadTodoImages(context: Context, todos: List<TodoItem>): List<Bitmap> {
        val uris = todos.flatMap { it.images }.take(TodoItem.MAX_IMAGES)
        if (uris.isEmpty()) return emptyList()
        val maxDimension = WidgetImageBudget.maxDimensionFor(context, uris.size)
        return uris.mapNotNull { uri ->
            runCatching { ImageLoader.load(context, uri, maxDimension) }.getOrNull()
        }
    }

    private fun TodoItem.toWidgetTodo() = WidgetTodo(
        content = content,
        done = done,
        imageCount = images.size
    )

    /**
     * 课程**文字**用的颜色，取配色里的前景色（深色卡片上接近纯白）。
     *
     * 注意必须用 `Color.toArgb()`：`Color.value` 是 `ULong`（sRGB 的 32 位色值放在**高 32 位**），
     * 直接 `.value.toInt()` 取到的是低 32 位的色彩空间 id，也就是 **0**（透明），
     * 画出来什么都没有（v1.8.7 修复）。
     */
    private fun accentOf(courseId: Long, custom: Int?, dark: Boolean): Int {
        val (_, fg) = com.university.timetable.ui.theme.CoursePalette
            .colorsFor(courseId, custom, dark)
        return fg.toArgb()
    }

    /**
     * 课程**色条**用的颜色。不能拿 [accentOf] 的结果去画色条——那是前景色，
     * 自定义深色课程色的前景色接近纯白，画出来是一条白条。
     * 自定义色直接用课程色本身；预设色取对应主题下的柔和色。
     */
    private fun barOf(courseId: Long, custom: Int?, dark: Boolean): Int {
        if (custom != null && custom != 0) return custom or (0xFF shl 24)
        val (_, fg) = com.university.timetable.ui.theme.CoursePalette
            .colorsFor(courseId, null, dark)
        return fg.toArgb() or (0xFF shl 24)
    }
}

// ---------------------------------------------------------------------------
// 共用的颜色与小组件
// ---------------------------------------------------------------------------

/**
 * 桌面卡片的一套配色。
 *
 * **必须由应用内同一套主题计算得出**（[com.university.timetable.ui.theme.timetableScheme] +
 * [com.university.timetable.ui.theme.resolveDark]），这样设置里换主题色、切深浅色、
 * 开关哥伦比娅风格之后，桌面卡片才会跟着变。
 * 之前这里是一组写死的颜色常量，所以卡片永远不跟随应用主题。
 */
data class WidgetTheme(
    val cardBg: GlanceColorProvider,
    val title: GlanceColorProvider,
    val muted: GlanceColorProvider,
    val divider: GlanceColorProvider,
    val todayBg: GlanceColorProvider
)
/** 把一套 [com.university.timetable.ui.theme.TimetableScheme] 映射成卡片的 [WidgetTheme]。 */
private fun widgetThemeOf(
    prefs: com.university.timetable.data.prefs.UiPreferences,
    context: Context
): WidgetTheme {
    val dark = com.university.timetable.ui.theme.resolveDark(
        context, prefs.followSystemTheme, prefs.darkTheme
    )
    val scheme = com.university.timetable.ui.theme
        .timetableScheme(dark, prefs.themePreset, prefs.columbinaStyle)
    return WidgetTheme(
        // 卡片底色按用户在设置里调的不透明度落到色值上，让桌面卡片能透出壁纸。
        // 只改 alpha 通道，色相仍由应用主题决定。
        cardBg = FixedColorProvider(
            withAlpha(scheme.extras.surfaceCard.toArgb(), prefs.widgetBgAlpha)
        ),
        title = FixedColorProvider(scheme.colorScheme.onSurface.toArgb()),
        muted = FixedColorProvider(scheme.extras.mutedText.toArgb()),
        divider = FixedColorProvider(scheme.extras.gridLine.toArgb()),
        todayBg = FixedColorProvider(scheme.colorScheme.surfaceVariant.toArgb())
    )
}

/** 加载不到偏好时的兜底配色（与默认主题一致）。 */
private val FallbackWidgetTheme = WidgetTheme(
    cardBg = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF16171C)),
    title = ColorProvider(day = Color(0xFF16181D), night = Color(0xFFE7E7EC)),
    muted = ColorProvider(day = Color(0xFF8E909B), night = Color(0xFF9EA1AD)),
    divider = ColorProvider(day = Color(0xFFEAEBF2), night = Color(0xFF2A2C34)),
    todayBg = ColorProvider(day = Color(0xFFF1F3FB), night = Color(0xFF20222A))
)

@Composable
private fun openTodayIntent() = actionStartActivity(
    Intent(LocalContext.current, MainActivity::class.java).apply {
        action = MainActivity.ACTION_OPEN_TODAY
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
)

/**
 * 吞掉点击、但**什么都不做**。
 *
 * 卡片现在把可点击区域铺到「除课程格以外的全部区域」（外层容器整体挂
 * [openTodayIntent]）。课程格显示的是课程名与地点，点它不该跳到软件；
 * 可它是外层容器的子视图，不处理点击就会冒泡上去打开软件。
 * RemoteViews 里没有「只吃掉点击」的修饰符，所以给课程格挂一个空动作的回调。
 */
class ConsumeTapAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) = Unit
}

/** 见 [ConsumeTapAction]：把这个区域标成「可点但无事发生」，从而挡住外层的跳转。 */
@Composable
private fun consumeTap() = actionRunCallback<ConsumeTapAction>()

/**
 * 卡片右上角的手动刷新按钮。
 *
 * 用 [androidx.glance.appwidget.action.actionRunCallback] 触发 [RefreshWidgetsAction]，
 * 该回调由 Glance 自带的广播接收器执行（无需在清单里额外注册），
 * 只做「重新读数据库 → 重绘」，不会修改任何业务数据。
 */
@Composable
private fun RefreshChip(theme: WidgetTheme) {
    Text(
        text = "⟳",
        style = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = theme.muted
        ),
        maxLines = 1,
        modifier = GlanceModifier
            .clickable(
                androidx.glance.appwidget.action.actionRunCallback<RefreshWidgetsAction>()
            )
            .padding(horizontal = 2.dp)
    )
}

// ---------------------------------------------------------------------------
// 2×2（4×2 单元格）：显示当前课程 + 下一节课
// ---------------------------------------------------------------------------

class CompactClassesWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = runCatching { WidgetDataLoader.loadToday(context) }
            .getOrElse { WidgetData(title = "今日课表", subtitle = "读取课表失败") }
        WidgetRenderCache.today.value = initial
        provideContent { CompactContent(WidgetRenderCache.today.value ?: initial) }
    }
}

@Composable
private fun CompactContent(data: WidgetData) {
    val theme = data.theme
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(theme.cardBg)
            .cornerRadius(22.dp)
            .padding(12.dp)
            .clickable(openTodayIntent())
    ) {
        Spacer(GlanceModifier.height(4.dp))

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = if (data.now != null) "正在上课" else "下一节课",
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = theme.title
                ),
                maxLines = 1
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                text = data.subtitle,
                style = TextStyle(fontSize = 9.sp, color = theme.muted),
                maxLines = 1
            )
            // 手动刷新入口：点一下立刻重新读取数据库并重绘，不做任何业务写入。
            Spacer(GlanceModifier.width(6.dp))
            RefreshChip(theme)
        }

        Spacer(GlanceModifier.height(6.dp))

        val primary = data.now ?: data.next
        if (primary == null) {
            Text(
                text = if (data.classes.isEmpty()) "今天没有课" else "今日课程已结束",
                style = TextStyle(fontSize = 13.sp, color = theme.muted)
            )
        } else {
            // 课程名与地点这一段**不**响应点击：点它不该跳进软件。
            // 卡片其余区域（标题行、空白、分隔线、底部留白）都由外层 Column 打开软件。
            Column(modifier = GlanceModifier.fillMaxWidth().clickable(consumeTap())) {
                Text(
                    text = primary.name,
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = theme.title
                    ),
                    maxLines = 1
                )
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    text = buildString {
                        append(primary.time)
                        if (primary.location.isNotBlank()) append(" · ${primary.location}")
                    },
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = FixedColorProvider(primary.accentArgb)
                    ),
                    maxLines = 1
                )
            }

            // 正在上课时，补充显示紧随其后的那节
            val following = if (data.now != null) data.next else null
                ?: data.classes.filter { !it.isNow && !it.isPast }.drop(1).firstOrNull()

            if (following != null) {
                Spacer(GlanceModifier.height(8.dp))
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(theme.divider)
                ) {}
                Spacer(GlanceModifier.height(6.dp))
                Text(
                    text = "接下来",
                    style = TextStyle(fontSize = 9.sp, color = theme.muted),
                    maxLines = 1
                )
                Text(
                    text = "${following.time}  ${following.name}",
                    style = TextStyle(
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = theme.title
                    ),
                    maxLines = 1,
                    modifier = GlanceModifier.clickable(consumeTap())
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 2×4（4×4 单元格）：左侧待办（独立滚动）+ 右侧课表（独立滚动）
// ---------------------------------------------------------------------------

class FullScheduleWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Log.d("WidgetRefresh", "provideGlance(Full) 进入 id=$id")
        val initial = runCatching { WidgetDataLoader.loadToday(context) }
            .getOrElse { WidgetData(title = "今日课表", subtitle = "读取课表失败") }
        WidgetRenderCache.today.value = initial
        provideContent { FullContent(WidgetRenderCache.today.value ?: initial) }
    }
}

@Composable
private fun FullContent(data: WidgetData) {
    val theme = data.theme
    val tomorrow = data.tomorrow
    if (tomorrow != null) {
        TomorrowContent(tomorrow, theme)
        return
    }

    // 待办栏只在「真的有东西可显示」时才占版面：没有待办、也没有待办图片时，
    // 连同分隔线一起隐藏，把宽度整块让给课程。4×2 本来就窄，
    // 留一栏「暂无待办」的占位提示既浪费版面，又让课程只能挤在半边。
    val showTodoPane = data.todos.isNotEmpty() || data.images.isNotEmpty()

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(theme.cardBg)
            .cornerRadius(20.dp)
            .padding(12.dp)
            .clickable(openTodayIntent())
    ) {
        Spacer(GlanceModifier.height(6.dp))

        // 可点击区域铺到整张卡片，**只有课程行不该点**（课程行自己挂空动作挡掉）。
        // 课程栏要留给上下滑动：LazyColumn 是独立的 ListView，滑动时手势被它吃掉，
        // 不会误触成「打开软件」。
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = data.title,
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = theme.title
                ),
                maxLines = 1
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                text = data.subtitle,
                style = TextStyle(fontSize = 9.sp, color = theme.muted),
                maxLines = 1
            )
            Spacer(GlanceModifier.width(6.dp))
            RefreshChip(theme)
        }

        Spacer(GlanceModifier.height(6.dp))

        // 左：待办（前 3 条）  |  右：课程（前 5 条）
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {

            if (showTodoPane) {
                // ---- 左列：待办 ----
                Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                    Text(
                        text = "待办",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = theme.muted
                        ),
                        maxLines = 1
                    )
                    Spacer(GlanceModifier.height(4.dp))

                    if (data.todos.isNotEmpty()) {
                        // 不要用 LazyColumn：启动器把集合条目视图当独立子组件 inflate 时
                        // appWidgetId 仍为 0，会被换成常驻的「无法添加微件。」错误视图。
                        Column(modifier = GlanceModifier.fillMaxWidth()) {
                            data.todos.take(3).forEach { todo ->
                                TodoRow(todo, theme)
                                Spacer(GlanceModifier.height(4.dp))
                            }
                        }
                    } else {
                        androidx.glance.Image(
                            provider = bitmapProvider(data.images.first()),
                            contentDescription = "待办图片",
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .height(84.dp)
                                .cornerRadius(12.dp)
                        )
                    }
                }

                Spacer(GlanceModifier.width(8.dp))

                // 竖直分隔线
                Box(
                    modifier = GlanceModifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(theme.divider)
                ) {}

                Spacer(GlanceModifier.width(8.dp))
            }

            // ---- 右列：课程 ----
            Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                Text(
                    text = "课程",
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = theme.muted
                    ),
                    maxLines = 1
                )
                Spacer(GlanceModifier.height(4.dp))

                if (data.classes.isEmpty()) {
                    Text(
                        text = "今天没有课",
                        style = TextStyle(fontSize = 10.sp, color = theme.muted),
                        maxLines = 1
                    )
                } else if (data.classes.size <= 2) {
                    // 只有一两节课时用普通 Column，**不要**用 LazyColumn：
                    // LazyColumn 编译成 ListView，会连同自己下方的空白背景一起吞掉点击，
                    // 那一片就变成「点了没反应」。普通 Column 只占内容高度，
                    // 剩下的空白仍属于外层 Column，也就是仍能点一下进软件。
                    // 4×2 卡片的高度正好放得下两行，再多就必须靠 LazyColumn 滚动。
                    Column(modifier = GlanceModifier.fillMaxWidth()) {
                        data.classes.forEach { item ->
                            ClassRow(item, theme)
                            Spacer(GlanceModifier.height(4.dp))
                        }
                    }
                } else {
                    // 三节以上必须用 LazyColumn：Glance 里唯一能纵向滚动的容器就是它。
                    // 课多的时候普通 Column 只会把后面的课挤出可视区，用户根本看不到；
                    // LazyColumn 则可以在卡片里上下滑动，把被挡住的课翻出来。
                    //
                    // 早先注释里怀疑 LazyColumn 会导致常驻的「无法添加微件。」错误视图，
                    // 后来定位到真正的元凶是「updateAll + 再逐个 update(context, id)」重复刷新，
                    // 与 LazyColumn 无关（见 WidgetRefresher.refreshAllOnce 的注释）。
                    LazyColumn(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .defaultWeight()
                    ) {
                        items(data.classes.size) { index ->
                            ClassRow(data.classes[index], theme)
                            Spacer(GlanceModifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * 「明日预告」整卡版式：今天的课已全部结束（或今天既没课也没待办）时使用。
 *
 * 第一行大字「明日周一，共有 4 门课要上」（数字用强调色）+ 右上角明天的日期/周次，
 * 下方是课程格：课程名在上、起止时间与地点在下，格内左右留白、格与格之间留 [TomorrowGridLayout.CellGap]。
 * 课程格用课程色做底（低透明度），保持与课表内课程颜色一致。
 *
 * 课少时（≤ [TomorrowGridLayout.MaxStaticCells]）格子按剩余高度均分、把卡片铺满；
 * 课多时整块换成可上下滑动的列表，不再把第 5 门之后的课藏起来。
 */
@Composable
private fun TomorrowContent(tomorrow: WidgetTomorrow, theme: WidgetTheme) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(theme.cardBg)
            .cornerRadius(20.dp)
            .padding(12.dp)
            .clickable(openTodayIntent())
    ) {
        Spacer(GlanceModifier.height(8.dp))

        // 可点击区域铺到整张卡片；**课程格自己挡掉点击**（见 TomorrowClassCell），
        // 所以点课程名/地点不会跳进软件，其余任何地方都会。
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = tomorrow.dayLabel,
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = theme.title
                ),
                maxLines = 1
            )
            if (tomorrow.classes.isEmpty()) {
                Text(
                    text = "没有课",
                    style = TextStyle(fontSize = 12.sp, color = theme.muted),
                    maxLines = 1
                )
            } else {
                Text(
                    text = "，共有 ",
                    style = TextStyle(fontSize = 12.sp, color = theme.title),
                    maxLines = 1
                )
                Text(
                    text = "${tomorrow.classes.size}",
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = FixedColorProvider(0xFFFF5A5F.toInt())
                    ),
                    maxLines = 1
                )
                Text(
                    text = " 门课要上",
                    style = TextStyle(fontSize = 12.sp, color = theme.title),
                    maxLines = 1
                )
            }
            Spacer(GlanceModifier.defaultWeight())
            Column(horizontalAlignment = Alignment.Horizontal.End) {
                Text(
                    text = tomorrow.dateLabel,
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = theme.title
                    ),
                    maxLines = 1
                )
                Text(
                    text = tomorrow.weekLabel,
                    style = TextStyle(fontSize = 9.sp, color = theme.muted),
                    maxLines = 1
                )
            }
        }

        Spacer(GlanceModifier.height(8.dp))

        if (tomorrow.classes.isEmpty()) {
            Text(
                text = "明天没有安排，好好休息一下",
                style = TextStyle(fontSize = 11.sp, color = theme.muted),
                maxLines = 1
            )
        } else {
            // 课程格占据标题行以下的全部剩余高度，由行权重均分，避免在最小尺寸下被裁切
            TomorrowGrid(
                classes = tomorrow.classes,
                theme = theme,
                modifier = GlanceModifier.fillMaxWidth().defaultWeight()
            )
        }
    }
}

/**
 * 明日课程格。
 *
 * 课 ≤ [TomorrowGridLayout.MaxStaticCells] 门时用静态网格把卡片剩余高度铺满（常见场景最耐看）；
 * 超过时切换成可上下滑动的版本，见 [ScrollableTomorrowGrid]。
 */
@Composable
private fun TomorrowGrid(
    classes: List<WidgetClass>,
    theme: WidgetTheme,
    modifier: GlanceModifier = GlanceModifier
) {
    if (TomorrowGridLayout.needsScroll(classes.size)) {
        ScrollableTomorrowGrid(classes, theme, modifier)
    } else {
        StaticTomorrowGrid(classes, theme, modifier)
    }
}

/**
 * 课 ≤ [TomorrowGridLayout.MaxStaticCells] 门时的静态网格：标题行以下的剩余高度按行均分。
 *
 * 这样在 4×2 的最小尺寸下既不会把文字裁掉半截，也不会在只有 3 门课时留下大片空白。
 */
@Composable
private fun StaticTomorrowGrid(
    classes: List<WidgetClass>,
    theme: WidgetTheme,
    modifier: GlanceModifier
) {
    val rows = classes.chunked(TomorrowGridLayout.Columns)

    Column(modifier = modifier) {
        rows.forEachIndexed { index, pair ->
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                TomorrowClassCell(
                    item = pair[0],
                    theme = theme,
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight()
                )
                Spacer(GlanceModifier.width(TomorrowGridLayout.CellGap))
                if (pair.size == TomorrowGridLayout.Columns) {
                    TomorrowClassCell(
                        item = pair[1],
                        theme = theme,
                        modifier = GlanceModifier.defaultWeight().fillMaxHeight()
                    )
                } else {
                    Spacer(GlanceModifier.defaultWeight().fillMaxHeight())
                }
            }
            if (index != rows.lastIndex) Spacer(GlanceModifier.height(TomorrowGridLayout.CellGap))
        }
    }
}

/**
 * 明天课多于 [TomorrowGridLayout.MaxStaticCells] 门时的版式：**可以上下滑动**。
 *
 * 早先的实现是 `classes.take(4)` + 「还有 N 门课」——第 5 门之后的课根本看不到。
 * 这里按「一行两门课」切成若干行交给 LazyColumn（Glance 里唯一能纵向滚动的容器），
 * 每行用 [TomorrowGridLayout.CellRowHeight] 固定高度，课再多也能滑出来。
 */
@Composable
private fun ScrollableTomorrowGrid(
    classes: List<WidgetClass>,
    theme: WidgetTheme,
    modifier: GlanceModifier
) {
    val rows = classes.chunked(TomorrowGridLayout.Columns)

    LazyColumn(modifier = modifier) {
        items(rows.size) { index ->
            val pair = rows[index]
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(bottom = TomorrowGridLayout.CellGap)
            ) {
                TomorrowClassCell(
                    item = pair[0],
                    theme = theme,
                    modifier = GlanceModifier.defaultWeight().height(TomorrowGridLayout.CellRowHeight)
                )
                Spacer(GlanceModifier.width(TomorrowGridLayout.CellGap))
                if (pair.size == TomorrowGridLayout.Columns) {
                    TomorrowClassCell(
                        item = pair[1],
                        theme = theme,
                        modifier = GlanceModifier
                            .defaultWeight()
                            .height(TomorrowGridLayout.CellRowHeight)
                    )
                } else {
                    Spacer(GlanceModifier.defaultWeight())
                }
            }
        }
    }
}

/**
 * 明日课程格：课程色低透明底 + **左侧课程色彩条** + 课名（白）+ 起止时间与地点（灰）。
 *
 * 排版用「课名一行 + 时间一行」——4×2 卡片的可用高度很紧，两行已是上限；
 * 但格子内部左右都留出呼吸空间（不再是文字贴到圆角边上），格与格之间由调用方用
 * [TomorrowGridLayout.CellGap] 隔开，避免整块看起来「边贴着边」。
 */
@Composable
private fun TomorrowClassCell(
    item: WidgetClass,
    theme: WidgetTheme,
    modifier: GlanceModifier = GlanceModifier
) {
    val tint = withAlpha(item.barArgb, 0.22f)
    Row(
        modifier = modifier
            .background(FixedColorProvider(tint))
            .cornerRadius(12.dp)
            .padding(end = 9.dp, top = 7.dp, bottom = 7.dp)
            // 课程格显示课名与地点，点它不该跳进软件 —— 用空动作把点击挡在这里。
            .clickable(consumeTap())
    ) {
        // 侧边彩条：与今日课程列表的色条一致，用课程本身的颜色（不调淡）
        Box(
            modifier = GlanceModifier
                .width(3.dp)
                .fillMaxHeight()
                .background(FixedColorProvider(item.barArgb))
        ) {}
        Spacer(GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = item.name,
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = TomorrowGridLayout.CellNameSp.sp,
                    color = theme.title
                ),
                maxLines = 1
            )
            // ranges 里「中间有课间休息」的连堂课会被拼成多行（\n 分隔），
            // 但格子里只排得下一行，所以取整体的「第一段开始 - 最后一段结束」。
            val rawRange = item.ranges.firstOrNull().orEmpty().ifBlank { item.time }
            val segments = rawRange.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
            val timeLine = if (segments.isEmpty()) {
                rawRange
            } else {
                "${segments.first().substringBefore('-')}-${segments.last().substringAfter('-')}"
            }
            Text(
                text = buildString {
                    append(timeLine)
                    // 格内宽度有限，用单空格分隔，避免「时间 + 地点」被省略号截断
                    if (item.location.isNotBlank()) append(" ${item.location}")
                },
                style = TextStyle(fontSize = TomorrowGridLayout.CellDetailSp.sp, color = theme.muted),
                maxLines = 1
            )
        }
    }
}

/** 把颜色按 alpha 调淡，用于课程格的底纹。 */
private fun withAlpha(argb: Int, alpha: Float): Int {
    val a = (alpha.coerceIn(0f, 1f) * 255f).toInt()
    return (argb and 0x00FFFFFF) or (a shl 24)
}

@Composable
private fun TodoRow(todo: WidgetTodo, theme: WidgetTheme) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.Top
    ) {
        Box(
            modifier = GlanceModifier
                .width(3.dp)
                .height(16.dp)
                .background(
                    if (todo.done) FixedColorProvider(0xFF9EA1AD.toInt())
                    else FixedColorProvider(0xFF4C7DFF.toInt())
                )
                .cornerRadius(2.dp)
        ) {}
        Spacer(GlanceModifier.width(6.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = todo.content,
                style = TextStyle(
                    fontSize = 11.sp,
                    color = if (todo.done) theme.muted else theme.title
                ),
                maxLines = 3
            )
            if (todo.imageCount > 0) {
                Text(
                    text = "含 ${todo.imageCount} 张图片",
                    style = TextStyle(fontSize = 9.sp, color = theme.muted),
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * 课程行：**只显示课程名与地点**，用课程色区分（左侧色条 + 课程名着色）。
 * 已上完的课色条保留课程色、只降低透明度，正在上的课加粗并加右侧色点强调。
 */
@Composable
private fun ClassRow(item: WidgetClass, theme: WidgetTheme) {
    val accent = FixedColorProvider(item.barArgb)
    val nameColor = if (item.isPast) theme.muted else theme.title

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            // 课程行只显示课名与地点，点它不该跳进软件 —— 用空动作把点击挡在这里。
            .clickable(consumeTap()),
        verticalAlignment = Alignment.Vertical.Top
    ) {
        Box(
            modifier = GlanceModifier
                .width(3.dp)
                .height(22.dp)
                .background(
                    if (item.isPast) FixedColorProvider(withAlpha(item.barArgb, 0.55f))
                    else FixedColorProvider(item.barArgb)
                )
                .cornerRadius(2.dp)
        ) {}
        Spacer(GlanceModifier.width(6.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = item.name,
                style = TextStyle(
                    fontWeight = if (item.isNow) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 11.sp,
                    color = nameColor
                ),
                maxLines = 2
            )
            if (item.location.isNotBlank()) {
                Text(
                    text = item.location,
                    style = TextStyle(fontSize = 9.sp, color = theme.muted),
                    maxLines = 1
                )
            }
        }
        if (item.isNow) {
            Spacer(GlanceModifier.width(4.dp))
            Box(
                modifier = GlanceModifier
                    .size(6.dp)
                    .background(accent)
                    .cornerRadius(3.dp)
            ) {}
        }
    }
}

// ---------------------------------------------------------------------------
// Receiver
// ---------------------------------------------------------------------------

class CompactClassesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CompactClassesWidget()
}

class FullScheduleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FullScheduleWidget()
}

/**
 * 旧的「今日课表」卡片名称保留为 2×4 布局，
 * 这样从旧版本升级后桌面已有的卡片仍能继续工作。
 */
class TodayClassesWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = runCatching { WidgetDataLoader.loadToday(context) }
            .getOrElse { WidgetData(title = "今日课表", subtitle = "读取课表失败") }
        WidgetRenderCache.today.value = initial
        provideContent { FullContent(WidgetRenderCache.today.value ?: initial) }
    }
}

class TodayClassesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayClassesWidget()
}

/** 保留旧的「本周课表」类型，复用 2×4 布局（本周课程列表）。 */
class WeekClassesWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = runCatching { WidgetDataLoader.loadWeek(context) }
            .getOrElse { WidgetData(title = "本周课表", subtitle = "读取课表失败") }
        WidgetRenderCache.week.value = initial
        provideContent { FullContent(WidgetRenderCache.week.value ?: initial) }
    }
}

class WeekClassesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeekClassesWidget()
}
