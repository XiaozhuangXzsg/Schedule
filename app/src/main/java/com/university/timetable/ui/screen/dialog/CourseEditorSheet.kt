package com.university.timetable.ui.screen.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.university.timetable.data.entity.Course
import com.university.timetable.data.entity.PeriodTime
import com.university.timetable.data.entity.WeekParity
import com.university.timetable.ui.CourseEditorData
import com.university.timetable.ui.EditorTarget
import com.university.timetable.ui.theme.CoursePalette
import com.university.timetable.ui.theme.LocalTimetableColors
import com.university.timetable.util.SemesterWeeks

/**
 * 课程快速编辑弹窗：新增或修改课程名称、教师、地点、颜色、节次与周次。
 */
@Composable
fun CourseEditorSheet(
    target: EditorTarget,
    periods: List<PeriodTime>,
    onDismiss: () -> Unit,
    onSave: (CourseEditorData) -> Unit
) {
    val existingBlock = (target as? EditorTarget.Existing)?.block
    val initialDay = when (target) {
        is EditorTarget.New -> target.dayOfWeek
        is EditorTarget.Existing -> target.block.dayOfWeek
    }
    val initialStart = when (target) {
        is EditorTarget.New -> target.startPeriod
        is EditorTarget.Existing -> target.block.startPeriod
    }
    val existingCourse = remember(existingBlock) {
        existingBlock?.let {
            Course(
                id = it.courseId,
                name = it.name,
                teacher = it.teacher,
                color = it.color,
                category = it.category
            )
        }
    }

    var name by remember { mutableStateOf(existingBlock?.name ?: "") }
    var teacher by remember { mutableStateOf(existingBlock?.teacher ?: "") }
    var location by remember { mutableStateOf(existingBlock?.location ?: "") }
    var category by remember { mutableStateOf(existingBlock?.category ?: "课程") }
    var day by remember { mutableIntStateOf(initialDay) }
    var startPeriod by remember { mutableIntStateOf(initialStart) }
    var endPeriod by remember {
        mutableIntStateOf(existingBlock?.endPeriod ?: initialStart)
    }
    var startWeek by remember { mutableIntStateOf(existingBlock?.startWeek ?: 1) }
    var endWeek by remember { mutableIntStateOf(existingBlock?.endWeek ?: 18) }
    var parity by remember { mutableIntStateOf(existingBlock?.weekParity ?: WeekParity.EVERY) }
    var customColor by remember {
        mutableStateOf<Int?>(existingBlock?.color)
    }

    val colors = LocalTimetableColors.current
    val palette = CoursePalette.lightPalette

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (existingBlock == null) "添加课程" else "编辑课程",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭")
                }
            }

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("课程名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = teacher,
                onValueChange = { teacher = it },
                label = { Text("授课教师（可选）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("上课地点（可选）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))
            FieldLabel("星期")
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..7).forEach { d ->
                    SelectableChip(
                        text = SemesterWeeks.dayChar(d),
                        selected = day == d,
                        onClick = { day = d }
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            FieldLabel("节次")
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PeriodDropdown(
                    label = "开始",
                    value = startPeriod,
                    periods = periods,
                    onSelect = {
                        startPeriod = it
                        if (endPeriod < startPeriod) endPeriod = startPeriod
                    }
                )
                Spacer(Modifier.width(12.dp))
                PeriodDropdown(
                    label = "结束",
                    value = endPeriod,
                    periods = periods,
                    onSelect = { endPeriod = it.coerceAtLeast(startPeriod) }
                )
            }

            Spacer(Modifier.height(14.dp))
            FieldLabel("周次范围：第 $startWeek - $endWeek 周")
            RangeSlider(
                value = startWeek.toFloat()..endWeek.toFloat(),
                onValueChange = { range ->
                    startWeek = range.start.toInt().coerceAtLeast(1)
                    endWeek = range.endInclusive.toInt().coerceAtLeast(startWeek)
                },
                valueRange = 1f..20f,
                steps = 18
            )

            Spacer(Modifier.height(6.dp))
            FieldLabel("单双周")
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    WeekParity.EVERY to "每周",
                    WeekParity.ODD to "单周",
                    WeekParity.EVEN to "双周"
                ).forEach { (value, label) ->
                    SelectableChip(
                        text = label,
                        selected = parity == value,
                        onClick = { parity = value }
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            FieldLabel("课程颜色")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                palette.forEachIndexed { index, (container, _) ->
                    val selected = customColor == container.value.toInt()
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(container)
                            .then(
                                if (selected) {
                                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                } else Modifier
                            )
                            .clickable {
                                customColor = container.value.toInt()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .border(1.dp, colors.gridLine, CircleShape)
                        .clickable { customColor = null },
                    contentAlignment = Alignment.Center
                ) {
                    Text("自动", fontSize = 8.sp, color = colors.mutedText)
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("取消")
                }
                Button(
                    onClick = {
                        if (name.isBlank()) return@Button
                        onSave(
                            CourseEditorData(
                                existing = existingCourse,
                                name = name.trim(),
                                teacher = teacher.trim().ifBlank { null },
                                location = location.trim().ifBlank { null },
                                color = customColor,
                                category = category,
                                dayOfWeek = day,
                                startPeriod = startPeriod,
                                endPeriod = endPeriod,
                                startWeek = startWeek,
                                endWeek = endWeek,
                                parity = parity
                            )
                        )
                    },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.weight(1.4f)
                ) {
                    Text("保存")
                }
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = LocalTimetableColors.current.mutedText
    )
}

@Composable
private fun SelectableChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 34.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun PeriodDropdown(
    label: String,
    value: Int,
    periods: List<PeriodTime>,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val periodText = periods.firstOrNull { it.period == value }?.let {
        "第 $value 节 ${it.startTime}"
    } ?: "第 $value 节"

    Box {
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = LocalTimetableColors.current.mutedText
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                Text(periodText, style = MaterialTheme.typography.bodyMedium)
            }
        }
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            periods.forEach { p ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("第 ${p.period} 节 · ${p.startTime}-${p.endTime}") },
                    onClick = {
                        onSelect(p.period)
                        expanded = false
                    }
                )
            }
        }
    }
}
