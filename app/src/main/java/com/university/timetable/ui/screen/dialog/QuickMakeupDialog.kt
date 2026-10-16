package com.university.timetable.ui.screen.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.university.timetable.ui.component.glassLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.university.timetable.data.repository.ClassBlock
import com.university.timetable.data.repository.MovePlan
import com.university.timetable.data.repository.MoveResult
import com.university.timetable.ui.theme.LocalTimetableColors
import com.university.timetable.util.SemesterWeeks
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 快捷调休。
 *
 * 把「原上课日期」当天应上的**全部**课程移到「调休至」当天：
 *  - 源日期按**它自己**的周次 / 单双周 / 有效周次筛选（界面当前翻到第几周不影响结果）；
 *  - 当天放假、界面看不到课时，依然能取到原本应上的课；
 *  - 只影响这两天，不改动整学期的重复排课规则；
 *  - 目标日期已有课程会保留，时间冲突只提示不覆盖；
 *  - 重复执行同一组日期不会产生重复课程。
 */
@Composable
fun QuickMakeupDialog(
    today: LocalDate,
    semesterStart: LocalDate,
    totalWeeks: Int,
    scope: kotlinx.coroutines.CoroutineScope,
    onPlan: suspend (LocalDate, LocalDate) -> MovePlan,
    onConfirm: suspend (LocalDate, LocalDate) -> MoveResult,
    onDismiss: () -> Unit
) {
    val colors = LocalTimetableColors.current
    val lastDay = remember(semesterStart, totalWeeks) {
        semesterStart.plusWeeks(totalWeeks.coerceAtLeast(1).toLong()).minusDays(1)
    }
    val clamp = remember(semesterStart, lastDay) {
        { d: LocalDate -> if (d < semesterStart) semesterStart else if (d > lastDay) lastDay else d }
    }

    var from by remember { mutableStateOf(clamp(today)) }
    var to by remember { mutableStateOf(clamp(today.plusDays(1))) }
    var picker by remember { mutableStateOf<Boolean?>(null) } // true=选「调休至」，false=选「原上课日期」
    var plan by remember { mutableStateOf<MovePlan?>(null) }
    var planning by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<MoveResult?>(null) }
    var working by remember { mutableStateOf(false) }

    // 任一日期变化就重新预演，让用户在执行前看到准确的影响范围
    LaunchedEffect(from, to) {
        planning = true
        error = null
        runCatching { onPlan(from, to) }
            .onSuccess { plan = it }
            .onFailure {
                plan = null
                error = it.message ?: "预演失败"
            }
        planning = false
    }

    picker?.let { isTarget ->
        val initial = if (isTarget) to else from
        MakeupDatePicker(
            initial = initial,
            firstAllowed = if (isTarget) from.plusDays(1) else semesterStart,
            lastAllowed = if (isTarget) lastDay else to.minusDays(1),
            title = if (isTarget) "选择调休至的日期" else "选择原上课日期",
            onDismiss = { picker = null },
            onPick = { picked ->
                if (isTarget) to = picked else from = picked
                picker = null
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 700.dp)
                .clip(RoundedCornerShape(24.dp))
                .glassLayer(MaterialTheme.colorScheme.surface)
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "快捷调休",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭")
                }
            }
            Text(
                text = "把原上课日期当天应上的全部课程移到目标日期，只影响这两天，不改动整学期的排课规则。",
                style = MaterialTheme.typography.labelSmall,
                color = colors.mutedText
            )

            Spacer(Modifier.height(12.dp))

            Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                DateField(
                    label = "原上课日期",
                    date = from,
                    semesterStart = semesterStart,
                    enabled = result == null,
                    onClick = { picker = false }
                )
                Spacer(Modifier.height(10.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.SwapHoriz,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.height(10.dp))
                DateField(
                    label = "调休至",
                    date = to,
                    semesterStart = semesterStart,
                    enabled = result == null,
                    onClick = { picker = true }
                )

                Spacer(Modifier.height(14.dp))

                when {
                    result != null -> ResultCard(result!!)

                    planning -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("正在检查当天课程…", style = MaterialTheme.typography.labelMedium, color = colors.mutedText)
                    }

                    error != null -> Text(
                        text = "无法预演：$error",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )

                    plan != null -> PlanCard(plan!!)
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(
                    enabled = !working && result == null && (plan?.courses?.isNotEmpty() == true),
                    onClick = {
                        working = true
                        scope.launch {
                            val r = runCatching { onConfirm(from, to) }.getOrElse {
                                MoveResult(0, emptyList(), emptyList(), "执行失败：${it.message}")
                            }
                            result = r
                            working = false
                        }
                    },
                    modifier = Modifier.weight(1.4f)
                ) { Text(if (result != null) "已完成" else "确认调休") }
            }
        }
    }
}

/**
 * 确认按钮的实际执行入口。
 *
 * 抽出来是为了让调用方（Compose 侧）提供协程作用域，避免在对话框内部持有 Job。
 */

@Composable
private fun DateField(
    label: String,
    date: LocalDate,
    semesterStart: LocalDate,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalTimetableColors.current
    val week = SemesterWeeks.weekOf(date, semesterStart).coerceAtLeast(1)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.mutedText)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                // 年份与星期同时显示，避免跨年或选错日期
                text = "${date.year} 年 ${date.monthValue} 月 ${date.dayOfMonth} 日 · " +
                    SemesterWeeks.dayLabel(date.dayOfWeek.value) + " · 第 $week 周",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Filled.CalendarMonth,
                contentDescription = "选择日期",
                tint = if (enabled) MaterialTheme.colorScheme.primary else colors.mutedText
            )
        }
    }
}

@Composable
private fun PlanCard(plan: MovePlan) {
    val colors = LocalTimetableColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp)
    ) {
        if (plan.courses.isEmpty()) {
            Text(
                text = "原上课日期没有需要移动的课程",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "该日期按排课规则本来就没有课，请确认选择的日期是否正确。",
                style = MaterialTheme.typography.labelSmall,
                color = colors.mutedText
            )
        } else {
            Text(
                text = "将移动 ${plan.courses.size} 门课程（按 ${plan.from} 第 ${plan.fromWeek} 周的排课）",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            plan.courses.forEach { ClassLine(it) }
        }

        if (plan.targetExisting.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "目标日期已有 ${plan.targetExisting.size} 门课程，将保留、不会被覆盖：",
                style = MaterialTheme.typography.labelMedium,
                color = colors.mutedText
            )
            Spacer(Modifier.height(4.dp))
            plan.targetExisting.forEach { ClassLine(it, faded = true) }
        }

        if (plan.hasConflicts) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "时间冲突：${plan.conflicts.joinToString("、") { it.name }}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        if (plan.alreadyApplied) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "该调休此前已执行过，再次确认不会重复添加课程。",
                style = MaterialTheme.typography.labelSmall,
                color = colors.mutedText
            )
        }
    }
}

@Composable
private fun ResultCard(result: MoveResult) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(14.dp)
    ) {
        Text(
            text = if (result.moved > 0) "已完成调休" else "无需重复调休",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = result.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun ClassLine(block: ClassBlock, faded: Boolean = false) {    val colors = LocalTimetableColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (faded) colors.mutedText else Color(block.color ?: DEFAULT_CLASS_COLOR))
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = block.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Text(
                text = "第${block.startPeriod}-${block.endPeriod}节 ${block.timeLabel}" +
                    (block.location?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = colors.mutedText,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
    }
}

/** 限定在学期范围内的日期选择器。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MakeupDatePicker(
    initial: LocalDate,
    firstAllowed: LocalDate,
    lastAllowed: LocalDate,
    title: String,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit
) {
    val lo = if (firstAllowed > lastAllowed) lastAllowed else firstAllowed
    val hi = if (firstAllowed > lastAllowed) firstAllowed else lastAllowed
    val selectable = remember(lo, hi) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val d = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                return d >= lo && d <= hi
            }
        }
    }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        yearRange = lo.year..hi.year,
        selectableDates = selectable
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis ?: return@TextButton
                onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 24.dp, top = 16.dp)
            )
            DatePicker(state = state)
        }
    }
}

/** 课程没有配色时使用的兜底色（与仓库层的默认色一致）。 */
private val DEFAULT_CLASS_COLOR = 0xFF4C7DFF.toInt()
