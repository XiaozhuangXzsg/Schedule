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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.university.timetable.ui.component.glassLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.university.timetable.data.entity.PeriodTime
import com.university.timetable.ui.theme.LocalTimetableColors

/** 课程节数上限，避免误操作产生无意义的超长课表。 */
private const val MAX_PERIODS = 20

/**
 * 作息时间编辑器：逐节修改开始 / 结束时间，并支持增删节数。
 *
 * 保存后时间轴与周视图会立即按新的节数与时间重新排版；
 * 如果减少了节数，超出范围的课程时段会被同步裁剪（见 ScheduleRepository.savePeriodTimes）。
 */
@Composable
fun PeriodTimeEditor(
    periods: List<PeriodTime>,
    onDismiss: () -> Unit,
    onSave: (List<PeriodTime>) -> Unit
) {
    val working = remember(periods) { mutableStateListOf<PeriodTime>().apply { addAll(periods) } }
    val colors = LocalTimetableColors.current
    var errorText by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 660.dp)
                .clip(RoundedCornerShape(24.dp))
                .glassLayer(MaterialTheme.colorScheme.surface)
                .padding(20.dp)
        ) {
            Text(
                text = "作息时间设置",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "格式 HH:mm，例如 08:00。可直接增删节数，保存后时间轴与本周课表会自动适配。",
                style = MaterialTheme.typography.labelSmall,
                color = colors.mutedText
            )

            Spacer(Modifier.height(10.dp))

            // ---- 节数调节栏 ----
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "每天 ${working.size} 节课",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "减少节数会同步裁剪超出该节次的课程",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.mutedText
                    )
                }
                // 删除最后一节
                IconButton(
                    onClick = {
                        if (working.size > 1) {
                            working.removeAt(working.size - 1)
                            errorText = null
                        }
                    },
                    enabled = working.size > 1,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "减少一节",
                        tint = if (working.size > 1) {
                            MaterialTheme.colorScheme.error
                        } else {
                            colors.mutedText.copy(alpha = 0.4f)
                        }
                    )
                }
                // 新增一节
                IconButton(
                    onClick = {
                        if (working.size < MAX_PERIODS) {
                            working.add(nextPeriodOf(working))
                            errorText = null
                        }
                    },
                    enabled = working.size < MAX_PERIODS,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "增加一节",
                        tint = if (working.size < MAX_PERIODS) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            colors.mutedText.copy(alpha = 0.4f)
                        }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                working.forEachIndexed { index, period ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.width(64.dp)
                        ) {
                            Text(
                                text = "第 ${period.period} 节",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        OutlinedTextField(
                            value = period.startTime,
                            onValueChange = { v ->
                                working[index] = period.copy(startTime = v)
                            },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = period.endTime,
                            onValueChange = { v ->
                                working[index] = period.copy(endTime = v)
                            },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(4.dp))
                        // 删除任意一节：后面节次自动前移重新编号
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .clickable(enabled = working.size > 1) {
                                    if (working.size > 1) {
                                        working.removeAt(index)
                                        renumber(working)
                                        errorText = null
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "删除第 ${period.period} 节",
                                tint = if (working.size > 1) {
                                    colors.mutedText
                                } else {
                                    colors.mutedText.copy(alpha = 0.3f)
                                },
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            errorText?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(
                    onClick = {
                        val invalid = working.firstOrNull { p ->
                            !TIME_PATTERN.matches(p.startTime) || !TIME_PATTERN.matches(p.endTime)
                        }
                        if (invalid != null) {
                            errorText = "第 ${invalid.period} 节时间格式不正确，应为 HH:mm"
                        } else if (working.isEmpty()) {
                            errorText = "至少需要保留 1 节课"
                        } else {
                            onSave(working.toList())
                        }
                    },
                    modifier = Modifier.weight(1.4f)
                ) { Text("保存") }
            }
        }
    }
}

/** 追加一节：优先接在上一节结束时间之后（时长 45 分钟），否则用 08:00 起算。 */
private fun nextPeriodOf(current: List<PeriodTime>): PeriodTime {
    val period = (current.maxOfOrNull { it.period } ?: 0) + 1
    val lastEnd = current.lastOrNull()?.endTime
    val start = lastEnd?.let { addMinutes(it, 5) } ?: "08:00"
    return PeriodTime(period = period, startTime = start, endTime = addMinutes(start, 45))
}

/** 删除后重新编号，保证节次从 1 连续递增。 */
private fun renumber(list: MutableList<PeriodTime>) {
    val snapshot = list.toList()
    list.clear()
    snapshot.forEachIndexed { index, p -> list.add(p.copy(period = index + 1)) }
}

/** 对 "HH:mm" 增加分钟数；解析失败时原样返回。 */
private fun addMinutes(time: String, minutes: Int): String {
    val parts = time.split(":")
    val h = parts.getOrNull(0)?.toIntOrNull() ?: return time
    val m = parts.getOrNull(1)?.toIntOrNull() ?: return time
    val total = (h * 60 + m + minutes).coerceIn(0, 24 * 60 - 1)
    return "%02d:%02d".format(total / 60, total % 60)
}

private val TIME_PATTERN = Regex("""^\d{1,2}:\d{2}$""")
