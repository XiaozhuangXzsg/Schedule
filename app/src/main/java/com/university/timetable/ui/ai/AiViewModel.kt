package com.university.timetable.ui.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.university.timetable.ServiceLocator
import com.university.timetable.data.ai.AiAction
import com.university.timetable.data.ai.AiActionExecutor
import com.university.timetable.data.ai.AiActionParser
import com.university.timetable.data.ai.AiChatRepository
import com.university.timetable.data.ai.AiClient
import com.university.timetable.data.ai.AiConfig
import com.university.timetable.data.ai.AiConfigRepository
import com.university.timetable.data.ai.AiContextBuilder
import com.university.timetable.data.ai.AiExecContext
import com.university.timetable.data.ai.AiExecutionResult
import com.university.timetable.data.ai.ApiMessage
import com.university.timetable.data.entity.ChatMessage
import com.university.timetable.data.entity.ChatRole
import com.university.timetable.data.entity.ChatSession
import com.university.timetable.data.prefs.PreferencesRepository
import com.university.timetable.data.repository.ScheduleRepository
import com.university.timetable.util.ImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.LocalDate

/** 待用户确认的动作。 */
data class PendingAction(
    val messageId: Long,
    val action: AiAction,
    val details: List<String>
)

/** 待发送的附件。 */
data class AiAttachment(
    val uri: String,
    val name: String,
    val isImage: Boolean,
    /** 文本类附件解析出的内容（已截断） */
    val textContent: String = ""
)

/**
 * AI 助手 ViewModel。
 *
 * 状态被拆成若干独立 StateFlow（会话 / 消息 / 配置 / 流式文本 / 错误 …），
 * 由 Compose 侧分别 collect，避免把复杂 combine 塞进 ViewModel。
 */
class AiViewModel(
    private val appContext: Context,
    private val chat: AiChatRepository,
    private val configRepo: AiConfigRepository,
    private val schedule: ScheduleRepository,
    private val prefsRepo: PreferencesRepository
) : ViewModel() {

    private val executor = AiActionExecutor(schedule)

    // ---------------- 对外状态 ----------------

    val sessions: StateFlow<List<ChatSession>> = chat.sessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val configs: StateFlow<List<AiConfig>> = configRepo.configs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allowTools: StateFlow<Boolean> = configRepo.allowTools
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _currentSessionId = MutableStateFlow<Long?>(null)
    val currentSessionId: StateFlow<Long?> = _currentSessionId.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<ChatMessage>> = _currentSessionId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else chat.messages(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText.asStateFlow()

    private val _generating = MutableStateFlow(false)
    val generating: StateFlow<Boolean> = _generating.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _pending = MutableStateFlow<PendingAction?>(null)
    val pendingAction: StateFlow<PendingAction?> = _pending.asStateFlow()

    private val _lastResult = MutableStateFlow<String?>(null)
    val lastActionResult: StateFlow<String?> = _lastResult.asStateFlow()

    /** 可撤销的上一步操作（没有则为 null）。 */
    private val _undoLabel = MutableStateFlow<String?>(null)
    val undoLabel: StateFlow<String?> = _undoLabel.asStateFlow()
    private var undoToken: com.university.timetable.data.ai.UndoToken? = null

    private val _attachments = MutableStateFlow<List<AiAttachment>>(emptyList())
    val attachments: StateFlow<List<AiAttachment>> = _attachments.asStateFlow()

    private var generateJob: Job? = null

    /** 每轮生成时刷新的课表上下文（学期起始 / 总周数 / 今天）。 */
    private var execContext: AiExecContext? = null

    private suspend fun currentContext(): AiExecContext {
        val prefs = prefsRepo.preferences.first()
        return AiExecContext(
            today = LocalDate.now(),
            semesterStart = runCatching { LocalDate.parse(prefs.semesterStart) }.getOrElse { LocalDate.now() },
            totalWeeks = prefs.totalWeeks
        )
    }

    init {
        viewModelScope.launch { _currentSessionId.value = chat.ensureSession() }
    }

    // ---------------- 会话 ----------------

    fun newSession() {
        viewModelScope.launch {
            _currentSessionId.value = chat.createSession()
            resetTransient()
        }
    }

    fun switchSession(id: Long) {
        _currentSessionId.value = id
        resetTransient()
    }

    fun deleteSession(id: Long) {
        viewModelScope.launch {
            chat.deleteSession(id)
            if (_currentSessionId.value == id) {
                _currentSessionId.value = chat.ensureSession()
                resetTransient()
            }
        }
    }

    fun clearCurrentMessages() {
        val id = _currentSessionId.value ?: return
        viewModelScope.launch { chat.clearMessages(id) }
    }

    fun renameSession(id: Long, title: String) {
        if (title.isBlank()) return
        viewModelScope.launch { chat.renameSession(id, title) }
    }

    private fun resetTransient() {
        _streamingText.value = ""
        _error.value = null
        _pending.value = null
        _lastResult.value = null
        _attachments.value = emptyList()
    }

    fun dismissError() { _error.value = null }

    fun dismissActionResult() { _lastResult.value = null }

    // ---------------- 模型配置 ----------------

    fun saveConfig(config: AiConfig) = viewModelScope.launch { configRepo.upsert(config) }

    fun deleteConfig(id: String) = viewModelScope.launch { configRepo.delete(id) }

    fun setActiveConfig(id: String) = viewModelScope.launch { configRepo.setActive(id) }

    fun setAllowTools(value: Boolean) = viewModelScope.launch { configRepo.setAllowTools(value) }

    fun testConfig(config: AiConfig, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val reply = AiClient.testConnection(config)
                onResult(true, "连接成功，模型回复：$reply")
            } catch (t: Throwable) {
                onResult(false, t.message ?: "连接失败")
            }
        }
    }

    // ---------------- 附件 ----------------

    fun addAttachment(attachment: AiAttachment) {
        _attachments.value = _attachments.value + attachment
    }

    fun removeAttachment(uri: String) {
        _attachments.value = _attachments.value.filterNot { it.uri == uri }
    }

    fun clearAttachments() { _attachments.value = emptyList() }

    // ---------------- 发送 ----------------

    fun send(text: String) {
        val content = text.trim()
        val attachments = _attachments.value
        if (content.isEmpty() && attachments.isEmpty()) return
        if (_generating.value) return

        val sessionId = _currentSessionId.value ?: return

        generateJob = viewModelScope.launch {
            val config = configRepo.current()
            if (config == null || !config.isUsable || config.apiKey.isBlank()) {
                _error.value = "请先在「AI 助手 → 模型配置」中添加可用的模型与 API Key"
                return@launch
            }

            // 1) 用户消息落盘
            chat.addMessage(
                ChatMessage(
                    sessionId = sessionId,
                    role = ChatRole.USER,
                    content = content,
                    images = attachments.filter { it.isImage }.joinToString("|") { it.uri },
                    files = attachments.filterNot { it.isImage }.joinToString("|") { it.uri },
                    fileNames = attachments.filterNot { it.isImage }.joinToString("|") { it.name }
                )
            )
            chat.autoTitle(sessionId, content.ifBlank { attachments.firstOrNull()?.name ?: "新对话" })
            clearAttachments()

            _generating.value = true
            _streamingText.value = ""
            _error.value = null
            _pending.value = null

            try {
                val ctx = currentContext()
                execContext = ctx
                val toolsOn = configRepo.allowTools.first()
                val system = chat.buildSystemPrompt(
                    today = ctx.today,
                    semesterStart = ctx.semesterStart,
                    totalWeeks = ctx.totalWeeks,
                    allowTools = toolsOn
                )

                val history = chat.getMessages(sessionId)
                val apiMessages = mutableListOf(ApiMessage("system", system))
                apiMessages += chat.toApiMessages(history, emptyMap())

                // 把附件内容并入最后一条用户消息
                val attachmentText = attachments
                    .filter { !it.isImage && it.textContent.isNotBlank() }
                    .joinToString("\n\n") { "【附件 ${it.name}】\n${it.textContent}" }
                val imageCount = attachments.count { it.isImage }
                val images = if (config.supportsImages) {
                    attachments.filter { it.isImage }.mapNotNull { toDataUrl(it.uri) }
                } else emptyList()

                val note = buildString {
                    if (imageCount > 0 && !config.supportsImages) {
                        append("\n（当前模型不支持图片输入，已忽略 $imageCount 张图片）")
                    }
                    if (attachmentText.isNotBlank()) append("\n\n$attachmentText")
                }

                if (note.isNotBlank() || images.isNotEmpty()) {
                    val last = apiMessages.removeAt(apiMessages.lastIndex)
                    apiMessages += last.copy(
                        text = last.text + note,
                        imageDataUrls = images
                    )
                }

                var rounds = 0
                while (true) {
                    _streamingText.value = ""
                    val reply = AiClient.streamChat(config, apiMessages) { delta ->
                        _streamingText.value += delta
                    }

                    val (displayText, action) = AiActionParser.parse(reply)
                    val text = displayText.ifBlank { reply }

                    // 只读查询（query_schedule）不需要用户确认：自动执行后把真实数据回灌给模型，
                    // 让模型能基于「任意一天」的真实课表继续作答。
                    val autoQuery = toolsOn && action is AiAction.QuerySchedule
                    if (autoQuery && rounds < MAX_QUERY_ROUNDS) {
                        val outcome = executor.execute(action, ctx)
                        apiMessages += ApiMessage("assistant", text)
                        apiMessages += ApiMessage(
                            "user",
                            "【系统】已执行课表查询，真实结果如下（这是数据库里的权威数据）：\n" +
                                outcome.message +
                                "\n请直接基于以上数据回答，不要再表示要去查询。"
                        )
                        rounds++
                        continue
                    }

                    if (autoQuery) {
                        // 轮次用尽，兜底把查询结果直接作为回答
                        val outcome = executor.execute(action, ctx)
                        chat.addMessage(
                            ChatMessage(
                                sessionId = sessionId,
                                role = ChatRole.ASSISTANT,
                                content = "$text\n\n${outcome.message}"
                            )
                        )
                        _streamingText.value = ""
                        return@launch
                    }

                    val assistantId = chat.addMessage(
                        ChatMessage(
                            sessionId = sessionId,
                            role = ChatRole.ASSISTANT,
                            content = text,
                            pendingAction = action?.let { AiContextBuilder.actionToJson(it) }.orEmpty()
                        )
                    )
                    _streamingText.value = ""

                    if (action != null) {
                        _pending.value = PendingAction(
                            messageId = assistantId,
                            action = action,
                            details = AiActionParser.describe(action)
                        )
                    }
                    return@launch
                }
            } catch (t: Throwable) {
                _streamingText.value = ""
                _error.value = t.message ?: "生成失败"
            } finally {
                _generating.value = false
            }
        }
    }

    /** 取消生成；已生成的部分内容会作为消息保留。 */
    fun cancelGenerate() {
        generateJob?.cancel()
        generateJob = null
        val partial = _streamingText.value
        _streamingText.value = ""
        _generating.value = false
        if (partial.isNotBlank()) {
            viewModelScope.launch {
                val sessionId = _currentSessionId.value ?: return@launch
                chat.addMessage(
                    ChatMessage(
                        sessionId = sessionId,
                        role = ChatRole.ASSISTANT,
                        content = "$partial\n\n（已取消生成）"
                    )
                )
            }
        }
    }

    // ---------------- 动作确认 ----------------

    fun confirmPendingAction(onLaundry: (Int, String) -> Unit) {
        val pending = _pending.value ?: return
        _pending.value = null
        viewModelScope.launch {
            val ctx = execContext ?: currentContext().also { execContext = it }
            val outcome: AiExecutionResult = try {
                when (val action = pending.action) {
                    is AiAction.StartLaundryTimer -> {
                        // 由 UI 交给 MainViewModel 真正启动倒计时
                        onLaundry(action.minutes, action.labelText ?: "洗衣机")
                        AiExecutionResult("已启动 ${action.minutes} 分钟倒计时")
                    }

                    else -> executor.execute(action, ctx)
                }
            } catch (t: Throwable) {
                AiExecutionResult("执行失败：${t.message}")
            }
            _lastResult.value = outcome.message
            undoToken = outcome.undo
            _undoLabel.value = outcome.undo?.label
            val sessionId = _currentSessionId.value ?: return@launch
            chat.addMessage(ChatMessage(sessionId = sessionId, role = ChatRole.TOOL, content = outcome.message))
            markResolved(sessionId, pending.messageId)
        }
    }

    /** 撤销上一步由 AI 执行的课表修改。 */
    fun undoLastAction() {
        val token = undoToken ?: return
        undoToken = null
        _undoLabel.value = null
        viewModelScope.launch {
            val message = try {
                token.apply()
            } catch (t: Throwable) {
                "撤销失败：${t.message}"
            }
            _lastResult.value = message
            val sessionId = _currentSessionId.value ?: return@launch
            chat.addMessage(ChatMessage(sessionId = sessionId, role = ChatRole.TOOL, content = message))
        }
    }

    fun rejectPendingAction() {
        val pending = _pending.value ?: return
        _pending.value = null
        viewModelScope.launch {
            val sessionId = _currentSessionId.value ?: return@launch
            chat.addMessage(
                ChatMessage(
                    sessionId = sessionId,
                    role = ChatRole.TOOL,
                    content = "用户拒绝了该操作：${pending.action.label}"
                )
            )
            markResolved(sessionId, pending.messageId)
        }
    }

    private suspend fun markResolved(sessionId: Long, messageId: Long) {
        chat.getMessages(sessionId).firstOrNull { it.id == messageId }?.let {
            chat.updateMessage(it.copy(actionResolved = true))
        }
    }

    // ---------------- 图片转 data URL ----------------

    private suspend fun toDataUrl(uri: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = ImageLoader.load(appContext, uri) ?: return@runCatching null
            val scaled = scaleDown(bitmap, 1024)
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
            "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }.getOrNull()
    }

    private fun scaleDown(bitmap: Bitmap, maxSize: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxSize && h <= maxSize) return bitmap
        val ratio = minOf(maxSize.toFloat() / w, maxSize.toFloat() / h)
        return Bitmap.createScaledBitmap(bitmap, (w * ratio).toInt(), (h * ratio).toInt(), true)
    }

    companion object {
        /** 自动查询的最大轮次，防止模型陷入「查了又查」的循环。 */
        private const val MAX_QUERY_ROUNDS = 3

        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = context.applicationContext
                return AiViewModel(
                    appContext = app,
                    chat = AiChatRepository(app),
                    configRepo = AiConfigRepository(app),
                    schedule = ServiceLocator.schedule(app),
                    prefsRepo = ServiceLocator.preferences(app)
                ) as T
            }
        }
    }
}
