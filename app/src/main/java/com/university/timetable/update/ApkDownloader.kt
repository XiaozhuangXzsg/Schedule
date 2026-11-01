package com.university.timetable.update

import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * APK 下载器。
 *
 * - 文件写在应用私有目录 files/updates/（不需要任何存储权限）；
 * - 先写 .part，支持 Range 断点续传：中断后点「重试」会从已下载的位置继续；
 * - 校验总数与上限，防止异常响应写满磁盘。
 */
class ApkDownloader {

    suspend fun download(
        url: String,
        target: File,
        expectedSize: Long,
        onProgress: (downloaded: Long, total: Long, bytesPerSecond: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        require(url.startsWith("https://")) { "下载地址必须使用 HTTPS" }
        if (expectedSize > UpdateConfig.MAX_APK_BYTES) {
            throw UpdateException("安装包体积异常（超过 300MB），已取消下载。", UpdateException.Kind.IO)
        }
        val part = File(target.path + UpdateConfig.PART_SUFFIX)
        var existing = if (part.exists()) part.length() else 0L
        if (existing > 0L && (expectedSize <= 0L || existing > expectedSize)) {
            part.delete()
            existing = 0L
        }

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = UpdateConfig.CONNECT_TIMEOUT_MS
                readTimeout = UpdateConfig.READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/octet-stream")
                setRequestProperty(
                    "User-Agent",
                    UpdateConfig.USER_AGENT + "/" + UpdateConfig.CURRENT_VERSION_NAME
                )
                if (existing > 0L) {
                    setRequestProperty("Range", "bytes=" + existing + "-")
                }
            }
            val code = connection.responseCode
            val resuming = code == HttpURLConnection.HTTP_PARTIAL && existing > 0L
            if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                throw UpdateException(
                    "下载安装包失败（HTTP " + code + "），请稍后重试。",
                    UpdateException.Kind.NETWORK
                )
            }
            if (!resuming) {
                existing = 0L
                if (part.exists()) part.delete()
            }

            val contentLength = connection.contentLengthLong
            val total = when {
                resuming && contentLength > 0L -> existing + contentLength
                expectedSize > 0L -> expectedSize
                else -> contentLength
            }
            if (total > UpdateConfig.MAX_APK_BYTES) {
                throw UpdateException("安装包体积异常（超过 300MB），已取消下载。", UpdateException.Kind.IO)
            }

            var downloaded = existing
            var lastEmitAt = 0L
            var speedWindowStart = SystemClock.elapsedRealtime()
            var speedWindowBytes = 0L
            var bytesPerSecond = 0L

            connection.inputStream.use { input ->
                FileOutputStream(part, resuming).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        speedWindowBytes += read
                        val now = SystemClock.elapsedRealtime()
                        if (now - speedWindowStart >= 500L) {
                            val elapsed = now - speedWindowStart
                            if (elapsed > 0L) {
                                bytesPerSecond = speedWindowBytes * 1000L / elapsed
                            }
                            speedWindowStart = now
                            speedWindowBytes = 0L
                        }
                        if (now - lastEmitAt >= 120L) {
                            lastEmitAt = now
                            onProgress(downloaded, total, bytesPerSecond)
                        }
                    }
                }
            }
            onProgress(downloaded, total, bytesPerSecond)

            if (total > 0L && downloaded < total) {
                throw UpdateException(
                    "下载中断：已下载 " + downloaded + " / " + total + " 字节，可点击重试继续下载。",
                    UpdateException.Kind.IO
                )
            }
            if (part.length() > UpdateConfig.MAX_APK_BYTES) {
                part.delete()
                throw UpdateException("安装包体积异常，已取消下载。", UpdateException.Kind.IO)
            }
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) {
                part.copyTo(target, overwrite = true)
                part.delete()
            }
            target
        } catch (error: CancellationException) {
            throw error
        } catch (error: UpdateException) {
            throw error
        } catch (error: IOException) {
            throw UpdateException(
                "下载失败：" + (error.message ?: "网络连接中断") + "，可点击重试继续下载。",
                UpdateException.Kind.IO
            )
        } finally {
            connection?.disconnect()
        }
    }
}
