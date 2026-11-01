package com.university.timetable.update

import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/**
 * 极简 GET 请求工具。
 *
 * - 只走 HTTPS（调用方传入的都是 https 地址）；
 * - 不跟随到 http 的重定向（[HttpURLConnection] 默认不跨协议跟随）；
 * - 不做任何静默重试，失败信息统一转成中文 [UpdateException]。
 */
class HttpText {

    data class Response(
        val code: Int,
        val body: String,
        val etag: String?,
        val rateRemaining: String?
    )

    fun get(url: String, accept: String = "application/json"): Response {
        require(url.startsWith("https://")) { "更新请求必须使用 HTTPS" }
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = UpdateConfig.CONNECT_TIMEOUT_MS
                readTimeout = UpdateConfig.READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("Accept", accept)
                setRequestProperty(
                    "User-Agent",
                    UpdateConfig.USER_AGENT + "/" + UpdateConfig.CURRENT_VERSION_NAME
                )
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return Response(
                code = code,
                body = body,
                etag = connection.getHeaderField("ETag"),
                rateRemaining = connection.getHeaderField("X-RateLimit-Remaining")
            )
        } catch (error: UpdateException) {
            throw error
        } catch (error: SocketTimeoutException) {
            throw UpdateException("连接 GitHub 超时，请检查网络后重试。", UpdateException.Kind.NETWORK)
        } catch (error: UnknownHostException) {
            throw UpdateException("无法连接网络，请检查网络设置后重试。", UpdateException.Kind.NETWORK)
        } catch (error: IOException) {
            throw UpdateException(
                "网络请求失败：" + (error.message ?: "请检查网络连接后重试"),
                UpdateException.Kind.NETWORK
            )
        } finally {
            connection?.disconnect()
        }
    }
}
