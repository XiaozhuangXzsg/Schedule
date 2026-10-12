package com.university.timetable.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shower
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.university.timetable.data.repository.FacilityStatus
import com.university.timetable.ui.LaundryTimer
import com.university.timetable.ui.theme.LocalTimetableColors
import kotlinx.coroutines.delay

/**
 * 今日页的「校园生活助手」卡片。
 *
 * - 顶部整行可点击 → 进入「校园生活」子页面查看完整作息
 * - 中部显示前几项设施的实时开放状态
 * - 底部为洗衣机倒计时器
 */
@Composable
fun LifeAssistantCard(
    statuses: List<FacilityStatus>,
    timers: List<LaundryTimer>,
    nowMinutes: Int,
    onOpenCampusLife: () -> Unit,
    onStartTimer: (minutes: Int, label: String) -> Unit,
    onCancelTimer: (id: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalTimetableColors.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.surfaceCard)
            .padding(16.dp)
    ) {
        // 标题行：整行可点进入子页面
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onOpenCampusLife)
                .padding(vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "校园生活",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "宿舍供电 · 浴室 · 洗衣机开放时间",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.mutedText
                )
            }
            Text(
                text = "%02d:%02d".format(nowMinutes / 60, nowMinutes % 60),
                style = MaterialTheme.typography.labelMedium,
                color = colors.mutedText
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = "查看全部",
                tint = colors.mutedText,
                modifier = Modifier.size(14.dp)
            )
        }

        Spacer(Modifier.height(10.dp))

        if (statuses.isEmpty()) {
            Text(
                text = "还没有校园生活条目，点击上方进入添加。",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.mutedText
            )
        } else {
            statuses.take(4).forEach { status ->
                CompactStatusRow(status = status, nowMinutes = nowMinutes)
                Spacer(Modifier.height(8.dp))
            }
            if (statuses.size > 4) {
                Text(
                    text = "还有 ${statuses.size - 4} 项，点击上方查看全部 →",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenCampusLife)
                        .padding(vertical = 2.dp)
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        LaundryTimerSection(
            timers = timers,
            onStartTimer = onStartTimer,
            onCancelTimer = onCancelTimer
        )
    }
}

@Composable
private fun CompactStatusRow(status: FacilityStatus, nowMinutes: Int) {
    val colors = LocalTimetableColors.current
    val accent = when {
        status.closedAllDay -> colors.neutral
        status.isOpen -> colors.success
        else -> colors.warning
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = iconFor(status.facility.type),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = buildString {
                    append(status.facility.name)
                    status.facility.location?.takeIf { it.isNotBlank() }?.let { append("（$it）") }
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = if (status.closedAllDay) "今日不开放"
                else status.windows.joinToString(" & ") { it.label() },
                style = MaterialTheme.typography.labelSmall,
                color = colors.mutedText,
                maxLines = 1
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(accent.copy(alpha = 0.14f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = when {
                    status.closedAllDay -> "休息"
                    status.isOpen -> "开放中"
                    status.nextWindow != null -> "未开放"
                    else -> "已结束"
                },
                fontSize = 10.sp,
                color = accent,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun iconFor(type: String): ImageVector = when (type) {
    "POWER" -> Icons.Filled.Bolt
    "BATH" -> Icons.Filled.Shower
    "LAUNDRY", "DRYER", "CHANGE", "SHOE" -> Icons.Filled.LocalLaundryService
    else -> Icons.Filled.Schedule
}

@Composable
private fun LaundryTimerSection(
    timers: List<LaundryTimer>,
    onStartTimer: (Int, String) -> Unit,
    onCancelTimer: (String) -> Unit
) {
    val colors = LocalTimetableColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.LocalLaundryService,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "洗衣机倒计时",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(8.dp))

        if (timers.isEmpty()) {
            // 2×2 网格，保证「60 分钟」这类较长标签不会被压成两行
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(listOf(30, 40), listOf(45, 60)).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { minutes ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .clickable { onStartTimer(minutes, "洗衣机 $minutes 分钟") }
                                    .padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$minutes 分钟",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "点击开始计时，完成后会收到通知提醒",
                style = MaterialTheme.typography.labelSmall,
                color = colors.mutedText
            )
        } else {
            timers.forEach { timer ->
                CountdownRow(timer = timer, onCancel = { onCancelTimer(timer.id) })
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun CountdownRow(timer: LaundryTimer, onCancel: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(timer.id) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L)
        }
    }
    val remaining = ((timer.endAtMillis - now).coerceAtLeast(0L)) / 1000L
    val minutes = remaining / 60
    val seconds = remaining % 60

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (remaining > 0) MaterialTheme.colorScheme.primary
                    else LocalTimetableColors.current.success
                )
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = timer.label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        Text(
            text = "%02d:%02d".format(minutes, seconds),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.Filled.Close,
            contentDescription = "取消",
            modifier = Modifier
                .size(18.dp)
                .clickable { onCancel() },
            tint = LocalTimetableColors.current.mutedText
        )
    }
}
