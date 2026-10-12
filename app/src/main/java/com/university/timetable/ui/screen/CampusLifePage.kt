package com.university.timetable.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shower
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import com.university.timetable.data.entity.CampusFacility
import com.university.timetable.data.entity.FacilityWindow
import com.university.timetable.data.entity.TimeWindowCodec
import com.university.timetable.data.repository.FacilityScheduleResolver
import com.university.timetable.data.repository.FacilityStatus
import com.university.timetable.ui.CampusUiState
import com.university.timetable.ui.MainViewModel
import com.university.timetable.ui.component.CourseCard
import com.university.timetable.ui.component.SectionHeader
import com.university.timetable.ui.screen.dialog.FacilityEditorDialog
import com.university.timetable.ui.theme.LocalTimetableColors
import com.university.timetable.util.SemesterWeeks

/**
 * 校园生活子页面：按「今天」的日期与节假日显示各设施的开放情况。
 *
 * 展示内容：
 *  - 公寓开关门时间
 *  - 宿舍供电（限电）时间
 *  - 浴室开放时间
 *  - 滚筒洗衣机 / 烘干机 / 换桶机 / 洗鞋机 运行时间与楼层位置
 *  - 吹风机 / 饮水机 / 自习室 等
 *
 * 所有条目与时间规则均可自由添加、修改、删除。
 */
@Composable
fun CampusLifePage(
    viewModel: MainViewModel,
    state: CampusUiState,
    onBack: () -> Unit
) {
    val colors = LocalTimetableColors.current
    var confirmReset by remember { mutableStateOf(false) }
    // 编辑 / 新增目标：null = 新增；非 null = 编辑该条目
    var editorOpen by remember { mutableStateOf(false) }
    var editorTarget by remember { mutableStateOf<CampusFacility?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶栏（顶部留出状态栏安全区）
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(WindowInsets.statusBars.asPaddingValues())
                .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 4.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBackIos, contentDescription = "返回", modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "校园生活",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = "%d-%02d-%02d %s · %s".format(
                        state.date.year, state.date.monthValue, state.date.dayOfMonth,
                        SemesterWeeks.dayLabel(state.date.dayOfWeek.value),
                        nowLabel(state.nowMinutes)
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.mutedText,
                    maxLines = 1
                )
            }
            IconButton(
                onClick = {
                    editorTarget = null
                    editorOpen = true
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "新增设施")
            }
            IconButton(onClick = { confirmReset = true }, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Filled.Restore,
                    contentDescription = "恢复默认",
                    tint = colors.mutedText,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 96.dp)
        ) {
            // 今日概览
            item {
                TodayOverview(state)
                Spacer(Modifier.height(14.dp))
            }

            if (state.isHoliday) {
                item {
                    HolidayNotice(state.holidayName ?: "节假日")
                    Spacer(Modifier.height(12.dp))
                }
            }

            // 洗澡专用入口：一眼看到「现在能不能去洗」
            val bath = state.statuses.firstOrNull { it.facility.type == "BATH" }
            if (bath != null) {
                item {
                    BathEntryCard(
                        status = bath,
                        nowMinutes = state.nowMinutes,
                        onOpen = {
                            editorTarget = bath.facility
                            editorOpen = true
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }

            item {
                SectionHeader(
                    title = "今日开放情况",
                    subtitle = "${state.openCount}/${state.statuses.size} 项开放中"
                )
                Spacer(Modifier.height(8.dp))
            }

            items(state.statuses, key = { it.facility.id }) { status ->
                FacilityCard(
                    status = status,
                    nowMinutes = state.nowMinutes,
                    onEdit = {
                        editorTarget = status.facility
                        editorOpen = true
                    }
                )
                Spacer(Modifier.height(10.dp))
            }

            if (state.statuses.isEmpty()) {
                item {
                    Text(
                        text = "还没有校园生活条目，点右上角「+」添加。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.mutedText,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }

            item { Spacer(Modifier.height(20.dp)) }
        }
    }

    if (editorOpen) {
        FacilityEditorDialog(
            existing = editorTarget,
            onDismiss = { editorOpen = false },
            onDelete = { facility ->
                viewModel.deleteFacility(facility.id)
                editorOpen = false
            },
            onSave = { facility ->
                viewModel.upsertFacility(facility)
                editorOpen = false
            }
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("恢复默认校园生活数据？") },
            text = { Text("将用学校默认作息覆盖当前所有条目，你的自定义修改会丢失。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetFacilities()
                    confirmReset = false
                }) { Text("恢复默认", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("取消") }
            }
        )
    }
}

private fun nowLabel(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

@Composable
private fun TodayOverview(state: CampusUiState) {
    val colors = LocalTimetableColors.current

    // 找出最近一次状态变化（关闭 / 开放）
    data class Upcoming(val minutes: Int, val action: String, val status: FacilityStatus)

    val nextChange = state.statuses
        .mapNotNull { status ->
            when {
                status.isOpen -> status.minutesUntilClose(state.nowMinutes)
                    ?.let { Upcoming(it, "关闭", status) }

                status.nextWindow != null -> status.minutesUntilOpen(state.nowMinutes)
                    ?.let { Upcoming(it, "开放", status) }

                else -> null
            }
        }
        .minByOrNull { it.minutes }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceCard)
            .padding(16.dp)
    ) {
        Text(
            text = "今天的校园作息",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (state.isHoliday) {
                "今天是${state.holidayName ?: "节假日"}，已按节假日安排显示"
            } else {
                "已按${FacilityScheduleResolver.dayLabel(state.date.dayOfWeek.value)}的安排显示"
            },
            style = MaterialTheme.typography.labelMedium,
            color = colors.mutedText
        )
        if (nextChange != null) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${nextChange.status.facility.name} 将于 " +
                        "${nextChange.minutes / 60} 小时 ${nextChange.minutes % 60} 分钟后${nextChange.action}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * 洗澡专用入口卡片。
 *
 * 目标是「一眼知道现在能不能去洗，下一场什么时候开」：
 * 大号状态 + 今天的时段 + 倒计时，右侧一个「浴室开放」图标徽章。
 */
@Composable
private fun BathEntryCard(
    status: FacilityStatus,
    nowMinutes: Int,
    onOpen: () -> Unit
) {
    val colors = LocalTimetableColors.current
    val open = status.isOpen
    val accent = when {
        open -> colors.success
        status.nextWindow != null -> colors.warning
        else -> colors.neutral
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.surfaceCard)
            .clickable(onClick = onOpen)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 图标徽章
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(
                                accent.copy(alpha = 0.22f),
                                accent.copy(alpha = 0.10f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Shower,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "浴室开放",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = status.facility.location?.takeIf { it.isNotBlank() } ?: "公共浴室",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.mutedText
                )
            }
            // 状态徽章
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.14f))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = when {
                        status.closedAllDay -> "今日停开"
                        open -> "可以去了"
                        status.nextWindow != null -> "还没开门"
                        else -> "今日已结束"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // 今天的时段（按天/节假日动态）
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            status.windows.forEach { window ->
                val isCurrent = window.contains(nowMinutes)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isCurrent) accent.copy(alpha = 0.16f)
                            else colors.surfaceCardElevated
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = window.label(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isCurrent) accent else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // 倒计时提示
        val hint = when {
            open -> status.minutesUntilClose(nowMinutes)?.let {
                "还有 ${it / 60} 小时 ${it % 60} 分钟关闭"
            }

            status.nextWindow != null -> status.minutesUntilOpen(nowMinutes)?.let {
                "距离下一场还有 ${it / 60} 小时 ${it % 60} 分钟"
            }

            else -> null
        }
        if (hint != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = accent,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        status.facility.note?.takeIf { it.isNotBlank() }?.let { note ->
            Spacer(Modifier.height(6.dp))
            Text(
                text = note,
                style = MaterialTheme.typography.labelSmall,
                color = colors.mutedText
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "点击可修改浴室开放时间 →",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun HolidayNotice(name: String) {    val colors = LocalTimetableColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.holidayContainer)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            "假期",
            style = MaterialTheme.typography.labelMedium,
            color = colors.onHolidayContainer,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "$name：部分设施可能调整开放时间",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onHolidayContainer
        )
    }
}

@Composable
private fun FacilityCard(
    status: FacilityStatus,
    nowMinutes: Int,
    onEdit: () -> Unit
) {
    val colors = LocalTimetableColors.current
    val facility = status.facility
    val accent = if (status.isOpen) colors.success else colors.neutral

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surfaceCard)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = facilityIcon(facility.type),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = facility.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    facility.location?.takeIf { it.isNotBlank() }?.let { loc ->
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = loc,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                Text(
                    text = if (status.closedAllDay) {
                        "今日不开放"
                    } else {
                        status.windows.joinToString("  &  ") { it.label() }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.mutedText
                )
            }

            StatusChip(status = status, nowMinutes = nowMinutes)

            IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "编辑",
                    modifier = Modifier.size(17.dp),
                    tint = colors.mutedText
                )
            }
        }

        // 数据来源 + 备注
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Schedule,
                contentDescription = null,
                tint = colors.mutedText,
                modifier = Modifier.size(12.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = status.sourceLabel,
                style = MaterialTheme.typography.labelSmall,
                color = colors.mutedText
            )
            if (status.isOpen) {
                status.minutesUntilClose(nowMinutes)?.let { left ->
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "还有 ${left / 60}h${left % 60}m 关闭",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        facility.note?.takeIf { it.isNotBlank() }?.let { note ->
            Spacer(Modifier.height(4.dp))
            Text(
                text = note,
                style = MaterialTheme.typography.labelSmall,
                color = colors.mutedText
            )
        }
    }
}

@Composable
private fun StatusChip(status: FacilityStatus, nowMinutes: Int) {
    val colors = LocalTimetableColors.current
    val (container, content, label) = when {
        status.closedAllDay ->
            Triple(colors.neutralContainer, colors.onNeutralContainer, "休息")

        status.isOpen ->
            Triple(colors.successContainer, colors.onSuccessContainer, "开放中")

        status.nextWindow != null ->
            Triple(colors.warningContainer, colors.onWarningContainer, "未开放")

        else ->
            Triple(colors.neutralContainer, colors.onNeutralContainer, "已结束")
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(container)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text = label, fontSize = 10.sp, color = content, fontWeight = FontWeight.SemiBold)
    }
}

internal fun facilityIcon(type: String) = when (type) {
    "LAUNDRY" -> Icons.Filled.LocalLaundryService
    "DRYER" -> Icons.Filled.LocalLaundryService
    "CHANGE" -> Icons.Filled.LocalLaundryService
    "SHOE" -> Icons.Filled.LocalLaundryService
    else -> Icons.Filled.Schedule
}

@Suppress("unused")
private fun windowLabels(facility: CampusFacility): List<String> =
    TimeWindowCodec.decode(facility.windows).map { it.label() }

@Suppress("unused")
private fun sampleWindow() = FacilityWindow("08:00", "22:00")
