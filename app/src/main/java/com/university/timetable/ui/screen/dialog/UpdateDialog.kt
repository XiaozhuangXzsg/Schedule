package com.university.timetable.ui.screen.dialog

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.university.timetable.ui.component.glassLayer
import com.university.timetable.ui.theme.LocalTimetableColors
import com.university.timetable.update.UpdateConfig
import com.university.timetable.update.UpdateUiState
import java.util.Locale

/**
 * 应用内更新弹窗：只展示信息与三种动作（立即更新 / 稍后再说 / 去授权），
 * 所有网络、下载、校验、安装逻辑都在 [com.university.timetable.update.UpdateViewModel] 里。
 *
 * 视觉沿用应用现有的毛玻璃弹窗风格，颜色全部取自主题，因此深色模式自动适配。
 */
@Composable
fun UpdateDialog(
    state: UpdateUiState,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onGrantPermission: () -> Unit,
    onPostpone: () -> Unit,
    onDismiss: () -> Unit
) {
    val release = state.release ?: return
    val colors = LocalTimetableColors.current

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .glassLayer(MaterialTheme.colorScheme.surface)
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.SystemUpdateAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (state.phase == UpdateUiState.Phase.InstallReady) "安装包已就绪" else "发现新版本",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = UpdateConfig.CURRENT_VERSION_LABEL + " → v" + release.versionName,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.mutedText
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoChip(label = "安装包", value = formatBytes(release.apkSize))
                InfoChip(label = "版本号", value = release.versionCode.toString())
            }

            if (release.notes.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "更新内容",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = release.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.mutedText,
                    modifier = Modifier
                        .heightIn(max = 168.dp)
                        .verticalScroll(rememberScrollState())
                )
            }

            when (state.phase) {
                UpdateUiState.Phase.Downloading -> {
                    Spacer(Modifier.height(16.dp))
                    val total = if (state.total > 0L) state.total else release.apkSize
                    if (state.downloaded > 0L && total > 0L) {
                        LinearProgressIndicator(
                            progress = { (state.downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (total > 0L) {
                                formatBytes(state.downloaded) + " / " + formatBytes(total)
                            } else {
                                "正在下载…"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.mutedText
                        )
                        Text(
                            text = if (state.bytesPerSecond > 0L) formatSpeed(state.bytesPerSecond) else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.mutedText
                        )
                    }
                }

                UpdateUiState.Phase.Verifying -> {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "正在校验安装包（SHA-256、包名、版本号与签名）…",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.mutedText
                    )
                }

                UpdateUiState.Phase.NeedsPermission -> {
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Filled.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "系统还没有允许本应用安装应用。授权后返回本页，点击「我已授权」即可继续。",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.mutedText
                        )
                    }
                }

                else -> Unit
            }

            if (state.errorMessage != null) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = state.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (state.phase == UpdateUiState.Phase.InstallReady) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "校验通过，点击安装会打开系统安装界面，覆盖安装不会清除数据。",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.mutedText
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            when (state.phase) {
                UpdateUiState.Phase.NeedsPermission -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onPostpone) { Text("稍后再说") }
                        Spacer(Modifier.width(4.dp))
                        TextButton(onClick = onGrantPermission) { Text("去授权") }
                        Spacer(Modifier.width(4.dp))
                        Button(onClick = onDownload) { Text("我已授权") }
                    }
                }

                UpdateUiState.Phase.InstallReady -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onPostpone) { Text("稍后安装") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = onInstall) { Text("立即安装") }
                    }
                }

                UpdateUiState.Phase.Downloading -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) { Text("暂停下载") }
                    }
                }

                UpdateUiState.Phase.Verifying -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) { Text("关闭") }
                    }
                }

                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onPostpone) { Text("稍后再说") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = onDownload) {
                            Text(if (state.errorMessage != null) "重试下载" else "立即更新")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoChip(label: String, value: String) {
    val colors = LocalTimetableColors.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceCardElevated)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.mutedText
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

/** 备份/更新弹窗共用的体积格式化。 */
internal fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "未知大小"
    val kb = 1024.0
    val mb = kb * 1024.0
    return when {
        bytes >= mb -> String.format(Locale.CHINA, "%.1f MB", bytes / mb)
        bytes >= kb -> String.format(Locale.CHINA, "%.0f KB", bytes / kb)
        else -> bytes.toString() + " B"
    }
}

private fun formatSpeed(bytesPerSecond: Long): String =
    formatBytes(bytesPerSecond) + "/s"

/** 恢复数据前确认用的提示框（不能取消，避免数据处于半恢复状态）。 */
@Composable
fun RestoreConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("从备份恢复？") },
        text = {
            Text("恢复会覆盖当前的课表、待办、设置和 AI 配置。恢复前会自动把当前数据另存一份到应用内的 backup 目录。恢复完成后应用会自动关闭，重新打开娅娅课表即可生效。")
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("继续恢复") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}