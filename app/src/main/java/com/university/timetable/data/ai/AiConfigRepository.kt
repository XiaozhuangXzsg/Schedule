package com.university.timetable.data.ai

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * 一个 AI 模型配置。
 *
 * 兼容 OpenAI Chat Completions 协议的任意服务（DeepSeek / OpenAI / 通义 / 自建网关等）。
 */
data class AiConfig(
    val id: String,
    /** 显示名称，例如「DeepSeek 官方」 */
    val name: String,
    /** API 基地址，例如 https://api.deepseek.com/v1（请填到 /v1） */
    val baseUrl: String,
    /** 模型名，例如 deepseek-chat */
    val model: String,
    /** 已解密的 API Key（仅内存中使用） */
    val apiKey: String = "",
    /** 是否支持图片输入（多模态模型） */
    val supportsImages: Boolean = false,
    /** 每次请求最大输出 token */
    val maxTokens: Int = 2048,
    /** 采样温度 */
    val temperature: Double = 0.7
) {
    /** 规范化后的 chat/completions 地址。 */
    val endpoint: String
        get() {
            val base = baseUrl.trim().trimEnd('/')
            return if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
        }

    val isUsable: Boolean get() = baseUrl.isNotBlank() && model.isNotBlank()
}

/**
 * AI 配置仓库：Key 经 [SecureKeyStore] 加密后存入 DataStore。
 */
private val Context.aiDataStore: DataStore<Preferences> by preferencesDataStore(name = "ai_settings")

class AiConfigRepository(private val context: Context) {

    private object Keys {
        val configs = stringPreferencesKey("ai_configs")
        val activeId = stringPreferencesKey("ai_active_id")
        val enabled = booleanPreferencesKey("ai_enabled")
        val allowTools = booleanPreferencesKey("ai_allow_tools")
    }

    /**
     * 配置列表（含已解密的 Key）。
     * 存储格式：每条一行，字段以 `\u0001` 分隔；Key 字段为密文。
     */
    val configs: Flow<List<AiConfig>> = context.aiDataStore.data.map { prefs ->
        decode(prefs[Keys.configs].orEmpty())
    }

    val activeId: Flow<String> = context.aiDataStore.data.map { it[Keys.activeId].orEmpty() }

    /** 是否允许 AI 在用户确认后执行写操作。 */
    val allowTools: Flow<Boolean> = context.aiDataStore.data.map { it[Keys.allowTools] ?: true }

    suspend fun current(): AiConfig? {
        val prefs = context.aiDataStore.data.first()
        val list = decode(prefs[Keys.configs].orEmpty())
        val id = prefs[Keys.activeId].orEmpty()
        return list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }

    suspend fun upsert(config: AiConfig) {
        context.aiDataStore.edit { prefs ->
            val list = decode(prefs[Keys.configs].orEmpty()).toMutableList()
            val index = list.indexOfFirst { it.id == config.id }
            if (index >= 0) list[index] = config else list += config
            prefs[Keys.configs] = encode(list)
            if (prefs[Keys.activeId].isNullOrEmpty()) {
                prefs[Keys.activeId] = config.id
            }
        }
    }

    suspend fun delete(id: String) {
        context.aiDataStore.edit { prefs ->
            val list = decode(prefs[Keys.configs].orEmpty()).filterNot { it.id == id }
            prefs[Keys.configs] = encode(list)
            if (prefs[Keys.activeId] == id) {
                prefs[Keys.activeId] = list.firstOrNull()?.id.orEmpty()
            }
        }
    }

    suspend fun setActive(id: String) {
        context.aiDataStore.edit { it[Keys.activeId] = id }
    }

    suspend fun setAllowTools(value: Boolean) {
        context.aiDataStore.edit { it[Keys.allowTools] = value }
    }

    // ---------------- 编解码 ----------------

    private val sep = '\u0001'

    private fun encode(list: List<AiConfig>): String = list.joinToString("\n") { c ->
        listOf(
            c.id,
            c.name,
            c.baseUrl,
            c.model,
            SecureKeyStore.encrypt(c.apiKey) ?: "",
            if (c.supportsImages) "1" else "0",
            c.maxTokens.toString(),
            c.temperature.toString()
        ).joinToString(sep.toString())
    }

    private fun decode(raw: String): List<AiConfig> {
        if (raw.isBlank()) return emptyList()
        return raw.split("\n").mapNotNull { line ->
            val f = line.split(sep)
            if (f.size < 4) return@mapNotNull null
            AiConfig(
                id = f[0],
                name = f[1],
                baseUrl = f[2],
                model = f[3],
                apiKey = SecureKeyStore.decrypt(f.getOrElse(4) { "" }),
                supportsImages = f.getOrElse(5) { "0" } == "1",
                maxTokens = f.getOrElse(6) { "2048" }.toIntOrNull() ?: 2048,
                temperature = f.getOrElse(7) { "0.7" }.toDoubleOrNull() ?: 0.7
            )
        }
    }
}

/** 内置的常用服务商预设，便于一键填写。 */
object AiPresets {
    data class Preset(val label: String, val baseUrl: String, val model: String, val images: Boolean)

    val all = listOf(
        Preset("DeepSeek（推荐）", "https://api.deepseek.com/v1", "deepseek-chat", false),
        Preset("DeepSeek 推理", "https://api.deepseek.com/v1", "deepseek-reasoner", false),
        Preset("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini", true),
        Preset("通义千问（兼容模式）", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus", true),
        Preset("月之暗面 Kimi", "https://api.moonshot.cn/v1", "moonshot-v1-8k", false),
        Preset("智谱 GLM", "https://open.bigmodel.cn/api/paas/v4", "glm-4-flash", false),
        Preset("自定义", "", "", false)
    )
}
