package com.university.timetable.ui.screen.sheet

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.university.timetable.data.entity.WeekParity
import com.university.timetable.data.repository.ClassBlock
import com.university.timetable.ui.theme.CoursePalette
import com.university.timetable.ui.theme.LocalIsDark
import com.university.timetable.ui.theme.LocalTimetableColors
import com.university.timetable.util.SemesterWeeks

/**
 * 点击课程后弹出的操作面板：编辑 / 删除该时段 / 清空当天。
 */
@Composable
fun ClassActionsSheet(
    block: ClassBlock,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onClearDay: () -> Unit
) {
    val dark = LocalIsDark.current
    val colors = LocalTimetableColors.current
    val (container, content) = CoursePalette.colorsFor(block.courseId, block.color, dark)

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp)
        ) {
            // 课程信息摘要
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(container),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = block.name.take(2),
                        color = content,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = block.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = buildString {
                            append(SemesterWeeks.dayLabel(block.dayOfWeek))
                            append(" 第${block.periodLabel}节 ")
                            append(block.timeLabel)
                            block.location?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.mutedText
                    )
                    Text(
                        text = buildString {
                            append("${block.startWeek}-${block.endWeek} 周")
                            if (block.weekParity != WeekParity.EVERY) {
                                append("（${WeekParity.label(block.weekParity)}）")
                            }
                            block.teacher?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.mutedText
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            SheetAction(Icons.Filled.Edit, "编辑课程信息", "修改名称、地点、教师、颜色与周次", onEdit)
            SheetAction(Icons.Filled.Delete, "删除该上课时段", "仅移除这一次安排", onDelete)
            SheetAction(
                Icons.Filled.DeleteSweep,
                "清空${SemesterWeeks.dayLabel(block.dayOfWeek)}全部课程",
                "快速清空这一天的课表",
                onClearDay,
                destructive = true
            )

            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surfaceCardElevated)
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("取消", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SheetAction(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    destructive: Boolean = false
) {
    val colors = LocalTimetableColors.current
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = colors.mutedText)
        }
    }
}
