package com.university.timetable.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.university.timetable.ui.theme.LocalTimetableColors
import com.university.timetable.util.SemesterWeeks
import java.time.LocalDate

/**
 * 顶栏：
 *  左侧 → 第几周 / 周几 / 年月日
 *  右侧 → [添加] [导入] [三点菜单]
 * 三点菜单内包含：周数进度条、日间/夜间切换、课表设置、作息时间、小组件设置、简洁模式。
 */
@Composable
fun AppTopBar(
    week: Int,
    date: LocalDate,
    onAdd: () -> Unit,
    onImport: () -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onJumpToday: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPeriodEditor: () -> Unit,
    onOpenWidgetHelp: () -> Unit = {},
    onToggleSimpleMode: () -> Unit,
    simpleMode: Boolean,
    totalWeeks: Int,
    selectedWeek: Int,
    onSelectWeek: (Int) -> Unit,
    darkTheme: Boolean,
    onToggleDarkTheme: () -> Unit,
    /** 非空时，三点按钮改为触发该回调（用于「本周课表」的居中面板） */
    onOpenMenu: (() -> Unit)? = null,
    /** 非空时，在顶栏显示「日历/待办」图标（用于「今日课表」） */
    onOpenTodo: (() -> Unit)? = null,
    /** 非空时，在顶栏显示 AI 助手图标 */
    onOpenAi: (() -> Unit)? = null,
    /** 今日待办数量，用于角标 */
    todoCount: Int = 0,
    /** 非空时，点击左上角「周次 + 日期」进入日历页 */
    onOpenCalendar: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalTimetableColors.current
    var menuOpen by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 左侧：周次 + 日期（可点击进入日历）
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (onOpenCalendar != null) {
                            Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(onClick = onOpenCalendar)
                                .padding(end = 8.dp, top = 2.dp, bottom = 2.dp)
                        } else Modifier
                    )
            ) {
                Text(
                    text = "第 $week 周 · ${SemesterWeeks.dayLabel(date.dayOfWeek.value)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "%d-%02d-%02d".format(date.year, date.monthValue, date.dayOfMonth),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.mutedText,
                    maxLines = 1
                )
                if (onOpenCalendar != null) {
                    Text(
                        text = "点击查看日历 ›",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
            }

            // 右侧：添加 / 导入 / 更多
            SmallFloatingActionButton(
                onClick = onAdd,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "添加课程",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(6.dp))
            if (onOpenAi != null) {
                IconButton(onClick = onOpenAi, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = "AI 助手",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (onOpenTodo != null) {
                TodaysIconButton(onClick = onOpenTodo, count = todoCount)
                Spacer(Modifier.width(2.dp))
            }
            IconButton(onClick = onImport, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.FileUpload,
                    contentDescription = "导入课表",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }

            Box {
                IconButton(
                    onClick = { if (onOpenMenu != null) onOpenMenu() else menuOpen = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "更多",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(21.dp)
                    )
                }
                if (onOpenMenu == null) {
                    DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    modifier = Modifier.width(288.dp)
                ) {
                    WeekHeaderRow(selectedWeek = selectedWeek)
                    HorizontalDivider()
                    MenuAction(
                        icon = Icons.AutoMirrored.Filled.ArrowBackIos,
                        title = "上一周",
                        onClick = {
                            onPreviousWeek()
                            menuOpen = false
                        }
                    )
                    MenuAction(
                        icon = Icons.Filled.Today,
                        title = "回到本周",
                        onClick = {
                            onJumpToday()
                            menuOpen = false
                        }
                    )
                    MenuAction(
                        icon = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        title = "下一周",
                        onClick = {
                            onNextWeek()
                            menuOpen = false
                        }
                    )
                    HorizontalDivider()
                    MenuAction(
                        icon = if (darkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                        title = if (darkTheme) "切换到日间模式" else "切换到夜间模式",
                        onClick = {
                            onToggleDarkTheme()
                            menuOpen = false
                        }
                    )
                    MenuAction(
                        icon = if (simpleMode) Icons.Filled.ViewAgenda else Icons.Filled.GridView,
                        title = if (simpleMode) "关闭简洁模式" else "开启简洁模式（极简色块）",
                        trailing = { if (simpleMode) CheckMark() },
                        onClick = {
                            onToggleSimpleMode()
                            menuOpen = false
                        }
                    )
                    MenuAction(
                        icon = Icons.Filled.Schedule,
                        title = "作息时间设置",
                        onClick = {
                            onOpenPeriodEditor()
                            menuOpen = false
                        }
                    )
                    MenuAction(
                        icon = Icons.Filled.GridView,
                        title = "小组件设置",
                        onClick = {
                            onOpenWidgetHelp()
                            menuOpen = false
                        }
                    )
                    MenuAction(
                        icon = Icons.Filled.Today,
                        title = "课表设置",
                        onClick = {
                            onOpenSettings()
                            menuOpen = false
                        }
                    )
                    }
                }
            }
        }

        // 独立的周次切换条：不与标题争夺横向空间，也不影响标题单行显示
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
        ) {
            Text(
                text = "第 $selectedWeek 周",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.weight(1f))
            WeekNavButton(Icons.AutoMirrored.Filled.ArrowBackIos, "上一周", onPreviousWeek)
            Spacer(Modifier.width(4.dp))
            WeekNavButton(Icons.Filled.Today, "回到本周", onJumpToday)
            Spacer(Modifier.width(4.dp))
            WeekNavButton(Icons.AutoMirrored.Filled.ArrowForwardIos, "下一周", onNextWeek)
        }
    }
}

@Composable
private fun WeekNavButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(30.dp)) {
        Icon(
            icon,
            contentDescription = description,
            modifier = Modifier.size(15.dp),
            tint = LocalTimetableColors.current.mutedText
        )
    }
}

@Composable
private fun TodaysIconButton(onClick: () -> Unit, count: Int) {
    Box {
        IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Filled.CalendarMonth,
                contentDescription = "今日待办事项",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }
        if (count > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 2.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error)
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = if (count > 99) "99+" else count.toString(),
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onError,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** 三点菜单顶部的周次标题行（此前的周数拖动条已按需求移除）。 */
@Composable
private fun WeekHeaderRow(selectedWeek: Int) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            "周数",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "当前第 $selectedWeek 周，用下方「上一周 / 回到本周 / 下一周」切换",
            style = MaterialTheme.typography.labelSmall,
            color = LocalTimetableColors.current.mutedText
        )
    }
}

@Composable
private fun MenuAction(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    DropdownMenuItem(
        text = { Text(title, style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
        trailingIcon = trailing,
        onClick = onClick
    )
}

@Composable
private fun CheckMark() {
    Icon(
        Icons.Filled.Check,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
        tint = MaterialTheme.colorScheme.primary
    )
}

/** 顶部小徽章：显示「还有 N 节课」之类的统计。 */
@Composable
fun CountBadge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontSize = 11.sp
        )
    }
}

/** 圆形色点。 */
@Composable
fun ColorDot(color: androidx.compose.ui.graphics.Color, size: Int = 8, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(size.dp).clip(CircleShape).background(color))
}
