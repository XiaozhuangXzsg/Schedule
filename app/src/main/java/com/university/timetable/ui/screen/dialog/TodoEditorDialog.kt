package com.university.timetable.ui.screen.dialog

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.university.timetable.data.entity.TodoItem
import com.university.timetable.ui.theme.LocalTimetableColors
import com.university.timetable.util.ImageLoader
import com.university.timetable.util.SemesterWeeks
import java.time.LocalDate

/**
 * 待办事项编辑窗口。
 *
 * - 顶部日期条：可切换「今天 / 明天 …」以及前后各一天，支持添加今日及后面几天的事项
 * - 每个事项可附带 1-3 张图片
 * - 保存后即写入数据库，桌面卡片左侧会显示当天的待办
 */
@Composable
fun TodoEditorDialog(
    today: LocalDate,
    todos: List<TodoItem>,
    onDismiss: () -> Unit,
    onSave: (existing: TodoItem?, date: LocalDate, content: String, images: List<String>) -> Unit,
    onDelete: (Long) -> Unit,
    onToggleDone: (TodoItem) -> Unit
) {
    val colors = LocalTimetableColors.current
    var selectedDate by remember { mutableStateOf(today) }

    // 编辑中的草稿
    var editing by remember { mutableStateOf<TodoItem?>(null) }
    var draft by remember { mutableStateOf("") }
    val draftImages = remember { mutableStateListOf<String>() }
    var composing by remember { mutableStateOf(false) }

    fun startNew() {
        editing = null
        draft = ""
        draftImages.clear()
        composing = true
    }

    fun startEdit(item: TodoItem) {
        editing = item
        draft = item.content
        draftImages.clear()
        draftImages.addAll(item.images)
        composing = true
    }

    fun resetDraft() {
        composing = false
        editing = null
        draft = ""
        draftImages.clear()
    }

    val dayTodos = todos.filter { it.date == selectedDate.toString() }
        .sortedWith(compareBy({ it.done }, { it.sortOrder }, { it.createdAt }))

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 660.dp)
                .clip(RoundedCornerShape(24.dp))
                .glassLayer(MaterialTheme.colorScheme.surface)
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "待办事项",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭")
                }
            }

            Spacer(Modifier.height(10.dp))

            // ---- 日期选择 ----
            DateStrip(
                today = today,
                selected = selectedDate,
                onSelect = {
                    selectedDate = it
                    resetDraft()
                },
                onStep = { delta ->
                    selectedDate = selectedDate.plusDays(delta.toLong())
                    resetDraft()
                }
            )

            Spacer(Modifier.height(12.dp))

            // ---- 待办列表 ----
            Column(modifier = Modifier.weight(1f, fill = false).heightIn(min = 120.dp)) {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(dayTodos, key = { it.id }) { item ->
                        TodoRow(
                            item = item,
                            onToggle = { onToggleDone(item) },
                            onEdit = { startEdit(item) },
                            onDelete = { onDelete(item.id) }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            if (dayTodos.isEmpty() && !composing) {
                Text(
                    text = "${dateLabel(selectedDate, today)}还没有待办事项，点下方按钮添加。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.mutedText,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }

            // ---- 编辑区 ----
            if (composing) {
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surfaceCardElevated)
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = if (editing == null) {
                                "新增到 ${dateLabel(selectedDate, today)}"
                            } else {
                                "编辑事项"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.mutedText
                        )
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            label = { Text("事项内容") },
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(8.dp))
                        TodoImagePicker(
                            images = draftImages,
                            onAdd = { uri -> if (draftImages.size < TodoItem.MAX_IMAGES) draftImages.add(uri) },
                            onRemove = { uri -> draftImages.remove(uri) }
                        )

                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TextButton(onClick = { resetDraft() }, modifier = Modifier.weight(1f)) {
                                Text("取消")
                            }
                            Button(
                                onClick = {
                                    if (draft.isNotBlank()) {
                                        onSave(editing, selectedDate, draft, draftImages.toList())
                                        resetDraft()
                                    }
                                },
                                enabled = draft.isNotBlank(),
                                modifier = Modifier.weight(1.4f)
                            ) { Text("保存") }
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(10.dp))
                Button(onClick = { startNew() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("添加${dateLabel(selectedDate, today)}的事项")
                }
            }
        }
    }
}

/** 日期条：今天前后各一天 + 未来一周。 */
@Composable
private fun DateStrip(
    today: LocalDate,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onStep: (Int) -> Unit
) {
    val colors = LocalTimetableColors.current
    val dates = remember(today) { (-1..7).map { today.plusDays(it.toLong()) } }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onStep(-1) }, modifier = Modifier.size(30.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBackIos,
                    contentDescription = "前一天",
                    modifier = Modifier.size(14.dp),
                    tint = colors.mutedText
                )
            }
            Text(
                text = "%d 月 %d 日 %s".format(
                    selected.monthValue,
                    selected.dayOfMonth,
                    dateLabel(selected, today)
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { onStep(1) }, modifier = Modifier.size(30.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = "后一天",
                    modifier = Modifier.size(14.dp),
                    tint = colors.mutedText
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(dates) { date ->
                val isSelected = date == selected
                val isToday = date == today
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { onSelect(date) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = SemesterWeeks.dayChar(date.dayOfWeek.value),
                        fontSize = 11.sp,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else colors.mutedText
                    )
                    Text(
                        text = date.dayOfMonth.toString(),
                        fontSize = 13.sp,
                        fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** 单条待办。 */
@Composable
private fun TodoRow(
    item: TodoItem,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalTimetableColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceCardElevated)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(
                    if (item.done) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center
        ) {
            if (item.done) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "已完成",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.content,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (item.done) colors.mutedText else MaterialTheme.colorScheme.onSurface
            )
            if (item.images.isNotEmpty()) {
                Text(
                    text = "含 ${item.images.size} 张图片",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.mutedText
                )
            }
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = "编辑",
                modifier = Modifier.size(16.dp),
                tint = colors.mutedText
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "删除",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

/** 图片选择：最多 3 张，可预览与删除。 */
@Composable
private fun TodoImagePicker(
    images: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    val context = LocalContext.current
    val colors = LocalTimetableColors.current
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            // 持久化读权限，保证重启后卡片仍能读取
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            onAdd(uri.toString())
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        images.forEach { uri ->
            val bitmap = remember(uri) { ImageLoader.load(context, uri) }
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (bitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f))
                        .clickable { onRemove(uri) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "移除图片",
                        tint = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }

        if (images.size < TodoItem.MAX_IMAGES) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { picker.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "添加图片",
                        tint = colors.mutedText,
                        modifier = Modifier.size(16.dp)
                    )
                    Text("图片", fontSize = 9.sp, color = colors.mutedText)
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Text(
            text = "${images.size}/${TodoItem.MAX_IMAGES}",
            style = MaterialTheme.typography.labelSmall,
            color = colors.mutedText
        )
    }
}

/** 相对日期文案。 */
private fun dateLabel(date: LocalDate, today: LocalDate): String = when {
    date == today -> "今天"
    date == today.plusDays(1) -> "明天"
    date == today.minusDays(1) -> "昨天"
    else -> SemesterWeeks.dayLabel(date.dayOfWeek.value)
}
