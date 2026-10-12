package com.university.timetable.ui.ai

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.university.timetable.data.entity.ChatMessage
import com.university.timetable.data.entity.ChatRole
import com.university.timetable.data.entity.TodoItem
import com.university.timetable.importer.OcrScheduleParser
import com.university.timetable.ui.theme.LocalTimetableColors
import com.university.timetable.util.ImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 支持的文本类附件扩展名。 */
private val TEXT_EXTENSIONS = setOf("txt", "md", "csv", "json", "html", "htm", "xml", "log")

/**
 * AI 助手聊天界面。
 *
 * - 支持文字、图片、文件（文本类解析内容，其他类型提示不支持）
 * - 流式输出、可取消、错误提示、会话管理
 * - 动作需用户确认后才执行
 */
@Composable
fun AiChatScreen(
    viewModel: AiViewModel,
    sessions: List<com.university.timetable.data.entity.ChatSession>,
    currentSessionId: Long?,
    messages: List<ChatMessage>,
    streamingText: String,
    generating: Boolean,
    error: String?,
    pending: PendingAction?,
    attachments: List<AiAttachment>,
    allowTools: Boolean,
    lastResult: String?,
    undoLabel: String?,
    onUndo: () -> Unit,
    onDismissResult: () -> Unit,
    onBack: () -> Unit,
    onOpenConfig: () -> Unit,
    onStartLaundry: (Int, String) -> Unit
) {
    val colors = LocalTimetableColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var input by remember { mutableStateOf("") }
    var sessionMenuOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    // 待删除的历史对话（非空时弹出确认框）
    var deleteTarget by remember { mutableStateOf<com.university.timetable.data.entity.ChatSession?>(null) }
    var voiceHint by remember { mutableStateOf(false) }

    // 图片选择
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.addAttachment(AiAttachment(uri.toString(), "图片", isImage = true))
        }
    }

    // 文件选择：读文本类文件内容
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "文件"
            val ext = name.substringAfterLast('.', "").lowercase()
            val content = withContext(Dispatchers.IO) {
                if (ext in TEXT_EXTENSIONS) {
                    ImageLoader.readText(context, uri)?.take(8000)
                } else {
                    null
                }
            }
            viewModel.addAttachment(
                AiAttachment(
                    uri = uri.toString(),
                    name = name,
                    isImage = false,
                    textContent = content
                        ?: "（暂不支持直接解析 .$ext 文件，请转成 PDF 截图后用图片上传，或复制文本粘贴）"
                )
            )
        }
    }

    // 语音输入（系统语音识别，返回文字）
    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val text = result.data
            ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (!text.isNullOrBlank()) {
            input = if (input.isBlank()) text else "$input $text"
        }
    }

    // 新消息自动滚到底部
    LaunchedEffect(messages.size, streamingText) {
        val total = messages.size + if (streamingText.isNotEmpty()) 1 else 0
        if (total > 0) listState.animateScrollToItem((total - 1).coerceAtLeast(0))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // 键盘弹出时整列向上收缩，输入栏与底部按钮始终完整可见；
            // 收起键盘后 imePadding 归零，布局自动回到原位，不会跳动。
            .imePadding()
    ) {        // ---------------- 顶栏 ----------------
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBackIos,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "AI 助手",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (allowTools) "可在确认后修改课表与待办" else "只读模式（不可修改数据）",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.mutedText
                )
            }
            IconButton(onClick = { viewModel.newSession() }, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "新对话",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Box {
                IconButton(onClick = { sessionMenuOpen = true }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "更多",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                DropdownMenu(expanded = sessionMenuOpen, onDismissRequest = { sessionMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("模型配置") },
                        leadingIcon = { Icon(Icons.Filled.Tune, contentDescription = null) },
                        onClick = {
                            sessionMenuOpen = false
                            onOpenConfig()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("清空当前对话") },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = {
                            sessionMenuOpen = false
                            confirmClear = true
                        }
                    )
                    if (sessions.size > 1) {
                        DropdownMenuItem(
                            text = { Text("历史对话（${sessions.size}）", fontWeight = FontWeight.SemiBold) },
                            onClick = { },
                            enabled = false
                        )
                        sessions.take(6).forEach { s ->
                            // 历史对话条目：点击切换，右侧独立的删除按钮用于删除该对话。
                            // 删除按钮放在条目内部，避免与「清空当前对话」混淆。
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.width(210.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = (if (s.id == currentSessionId) "● " else "   ") + s.title,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = {
                                                // 先关掉菜单再弹确认框，避免菜单盖住确认框
                                                sessionMenuOpen = false
                                                deleteTarget = s
                                            },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.Delete,
                                                contentDescription = "删除【${s.title}】",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    sessionMenuOpen = false
                                    viewModel.switchSession(s.id)
                                }
                            )
                        }
                    }
                }
            }
        }

        // ---------------- 数据外发提示 ----------------
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
            Text(
                text = "对话内容（含你上传的图片/文件）会发送给你所配置的第三方模型服务，请勿发送敏感信息。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }

        // ---------------- 消息列表 ----------------
        Box(modifier = Modifier.weight(1f)) {
            if (messages.isEmpty() && streamingText.isEmpty() && !generating) {
                EmptyChatHint(
                    onOpenConfig = onOpenConfig,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        MessageBubble(message)
                        Spacer(Modifier.height(10.dp))
                    }
                    if (generating && streamingText.isNotEmpty()) {
                        item {
                            StreamingBubble(streamingText)
                            Spacer(Modifier.height(10.dp))
                        }
                    } else if (generating) {
                        item { ThinkingBubble() }
                    }
                }
            }
        }

        // ---------------- 错误提示 ----------------
        error?.let { msg ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(10.dp)
            ) {
                Text(
                    text = msg,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { viewModel.dismissError() }) { Text("知道了") }
            }
        }

        // ---------------- 执行结果 + 撤销 ----------------
        lastResult?.let { msg ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(10.dp)
            ) {
                Text(
                    text = msg,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f)
                )
                if (undoLabel != null) {
                    TextButton(onClick = onUndo) {
                        Text(
                            text = "撤销",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                } else {
                    TextButton(onClick = onDismissResult) { Text("知道了") }
                }
            }
        }

        // ---------------- 附件预览 ----------------
        if (attachments.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                attachments.forEach { att ->
                    AttachmentChip(attachment = att, onRemove = { viewModel.removeAttachment(att.uri) })
                }
            }
        }

        // ---------------- 输入区 ----------------
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            IconButton(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Filled.Image,
                    contentDescription = "上传图片",
                    tint = colors.mutedText
                )
            }
            IconButton(onClick = { filePicker.launch("*/*") }, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Filled.AttachFile,
                    contentDescription = "上传文件",
                    tint = colors.mutedText
                )
            }
            IconButton(
                onClick = {
                    val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(
                            android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                        )
                        putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                        putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "说出你想做的事，例如：把周三的课加到周四")
                    }
                    runCatching { voiceLauncher.launch(intent) }
                        .onFailure { voiceHint = true }
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Filled.Mic, contentDescription = "语音输入", tint = colors.mutedText)
            }

            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = {
                    Text(
                        text = "问课表、加待办或发截图",
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                },
                maxLines = 4,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp)
            )

            Spacer(Modifier.width(6.dp))

            if (generating) {
                IconButton(
                    onClick = { viewModel.cancelGenerate() },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer)
                ) {
                    Icon(
                        Icons.Filled.Stop,
                        contentDescription = "取消生成",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            } else {
                IconButton(
                    onClick = {
                        viewModel.send(input)
                        input = ""
                    },
                    enabled = input.isNotBlank() || attachments.isNotEmpty(),
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (input.isNotBlank() || attachments.isNotEmpty())
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "发送",
                        tint = if (input.isNotBlank() || attachments.isNotEmpty())
                            MaterialTheme.colorScheme.onPrimary
                        else colors.mutedText
                    )
                }
            }
        }
    }

    // ---------------- 弹窗 ----------------
    pending?.let { p ->
        AiActionConfirmDialog(
            pending = p,
            onConfirm = { viewModel.confirmPendingAction(onStartLaundry) },
            onReject = { viewModel.rejectPendingAction() }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空当前对话？") },
            text = { Text("将删除本会话的所有消息记录。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearCurrentMessages()
                    confirmClear = false
                }) { Text("清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } }
        )
    }

    // 删除单个历史对话
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除这个对话？") },
            text = { Text("「${target.title}」及其全部消息记录将被删除，此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSession(target.id)
                    deleteTarget = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }

    if (voiceHint) {
        AlertDialog(
            onDismissRequest = { voiceHint = false },
            title = { Text("无法启动语音识别") },
            text = { Text("设备上没有可用的语音识别服务。可以改用文字输入，或上传课表截图。") },
            confirmButton = { TextButton(onClick = { voiceHint = false }) { Text("知道了") } }
        )
    }
}

// ---------------------------------------------------------------------------
// 子组件
// ---------------------------------------------------------------------------

@Composable
private fun EmptyChatHint(onOpenConfig: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalTimetableColors.current
    Column(
        modifier = modifier.padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.SmartToy,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "有什么可以帮你？",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        listOf(
            "今天有什么课？",
            "帮我把「交实验报告」加到今天的待办",
            "浴室今天几点开放？",
            "洗衣机洗 40 分钟，帮我计时",
            "发一张课表截图，帮我识别导入"
        ).forEach { example ->
            Text(
                text = "· $example",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.mutedText
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "首次使用请先配置模型",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onOpenConfig)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val colors = LocalTimetableColors.current
    val isUser = message.role == ChatRole.USER

    if (message.role == ChatRole.TOOL) {
        // 工具执行结果：居中细条
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.labelSmall,
                color = colors.mutedText,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceCardElevated)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .wrapContentWidthCompat()
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .background(
                    if (isUser) MaterialTheme.colorScheme.primary
                    else colors.surfaceCard
                )
                .padding(12.dp)
        ) {
            if (message.content.isNotBlank()) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface
                )
            }

            // 附件缩略
            if (message.imageList.isNotEmpty() || message.fileList.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = buildString {
                        if (message.imageList.isNotEmpty()) append("🖼 ${message.imageList.size} 张图片")
                        if (message.fileList.isNotEmpty()) {
                            if (isNotEmpty()) append("  ")
                            append("📎 ${message.fileList.size} 个文件")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    else colors.mutedText
                )
            }
        }
    }
}

@Composable
private fun StreamingBubble(text: String) {
    val colors = LocalTimetableColors.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .wrapContentWidthCompat()
                .clip(RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
                .background(colors.surfaceCard)
                .padding(12.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ThinkingBubble() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = "正在生成…",
            style = MaterialTheme.typography.labelMedium,
            color = LocalTimetableColors.current.mutedText
        )
    }
}

@Composable
private fun AttachmentChip(attachment: AiAttachment, onRemove: () -> Unit) {
    val colors = LocalTimetableColors.current
    val context = LocalContext.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceCardElevated)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        if (attachment.isImage) {
            val bitmap: Bitmap? = remember(attachment.uri) {
                ImageLoader.load(context, attachment.uri)
            }
            if (bitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
                Spacer(Modifier.width(6.dp))
            }
        }
        Text(
            text = if (attachment.isImage) "图片" else attachment.name.take(14),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.Filled.Close,
            contentDescription = "移除",
            tint = colors.mutedText,
            modifier = Modifier.size(14.dp).clickable(onClick = onRemove)
        )
    }
}

/** 让气泡宽度随内容收缩，同时不超过父级限制。 */
private fun Modifier.wrapContentWidthCompat(): Modifier =
    this.wrapContentWidth(Alignment.Start)

@Suppress("unused")
private fun unusedTodoMax() = TodoItem.MAX_IMAGES

@Suppress("unused")
private fun unusedOcr() = OcrScheduleParser
