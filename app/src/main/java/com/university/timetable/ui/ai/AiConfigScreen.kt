package com.university.timetable.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.university.timetable.ui.component.glassLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.university.timetable.data.ai.AiConfig
import com.university.timetable.data.ai.AiPresets
import com.university.timetable.data.ai.SecureKeyStore
import com.university.timetable.ui.component.LiquidGlassSwitch
import com.university.timetable.ui.theme.LocalTimetableColors
import java.util.UUID

/**
 * 模型配置页：管理多个 API Key 与模型参数，可测试连接。
 */
@Composable
fun AiConfigScreen(
    viewModel: AiViewModel,
    configs: List<AiConfig>,
    activeId: String,
    onBack: () -> Unit
) {
    val colors = LocalTimetableColors.current
    var editing by remember { mutableStateOf<AiConfig?>(null) }
    var creating by remember { mutableStateOf(false) }

    // 统一处理状态栏留白与边缘滑动返回
    AiSubPage(onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
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
                    text = "模型配置",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "兼容 OpenAI 接口的服务均可，推荐 DeepSeek",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.mutedText
                )
            }
            IconButton(
                onClick = { creating = true },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "添加模型",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        if (configs.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Filled.Key,
                    contentDescription = null,
                    tint = colors.mutedText,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "还没有配置模型",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "点右上角「+」添加。需要填写 API 地址、模型名与 API Key。\n" +
                        "Key 会用系统密钥库加密后保存在本机，不会上传到任何第三方。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.mutedText
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, bottom = 24.dp
                )
            ) {
                items(configs, key = { it.id }) { config ->
                    ConfigCard(
                        config = config,
                        isActive = config.id == activeId,
                        onActivate = { viewModel.setActiveConfig(config.id) },
                        onEdit = { editing = config },
                        onDelete = { viewModel.deleteConfig(config.id) }
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
        }
    }

    val target = editing
    if (creating || target != null) {
        AiConfigEditorDialog(
            existing = target,
            onDismiss = {
                creating = false
                editing = null
            },
            onTest = { config, callback -> viewModel.testConfig(config, callback) },
            onSave = { config ->
                viewModel.saveConfig(config)
                creating = false
                editing = null
            }
        )
    }
}

@Composable
private fun ConfigCard(
    config: AiConfig,
    isActive: Boolean,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalTimetableColors.current

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
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(
                        if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Key,
                    contentDescription = null,
                    tint = if (isActive) MaterialTheme.colorScheme.onPrimary
                    else colors.mutedText,
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = config.name.ifBlank { "未命名模型" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${config.model} · ${SecureKeyStore.mask(config.apiKey)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.mutedText
                )
            }
            if (isActive) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "使用中",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = config.baseUrl.ifBlank { "（未填写 API 地址）" },
            style = MaterialTheme.typography.labelSmall,
            color = colors.mutedText
        )

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!isActive) {
                TextButton(onClick = onActivate) { Text("设为使用中") }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "当前使用",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "编辑",
                    tint = colors.mutedText,
                    modifier = Modifier.size(17.dp)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

/**
 * 新增 / 编辑模型配置。含常用服务商预设与「测试连接」。
 */
@Composable
private fun AiConfigEditorDialog(
    existing: AiConfig?,
    onDismiss: () -> Unit,
    onTest: (AiConfig, (Boolean, String) -> Unit) -> Unit,
    onSave: (AiConfig) -> Unit
) {
    val colors = LocalTimetableColors.current
    var name by remember { mutableStateOf(existing?.name ?: "DeepSeek") }
    var baseUrl by remember { mutableStateOf(existing?.baseUrl ?: AiPresets.all.first().baseUrl) }
    var model by remember { mutableStateOf(existing?.model ?: AiPresets.all.first().model) }
    var apiKey by remember { mutableStateOf(existing?.apiKey ?: "") }
    var supportsImages by remember { mutableStateOf(existing?.supportsImages ?: false) }
    var maxTokens by remember { mutableStateOf((existing?.maxTokens ?: 2048).toFloat()) }
    var temperature by remember { mutableStateOf((existing?.temperature ?: 0.7).toFloat()) }
    var showKey by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    fun current() = AiConfig(
        id = existing?.id ?: UUID.randomUUID().toString(),
        name = name.trim().ifBlank { "未命名" },
        baseUrl = baseUrl.trim(),
        model = model.trim(),
        apiKey = apiKey.trim(),
        supportsImages = supportsImages,
        maxTokens = maxTokens.toInt(),
        temperature = temperature.toDouble()
    )

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .clip(RoundedCornerShape(22.dp))
                .glassLayer(MaterialTheme.colorScheme.surface)
                .padding(18.dp)
        ) {
            Text(
                text = if (existing == null) "添加模型" else "编辑模型",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(12.dp))

            Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                Text(
                    text = "常用服务商",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.mutedText
                )
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AiPresets.all.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { preset ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable {
                                            if (preset.baseUrl.isNotBlank()) baseUrl = preset.baseUrl
                                            if (preset.model.isNotBlank()) model = preset.model
                                            name = preset.label
                                            supportsImages = preset.images
                                        }
                                        .padding(horizontal = 8.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        text = preset.label,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称（自定义，便于区分）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("API 地址（到 /v1 为止）") },
                    placeholder = { Text("https://api.deepseek.com/v1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("模型名称") },
                    placeholder = { Text("deepseek-chat") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    visualTransformation = if (showKey) androidx.compose.ui.text.input.VisualTransformation.None
                    else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { showKey = !showKey }) {
                            Text(if (showKey) "隐藏" else "显示", fontSize = 11.sp)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Key 使用 Android 系统密钥库加密后保存在本机，不会上传到任何第三方。",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.mutedText
                )

                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "支持图片输入（多模态）",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "如 gpt-4o / qwen-vl 等；DeepSeek 文本模型请关闭",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.mutedText
                        )
                    }
                    LiquidGlassSwitch(checked = supportsImages, onCheckedChange = { supportsImages = it })
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = "最大输出 ${maxTokens.toInt()} tokens",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Slider(
                    value = maxTokens,
                    onValueChange = { maxTokens = it },
                    valueRange = 256f..8192f
                )

                Text(
                    text = "随机性 ${"%.1f".format(temperature)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Slider(
                    value = temperature,
                    onValueChange = { temperature = it },
                    valueRange = 0f..2f
                )

                testResult?.let { (ok, msg) ->
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (ok) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.errorContainer
                            )
                            .padding(10.dp)
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (ok) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = {
                        testing = true
                        testResult = null
                        onTest(current()) { ok, msg ->
                            testing = false
                            testResult = ok to msg
                        }
                    },
                    enabled = !testing
                ) {
                    if (testing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                    } else {
                        Icon(
                            Icons.Filled.NetworkCheck,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text("测试连接")
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("取消") }
                Button(
                    onClick = { onSave(current()) },
                    enabled = baseUrl.isNotBlank() && model.isNotBlank()
                ) { Text("保存") }
            }
        }
    }
}
