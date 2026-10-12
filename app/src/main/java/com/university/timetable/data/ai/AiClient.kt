package com.university.timetable.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/** 聊天消息（传给模型的最小结构）。 */
data class ApiMessage(
    val role: String,
    /** 文本内容 */
    val text: String,
    /** 图片的 data URL（base64），仅多模态模型可用 */
    val imageDataUrls: List<String> = emptyList()
)

/** 调用出错时抛出，携带可读信息。 */
class AiException(message: String, val code: Int = -1) : Exception(message)

/**
 * OpenAI 兼容的 Chat Completions 客户端。
 *
 * - 支持 **流式（SSE）** 输出，边生成边回调，可随时取消
 * - 支持多模态：把图片以 `image_url` + data URL 传入（需模型支持）
 * - 纯 JDK 实现（HttpURLConnection + org.json），不引入额外依赖
 */
object AiClient {

    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 120_000

    /**
     * 流式对话。
     *
     * @param onDelta 每收到一段增量文本回调一次（在主线程外调用，UI 需自行切换线程）
     */
    suspend fun streamChat(
        config: AiConfig,
        messages: List<ApiMessage>,
        onDelta: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        if (!config.isUsable) throw AiException("模型配置不完整：请填写 API 地址与模型名")
        if (config.apiKey.isBlank()) throw AiException("尚未填写 API Key")

        val connection = (URL(config.endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            setRequestProperty("Accept", "text/event-stream")
        }

        try {
            val payload = buildPayload(config, messages, stream = true)
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }

            val status = connection.responseCode
            if (status !in 200..299) {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw AiException(describeHttpError(status, error), status)
            }

            val result = StringBuilder()
            BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                while (true) {
                    // 支持随时取消
                    coroutineContext.ensureActive()
                    val line = reader.readLine() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data.isEmpty()) continue
                    if (data == "[DONE]") break

                    val delta = extractDelta(data)
                    if (delta != null && delta.isNotEmpty()) {
                        result.append(delta)
                        onDelta(delta)
                    }
                }
            }

            if (result.isEmpty()) {
                throw AiException("模型没有返回内容（可能是模型名不正确或额度不足）")
            }
            result.toString()
        } catch (e: AiException) {
            throw e
        } catch (e: Exception) {
            throw AiException("网络请求失败：${e.message ?: e.javaClass.simpleName}")
        } finally {
            connection.disconnect()
        }
    }

    /** 连接测试：发一条极短的请求，确认 Key / 地址 / 模型可用。 */
    suspend fun testConnection(config: AiConfig): String = withContext(Dispatchers.IO) {
        val reply = streamChat(
            config = config,
            messages = listOf(ApiMessage("user", "请只回复两个字：可用"))
        ) { }
        reply.take(60)
    }

    // ------------------------------------------------------------------

    private fun buildPayload(
        config: AiConfig,
        messages: List<ApiMessage>,
        stream: Boolean
    ): JSONObject {
        val array = JSONArray()
        messages.forEach { m ->
            val obj = JSONObject().put("role", m.role)
            if (m.imageDataUrls.isEmpty()) {
                obj.put("content", m.text)
            } else {
                // 多模态：content 为数组
                val content = JSONArray()
                if (m.text.isNotBlank()) {
                    content.put(JSONObject().put("type", "text").put("text", m.text))
                }
                m.imageDataUrls.forEach { url ->
                    content.put(
                        JSONObject()
                            .put("type", "image_url")
                            .put("image_url", JSONObject().put("url", url))
                    )
                }
                obj.put("content", content)
            }
            array.put(obj)
        }

        return JSONObject()
            .put("model", config.model)
            .put("messages", array)
            .put("stream", stream)
            .put("temperature", config.temperature)
            .put("max_tokens", config.maxTokens)
    }

    /** 从 SSE 的一行 JSON 中取出增量文本。 */
    private fun extractDelta(json: String): String? = runCatching {
        val obj = JSONObject(json)
        val choices = obj.optJSONArray("choices") ?: return null
        if (choices.length() == 0) return null
        val choice = choices.getJSONObject(0)
        choice.optJSONObject("delta")?.optString("content")?.takeIf { it.isNotEmpty() }
            ?: choice.optJSONObject("message")?.optString("content")?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    /** 把 HTTP 错误码翻译成用户能看懂的话。 */
    private fun describeHttpError(status: Int, body: String): String {
        val detail = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message")
        }.getOrNull().orEmpty()

        val base = when (status) {
            401 -> "API Key 无效或已过期（401）"
            402 -> "账户余额不足（402）"
            403 -> "无权访问该模型（403）"
            404 -> "API 地址或模型名不正确（404）：请确认地址填到 /v1"
            422 -> "请求参数不被接受（422）"
            429 -> "请求过于频繁或超出额度（429）"
            in 500..599 -> "服务端错误（$status），请稍后重试"
            else -> "请求失败（$status）"
        }
        return if (detail.isBlank()) base else "$base\n$detail"
    }

    @Suppress("unused")
    private fun unusedTimeUnit() = TimeUnit.SECONDS
}
