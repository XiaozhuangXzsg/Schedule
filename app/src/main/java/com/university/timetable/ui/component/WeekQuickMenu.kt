package com.university.timetable.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.university.timetable.ui.theme.LocalTimetableColors

/**
 * 「本周课表」三点菜单：居中的面板式 UI。
 *
 * 上半部分为周数拖动条与课表切换，下半部分为 2×4 的图标功能网格。
 */
@Composable
fun WeekQuickMenu(
    selectedWeek: Int,
    totalWeeks: Int,
    onSelectWeek: (Int) -> Unit,
    onJumpToCurrentWeek: () -> Unit,
    simpleMode: Boolean,
    darkTheme: Boolean,
    onToggleSimpleMode: () -> Unit,
    onToggleDarkTheme: () -> Unit,
    onOpenPeriodEditor: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAddedCourses: () -> Unit,
    onOpenMakeup: () -> Unit,
    onOpenReminderSettings: () -> Unit,
    onContact: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalTimetableColors.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .clip(RoundedCornerShape(26.dp))
                .glassLayer(MaterialTheme.colorScheme.surface)
                .padding(18.dp)
        ) {
            Column {
                // ---- 周数 ----
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "周数",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "修改当前周",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onJumpToCurrentWeek)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(Modifier.height(2.dp))
                Slider(
                    value = selectedWeek.toFloat(),
                    onValueChange = { onSelectWeek(it.toInt().coerceIn(1, totalWeeks)) },
                    valueRange = 1f..totalWeeks.toFloat().coerceAtLeast(1f),
                    steps = (totalWeeks - 2).coerceAtLeast(0)
                )

                Spacer(Modifier.height(10.dp))

                // ---- 课表 ----
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "课表",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "当前第 $selectedWeek 周",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.mutedText
                    )
                }
                Spacer(Modifier.height(10.dp))

                // 当前课表卡片
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "默认课表",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(Modifier.height(18.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.gridLine)
                )
                Spacer(Modifier.height(14.dp))

                // ---- 第一行：课表相关 ----
                Row(modifier = Modifier.fillMaxWidth()) {
                    GridAction("上课时间", Icons.Filled.Schedule, Modifier.weight(1f), onOpenPeriodEditor)
                    GridAction("课表设置", Icons.Filled.GridView, Modifier.weight(1f), onOpenSettings)
                    GridAction("已添课程", Icons.Filled.ViewAgenda, Modifier.weight(1f), onOpenAddedCourses)
                    GridAction("调休", Icons.Filled.SwapHoriz, Modifier.weight(1f), onOpenMakeup)
                }

                Spacer(Modifier.height(18.dp))

                // ---- 第二行：其他 ----
                Row(modifier = Modifier.fillMaxWidth()) {
                    GridAction("联系我们", Icons.Filled.Sms, Modifier.weight(1f), onContact)
                    GridAction("上课提醒", Icons.Filled.NotificationsActive, Modifier.weight(1f), onOpenReminderSettings)
                    GridAction(
                        "夜间模式",
                        if (darkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                        Modifier.weight(1f),
                        onToggleDarkTheme,
                        active = darkTheme
                    )
                    GridAction(
                        "简洁模式",
                        if (simpleMode) Icons.Filled.ViewAgenda else Icons.Filled.GridView,
                        Modifier.weight(1f),
                        onToggleSimpleMode,
                        active = simpleMode
                    )
                }
            }
        }
    }
}

@Composable
private fun GridAction(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    active: Boolean = false
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = if (active) MaterialTheme.colorScheme.primary else LocalTimetableColors.current.mutedText,
            maxLines = 1
        )
    }
}
