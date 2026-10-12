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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.university.timetable.data.entity.CampusFacility
import com.university.timetable.data.entity.FacilityWindow
import com.university.timetable.data.entity.TimeWindowCodec
import com.university.timetable.ui.theme.LocalTimetableColors

/** 设施类型候选项。 */
private val FacilityTypes = listOf(
    "DOOR" to "公寓门禁",
    "POWER" to "宿舍供电",
    "BATH" to "浴室",
    "LAUNDRY" to "洗衣机",
    "DRYER" to "烘干机",
    "CHANGE" to "换桶机",
    "SHOE" to "洗鞋机",
    "HAIRDRYER" to "吹风机",
    "WATER" to "饮水机",
    "STUDY" to "自习室",
    "CUSTOM" to "其他"
)

private val DayLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/**
 * 校园生活条目编辑器。
 *
 * 时间可分别设置：
 *  - **默认**：每天通用的开放区间
 *  - **按星期**：为某一天单独覆盖（例如周一浴室上午停开检修）
 *  - **节假日**：放假时使用的区间
 */
@Composable
fun FacilityEditorDialog(
    existing: CampusFacility?,
    onDismiss: () -> Unit,
    onDelete: ((CampusFacility) -> Unit)?,
    onSave: (CampusFacility) -> Unit
) {
    val colors = LocalTimetableColors.current

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: "CUSTOM") }
    var location by remember { mutableStateOf(existing?.location ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }

    var defaultWindows by remember {
        mutableStateOf(TimeWindowCodec.decode(existing?.windows).ifEmpty { listOf(FacilityWindow("08:00", "22:00")) })
    }
    var dayWindows by remember {
        mutableStateOf(TimeWindowCodec.decodeDays(existing?.dayWindows).toMutableList())
    }
    var holidayWindows by remember {
        mutableStateOf(TimeWindowCodec.decode(existing?.holidayWindows))
    }

    // 0=默认 1=按星期 2=节假日
    var tab by remember { mutableIntStateOf(0) }
    var selectedDay by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 660.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (existing == null) "新增校园生活条目" else "编辑：${existing.name}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭")
                }
            }

            Spacer(Modifier.height(8.dp))

            Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称，例如 滚筒洗衣机") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("位置，例如 1-6 层 / 2 层、5 层") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))
                Text("类型", style = MaterialTheme.typography.labelLarge, color = colors.mutedText)
                Spacer(Modifier.height(6.dp))
                ChipFlow(
                    items = FacilityTypes.map { it.second },
                    selectedIndex = FacilityTypes.indexOfFirst { it.first == type }.coerceAtLeast(0),
                    onSelect = { type = FacilityTypes[it].first }
                )
                Text(
                    text = FacilityTypes.firstOrNull { it.first == type }?.first ?: "CUSTOM",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.mutedText
                )

                Spacer(Modifier.height(14.dp))
                // 时间范围切换
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("默认", "按星期", "节假日").forEachIndexed { index, label ->
                        SelectablePill(
                            text = label,
                            selected = tab == index,
                            onClick = { tab = index }
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                when (tab) {
                    0 -> {
                        Text(
                            text = "默认开放时间（每天通用，可添加多段，例如 11:00-14:00 与 16:30-23:00）",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.mutedText
                        )
                        Spacer(Modifier.height(6.dp))
                        WindowListEditor(
                            windows = defaultWindows,
                            onChange = { defaultWindows = it }
                        )
                    }

                    1 -> {
                        Text(
                            text = "为某一天单独设置（留空表示沿用默认）",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.mutedText
                        )
                        Spacer(Modifier.height(6.dp))
                        ChipFlow(
                            items = DayLabels,
                            selectedIndex = selectedDay,
                            onSelect = { selectedDay = it }
                        )
                        Spacer(Modifier.height(8.dp))
                        val current = TimeWindowCodec.decode(dayWindows.getOrNull(selectedDay))
                        WindowListEditor(
                            windows = current,
                            emptyHint = "该天沿用「默认」时间，点下方按钮可单独设置",
                            onChange = { updated ->
                                val list = dayWindows.toMutableList()
                                while (list.size < 7) list.add("")
                                list[selectedDay] = TimeWindowCodec.encode(updated)
                                dayWindows = list
                            }
                        )
                        if (current.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            TextButton(onClick = {
                                val list = dayWindows.toMutableList()
                                while (list.size < 7) list.add("")
                                list[selectedDay] = ""
                                dayWindows = list
                            }) { Text("清除${DayLabels[selectedDay]}的单独设置") }
                        }
                    }

                    else -> {
                        Text(
                            text = "节假日开放时间（留空表示沿用默认）",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.mutedText
                        )
                        Spacer(Modifier.height(6.dp))
                        WindowListEditor(
                            windows = holidayWindows,
                            emptyHint = "放假时沿用「默认」时间",
                            onChange = { holidayWindows = it }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可选）") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (existing != null && onDelete != null) {
                    IconButton(onClick = { onDelete(existing) }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(
                    onClick = {
                        val invalid = (defaultWindows + holidayWindows +
                            dayWindows.flatMap { TimeWindowCodec.decode(it) })
                            .firstOrNull { !it.isValid() }
                        when {
                            name.isBlank() -> error = "请填写名称"
                            invalid != null -> error = "时间格式不正确：${invalid.start}-${invalid.end}（应为 HH:mm 且结束晚于开始）"
                            else -> onSave(
                                CampusFacility(
                                    id = existing?.id ?: 0L,
                                    name = name.trim(),
                                    type = type,
                                    location = location.trim().ifBlank { null },
                                    windows = TimeWindowCodec.encode(defaultWindows),
                                    dayWindows = TimeWindowCodec.encodeDays(dayWindows),
                                    holidayWindows = TimeWindowCodec.encode(holidayWindows),
                                    note = note.trim().ifBlank { null },
                                    enabled = existing?.enabled ?: true,
                                    sortOrder = existing?.sortOrder ?: 99
                                )
                            )
                        }
                    },
                    modifier = Modifier.weight(1.4f)
                ) { Text("保存") }
            }
        }
    }
}

/** 多段开放时间的编辑器。 */
@Composable
private fun WindowListEditor(
    windows: List<FacilityWindow>,
    onChange: (List<FacilityWindow>) -> Unit,
    emptyHint: String = "还没有设置时间，点下方按钮添加一段"
) {
    val colors = LocalTimetableColors.current

    if (windows.isEmpty()) {
        Text(
            text = emptyHint,
            style = MaterialTheme.typography.labelSmall,
            color = colors.mutedText,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }

    windows.forEachIndexed { index, window ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
        ) {
            OutlinedTextField(
                value = window.start,
                onValueChange = { v ->
                    val list = windows.toMutableList()
                    list[index] = window.copy(start = v.trim())
                    onChange(list)
                },
                label = { Text("开始") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Text("  ~  ", style = MaterialTheme.typography.bodyMedium, color = colors.mutedText)
            OutlinedTextField(
                value = window.end,
                onValueChange = { v ->
                    val list = windows.toMutableList()
                    list[index] = window.copy(end = v.trim())
                    onChange(list)
                },
                label = { Text("结束") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { onChange(windows.filterIndexed { i, _ -> i != index }) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "删除该时段",
                    modifier = Modifier.size(16.dp),
                    tint = colors.mutedText
                )
            }
        }
    }

    TextButton(onClick = {
        val last = windows.lastOrNull()
        val next = if (last == null) FacilityWindow("08:00", "22:00")
        else FacilityWindow(last.end.ifBlank { "08:00" }, "23:00")
        onChange(windows + next)
    }) {
        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text("添加一段时间")
    }
}

@Composable
private fun SelectablePill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 自动换行的可选标签组。 */
@Composable
private fun ChipFlow(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    // 数量不多，按每行 4 个排布即可
    Column {
        items.chunked(4).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEachIndexed { colIndex, label ->
                    val index = rowIndex * 4 + colIndex
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (index == selectedIndex) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { onSelect(index) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            color = if (index == selectedIndex) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}
