package com.university.timetable.ui.screen.dialog

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.university.timetable.data.entity.Course
import com.university.timetable.data.entity.WeekParity
import com.university.timetable.ui.theme.CoursePalette
import com.university.timetable.ui.theme.LocalTimetableColors
import com.university.timetable.util.SemesterWeeks

/**
 * 全屏覆盖层：用于「校园生活」这类子页面。
 * 自带返回键处理与安全区留白。
 */
@Composable
fun FullScreenOverlay(
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    BackHandler(onBack = onBack)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        content()
    }
}

/**
 * 「已添课程」列表：查看并删除已添加的课程。
 */
@Composable
fun AddedCoursesDialog(
    courses: List<Course>,
    onDismiss: () -> Unit,
    onDeleteCourse: (Long) -> Unit
) {
    val colors = LocalTimetableColors.current

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "已添课程",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${courses.size} 门",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.mutedText
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭")
                }
            }

            Spacer(Modifier.height(8.dp))

            if (courses.isEmpty()) {
                Text(
                    text = "还没有添加课程。可以在「本周课表」长按空白格快速添加。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.mutedText,
                    modifier = Modifier.padding(vertical = 20.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(courses, key = { it.id }) { course ->
                        val (container) = CoursePalette.colorsFor(
                            course.id, course.color, dark = false
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.surfaceCardElevated)
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(container)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = course.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                                Text(
                                    text = buildString {
                                        course.teacher?.takeIf { it.isNotBlank() }?.let { append("$it · ") }
                                        append(course.category)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.mutedText,
                                    maxLines = 1
                                )
                            }
                            IconButton(
                                onClick = { onDeleteCourse(course.id) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "删除${course.name}",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = "删除课程会同时移除它的所有上课时段",
                style = MaterialTheme.typography.labelSmall,
                color = colors.mutedText
            )
        }
    }
}

@Suppress("unused")
private fun parityLabel(parity: Int) = WeekParity.label(parity)

@Suppress("unused")
private fun dayRef(day: Int) = SemesterWeeks.dayLabel(day)

@Suppress("unused")
private fun unusedClickable() = Modifier.clickable {}
