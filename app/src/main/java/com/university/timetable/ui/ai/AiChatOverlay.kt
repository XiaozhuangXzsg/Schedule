package com.university.timetable.ui.ai

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * AI 助手覆盖层：负责收集 [AiViewModel] 的各路状态并交给 [AiChatScreen]。
 *
 * 状态被刻意拆开（而不是一个大 UiState），这样任意一处变化只重组对应部分。
 */
@Composable
fun AiChatOverlay(
    viewModel: AiViewModel,
    onBack: () -> Unit,
    onOpenConfig: () -> Unit,
    onStartLaundry: (Int, String) -> Unit
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val currentSessionId by viewModel.currentSessionId.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val streamingText by viewModel.streamingText.collectAsStateWithLifecycle()
    val generating by viewModel.generating.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val pending by viewModel.pendingAction.collectAsStateWithLifecycle()
    val attachments by viewModel.attachments.collectAsStateWithLifecycle()
    val allowTools by viewModel.allowTools.collectAsStateWithLifecycle()
    val lastResult by viewModel.lastActionResult.collectAsStateWithLifecycle()
    val undoLabel by viewModel.undoLabel.collectAsStateWithLifecycle()

    AiSubPage(onBack = onBack) {
        AiChatScreen(
            viewModel = viewModel,
            sessions = sessions,
            currentSessionId = currentSessionId,
            messages = messages,
            streamingText = streamingText,
            generating = generating,
            error = error,
            pending = pending,
            attachments = attachments,
            allowTools = allowTools,
            lastResult = lastResult,
            undoLabel = undoLabel,
            onUndo = { viewModel.undoLastAction() },
            onDismissResult = { viewModel.dismissActionResult() },
            onBack = onBack,
            onOpenConfig = onOpenConfig,
            onStartLaundry = onStartLaundry
        )
    }
}
