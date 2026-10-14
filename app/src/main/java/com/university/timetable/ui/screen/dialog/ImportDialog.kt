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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.university.timetable.importer.ImportResult
import com.university.timetable.importer.OcrScheduleParser
import com.university.timetable.importer.ScheduleCsvParser
import com.university.timetable.importer.ScheduleHtmlParser
import com.university.timetable.importer.ScheduleTextParser
import com.university.timetable.ui.MainViewModel
import com.university.timetable.ui.screen.LzuImportActivity
import com.university.timetable.ui.tutorial.TutorialAnchors
import com.university.timetable.ui.tutorial.tutorialAnchor
import com.university.timetable.ui.theme.LocalTimetableColors
import com.university.timetable.util.ImageLoader
import com.university.timetable.util.SemesterWeeks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 智能导入弹窗：
 *  - HTML 文件
 *  - CSV 文件
 *  - 课表截图 OCR（ML Kit）
 *  - 直接粘贴文本
 */
@Composable
fun ImportDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onDone: (Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LocalTimetableColors.current

    var busy by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<ImportResult?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var manualText by remember { mutableStateOf("") }
    var showManual by remember { mutableStateOf(false) }

    val htmlPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            errorText = null
            statusText = "正在读取文件…"
            val parsed = withContext(Dispatchers.IO) {
                val content = ImageLoader.readText(context, uri)
                if (content.isNullOrBlank()) {
                    null
                } else {
                    ScheduleHtmlParser.parse(content, uri.lastPathSegment ?: "HTML")
                }
            }
            busy = false
            if (parsed == null) {
                errorText = "无法读取文件内容，请确认文件编码为 UTF-8"
            } else {
                result = parsed
                statusText = ""
            }
        }
    }

    val csvPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            errorText = null
            statusText = "正在读取文件…"
            val parsed = withContext(Dispatchers.IO) {
                val content = ImageLoader.readText(context, uri)
                if (content.isNullOrBlank()) {
                    null
                } else {
                    ScheduleCsvParser.parse(content, uri.lastPathSegment ?: "CSV")
                }
            }
            busy = false
            if (parsed == null) {
                errorText = "无法读取文件内容"
            } else {
                result = parsed
                statusText = ""
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            errorText = null
            statusText = "正在识别截图…"
            try {
                val parsed = withContext(Dispatchers.Default) {
                    val bitmap = ImageLoader.loadForOcr(context, uri)
                    if (bitmap == null) {
                        null
                    } else {
                        OcrScheduleParser.parse(bitmap, "课表截图") { statusText = it }
                    }
                }
                if (parsed == null) {
                    errorText = "无法读取图片"
                } else {
                    result = parsed
                    statusText = ""
                }
            } catch (t: Throwable) {
                errorText = "OCR 识别失败：${t.message ?: "未知错误"}"
            } finally {
                busy = false
            }
        }
    }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "智能导入课表",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "支持教务系统 HTML、CSV，以及课表截图 OCR 识别",
                style = MaterialTheme.typography.labelMedium,
                color = colors.mutedText
            )

            Spacer(Modifier.height(16.dp))

            // 应用内登录教务系统直接导入（兰州大学）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    // 只作为新手引导「出现即推进」的信号：这是独立 Dialog 窗口，
                    // 它的 boundsInRoot() 与主窗口引导层坐标系不一致，不能用来挖洞。
                    .tutorialAnchor(TutorialAnchors.IMPORT_DIALOG_ENTRY)
                    .clickable(enabled = !busy) {
                        context.startActivity(LzuImportActivity.intent(context))
                    }
                    .padding(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.AccountCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "登录教务系统导入",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "应用内打开兰州大学教务手机版，登录后一键识别课表",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(15.dp)
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "或使用以下方式导入已保存的文件",
                style = MaterialTheme.typography.labelMedium,
                color = colors.mutedText
            )
            Spacer(Modifier.height(8.dp))

            ImportOption(
                icon = Icons.Filled.Code,
                title = "HTML 课表文件",
                subtitle = "从教务系统「导出」保存的网页文件",
                enabled = !busy,
                onClick = { htmlPicker.launch(arrayOf("text/html", "text/plain", "*/*")) }
            )
            Spacer(Modifier.height(10.dp))
            ImportOption(
                icon = Icons.Filled.Description,
                title = "CSV 课表文件",
                subtitle = "表头：课程名称,授课教师,星期,节次,周次,上课地点",
                enabled = !busy,
                onClick = { csvPicker.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*")) }
            )
            Spacer(Modifier.height(10.dp))
            ImportOption(
                icon = Icons.Filled.Image,
                title = "课表截图 OCR 识别",
                subtitle = "使用 ML Kit 中文离线识别，支持相册选图",
                enabled = !busy,
                onClick = { imagePicker.launch("image/*") }
            )
            Spacer(Modifier.height(10.dp))
            ImportOption(
                icon = Icons.AutoMirrored.Filled.TextSnippet,
                title = "粘贴课表文本",
                subtitle = "直接粘贴从网页复制的课表内容",
                enabled = !busy,
                onClick = { showManual = !showManual }
            )

            if (showManual) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = manualText,
                    onValueChange = { manualText = it },
                    label = { Text("粘贴课表文本") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        scope.launch {
                            busy = true
                            val parsed = withContext(Dispatchers.Default) {
                                ScheduleTextParser.parse(manualText, "粘贴文本")
                            }
                            busy = false
                            result = parsed
                        }
                    },
                    enabled = manualText.isNotBlank() && !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("解析文本") }
            }

            if (busy) {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(statusText.ifBlank { "处理中…" }, style = MaterialTheme.typography.bodyMedium)
                }
            }

            errorText?.let { msg ->
                Spacer(Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                        .padding(12.dp)
                ) {
                    Text(msg, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }

            result?.let { r ->
                Spacer(Modifier.height(16.dp))
                ResultPreview(result = r)
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text("取消")
                }
                Button(
                    onClick = {
                        val r = result ?: return@Button
                        val entries = r.courses.map { it.course to it.sessions }
                        viewModel.replaceSchedule(entries)
                        onDone(r.courseCount)
                    },
                    enabled = (result?.isEmpty == false) && !busy,
                    modifier = Modifier.weight(1.4f)
                ) {
                    Text("替换并导入")
                }
            }
        }
    }
}

@Composable
private fun ImportOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalTimetableColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceCardElevated)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = colors.mutedText)
        }
    }
}

@Composable
private fun ResultPreview(result: ImportResult) {
    val colors = LocalTimetableColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceCardElevated)
            .padding(14.dp)
    ) {
        Text(
            text = "解析结果",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "${result.courseCount} 门课程 · ${result.sessionCount} 个上课时段",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )

        if (result.warnings.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            result.warnings.take(3).forEach { w ->
                Text("· $w", style = MaterialTheme.typography.labelSmall, color = colors.mutedText)
            }
        }

        Spacer(Modifier.height(10.dp))
        result.courses.take(6).forEach { imported ->
            val session = imported.sessions.firstOrNull()
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text(
                    text = session?.let { SemesterWeeks.dayLabel(it.dayOfWeek) } ?: "--",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.mutedText,
                    modifier = Modifier.width(40.dp)
                )
                Text(
                    text = imported.course.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = session?.location ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.mutedText
                )
            }
        }
        if (result.courseCount > 6) {
            Text(
                text = "… 另有 ${result.courseCount - 6} 门课程",
                style = MaterialTheme.typography.labelSmall,
                color = colors.mutedText
            )
        }
    }
}
