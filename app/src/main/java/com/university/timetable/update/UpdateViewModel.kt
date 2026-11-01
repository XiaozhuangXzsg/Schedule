package com.university.timetable.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.university.timetable.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 更新流程的界面状态。 */
data class UpdateUiState(
    val checking: Boolean = false,
    val release: UpdateRelease? = null,
    val phase: Phase = Phase.Idle,
    val downloaded: Long = 0L,
    val total: Long = 0L,
    val bytesPerSecond: Long = 0L,
    val errorMessage: String? = null,
    val showPostponeHint: Boolean = false
) {
    enum class Phase { Idle, Downloading, Verifying, InstallReady, NeedsPermission }
}

class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val repository = UpdateRepository(context)
    private val prefsRepo = ServiceLocator.preferences(context)

    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private var checkJob: Job? = null
    private var downloadJob: Job? = null
    private var lastAutoAttemptAt = 0L

    /** 启动时调用：默认每天最多自动检查一次，且遵循「稍后再说」的间隔。 */
    fun autoCheckIfDue() {
        if (checkJob?.isActive == true || downloadJob?.isActive == true) return
        if (_state.value.release != null) return
        val now = System.currentTimeMillis()
        if (now - lastAutoAttemptAt < 60_000L) return
        lastAutoAttemptAt = now
        viewModelScope.launch {
            val prefs = prefsRepo.preferences.first()
            if (!prefs.autoCheckUpdates) return@launch
            if (now - prefs.lastUpdateCheckAt < UpdateConfig.AUTO_CHECK_INTERVAL_MS) return@launch
            runCheck(manual = false)
        }
    }

    fun checkManually() {
        if (checkJob?.isActive == true || downloadJob?.isActive == true) return
        viewModelScope.launch { runCheck(manual = true) }
    }

    private suspend fun runCheck(manual: Boolean) {
        if (UpdatePolicy.isInstalledFromPlay(context)) {
            if (manual) {
                _events.emit("当前版本由 Google Play 分发，请在 Play 商店中更新。")
            }
            prefsRepo.setLastUpdateCheckAt(System.currentTimeMillis())
            return
        }
        _state.update { it.copy(checking = true, errorMessage = null) }
        val result = withContext(Dispatchers.IO) {
            runCatching { repository.fetchLatest() }
        }
        val now = System.currentTimeMillis()
        _state.update { it.copy(checking = false) }
        result.onSuccess { release ->
            // 只有真正拿到 GitHub 响应才记入「今天检查过」，网络失败时下次启动会重试。
            prefsRepo.setLastUpdateCheckAt(now)
            if (release == null) {
                if (manual) _events.emit("当前已是最新版本")
                return
            }
            val prefs = prefsRepo.preferences.first()
            val postponedRecently = prefs.postponedVersionCode == release.versionCode.toInt() &&
                now - prefs.postponedAt < UpdateConfig.POSTPONE_INTERVAL_MS
            if (!manual && postponedRecently) {
                return
            }
            _state.update { it.copy(release = release, phase = UpdateUiState.Phase.Idle, errorMessage = null) }
        }.onFailure { error ->
            val message = (error as? UpdateException)?.message ?: "检查更新失败，请稍后重试。"
            if (manual) _events.emit(message)
        }
    }

    fun startDownload() {
        val release = _state.value.release ?: return
        if (downloadJob?.isActive == true) return
        if (!UpdateInstaller.canInstall(context)) {
            _state.update { it.copy(phase = UpdateUiState.Phase.NeedsPermission) }
            return
        }
        _state.update {
            it.copy(
                phase = UpdateUiState.Phase.Downloading,
                downloaded = 0L,
                total = release.apkSize,
                bytesPerSecond = 0L,
                errorMessage = null
            )
        }
        downloadJob = viewModelScope.launch {
            try {
                val apk = repository.download(release) { downloaded, total, speed ->
                    _state.update { state ->
                        state.copy(
                            downloaded = downloaded,
                            total = if (total > 0L) total else state.total,
                            bytesPerSecond = speed
                        )
                    }
                }
                _state.update { it.copy(phase = UpdateUiState.Phase.Verifying) }
                val verify = withContext(Dispatchers.IO) { repository.verify(release, apk) }
                if (!verify.ok) {
                    runCatching { apk.delete() }
                    _state.update {
                        it.copy(
                            phase = UpdateUiState.Phase.Idle,
                            errorMessage = verify.message
                        )
                    }
                    _events.emit(verify.message)
                    return@launch
                }
                _state.update { it.copy(phase = UpdateUiState.Phase.InstallReady, errorMessage = null) }
                // 校验通过后直接调起系统安装器；若系统拦截（如后台启动限制），
                // 弹窗会停在「安装包已就绪」，用户点「立即安装」可再试。
                installNow()
            } catch (error: UpdateException) {
                _state.update {
                    it.copy(
                        phase = UpdateUiState.Phase.Idle,
                        errorMessage = error.message ?: "下载失败，请重试。"
                    )
                }
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                _state.update {
                    it.copy(
                        phase = UpdateUiState.Phase.Idle,
                        errorMessage = "下载失败：" + (error.message ?: "请稍后重试。")
                    )
                }
            }
        }
    }

    /** 调起系统安装器。安装前再校验一次，避免磁盘上残留的旧包被安装。 */
    fun installNow() {
        val release = _state.value.release
        if (release == null) {
            _events.tryEmit("更新信息已失效，请重新检查更新。")
            return
        }
        val apk = UpdateConfig.apkFile(context)
        viewModelScope.launch {
            _state.update { it.copy(phase = UpdateUiState.Phase.Verifying, errorMessage = null) }
            val verify = withContext(Dispatchers.IO) {
                runCatching { repository.verify(release, apk) }.getOrNull()
            }
            if (verify == null || !verify.ok) {
                val message = verify?.message ?: "安装包校验失败，请重新下载。"
                runCatching { apk.delete() }
                _state.update { it.copy(phase = UpdateUiState.Phase.Idle, errorMessage = message) }
                _events.emit(message)
                return@launch
            }
            _state.update { it.copy(phase = UpdateUiState.Phase.InstallReady, errorMessage = null) }
            val result = runCatching {
                withContext(Dispatchers.IO) { repository.install(apk) }
            }
            result.onFailure { error ->
                val message = (error as? UpdateException)?.message ?: "无法调起系统安装器，请稍后重试。"
                _state.update { it.copy(errorMessage = message) }
                _events.emit(message)
            }
        }
    }

    fun openPermissionSettings() {
        runCatching { UpdateInstaller.openInstallPermissionSettings(context) }
            .onFailure { _events.tryEmit((it as? UpdateException)?.message ?: "无法打开授权页面。") }
    }

    fun recheckPermission() {
        if (UpdateInstaller.canInstall(context)) {
            _state.update { it.copy(phase = UpdateUiState.Phase.Idle) }
            startDownload()
        } else {
            _events.tryEmit("仍未获得「安装未知应用」权限。")
        }
    }

    fun postpone() {
        val release = _state.value.release ?: return
        downloadJob?.cancel()
        viewModelScope.launch {
            prefsRepo.setPostponed(release.versionCode.toInt(), System.currentTimeMillis())
        }
        _state.update {
            it.copy(
                release = null,
                phase = UpdateUiState.Phase.Idle,
                downloaded = 0L,
                total = 0L,
                bytesPerSecond = 0L,
                errorMessage = null
            )
        }
        _events.tryEmit("已稍后提醒，可在「设置 → 软件更新」中随时检查。")
    }

    fun dismiss() {
        downloadJob?.cancel()
        _state.update {
            it.copy(
                release = null,
                phase = UpdateUiState.Phase.Idle,
                downloaded = 0L,
                total = 0L,
                bytesPerSecond = 0L,
                errorMessage = null
            )
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun cleanupDownloads() {
        viewModelScope.launch(Dispatchers.IO) { runCatching { repository.cleanup() } }
    }
}
