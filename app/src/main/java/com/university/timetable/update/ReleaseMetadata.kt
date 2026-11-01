package com.university.timetable.update

import org.json.JSONObject

/** Release 中 version.json 的内容（由 CI 生成）。 */
data class ReleaseMetadata(
    val versionCode: Long,
    val versionName: String,
    val tagName: String?,
    val apkName: String?,
    val apkUrl: String?,
    val sha256: String,
    val notes: String?
)

object ReleaseMetadataParser {

    private val SHA256_REGEX = Regex("^[a-fA-F0-9]{64}$")

    fun isSha256(value: String): Boolean = SHA256_REGEX.matches(value.trim())

    /** 接受 "sha256:abc..."、"ABC..." 等写法，统一转成小写 64 位十六进制。 */
    fun normalizeSha256(value: String): String =
        value.trim().removePrefix("sha256:").removePrefix("SHA256:").lowercase()

    fun parse(text: String): ReleaseMetadata {
        val json = try {
            JSONObject(text)
        } catch (error: Exception) {
            throw UpdateException(
                "版本信息文件不是合法的 JSON，已停止更新。",
                UpdateException.Kind.BAD_METADATA
            )
        }
        val versionCode = json.optLong("versionCode", -1L)
        if (versionCode <= 0L) {
            throw UpdateException(
                "版本信息缺少有效的 versionCode，已停止更新。",
                UpdateException.Kind.BAD_METADATA
            )
        }
        val versionName = json.optString("versionName").trim()
        if (versionName.isEmpty()) {
            throw UpdateException(
                "版本信息缺少 versionName，已停止更新。",
                UpdateException.Kind.BAD_METADATA
            )
        }
        val sha256 = normalizeSha256(json.optString("sha256"))
        if (!isSha256(sha256)) {
            throw UpdateException(
                "版本信息中的 SHA-256 无效，出于安全考虑已停止更新。",
                UpdateException.Kind.BAD_METADATA
            )
        }
        val apkUrl = json.optString("apkUrl").trim()
            .takeIf { it.startsWith("https://", ignoreCase = true) }
        return ReleaseMetadata(
            versionCode = versionCode,
            versionName = versionName,
            tagName = json.optString("tagName").takeIf { it.isNotBlank() && it != "null" },
            apkName = json.optString("apkName").takeIf { it.isNotBlank() && it != "null" },
            apkUrl = apkUrl,
            sha256 = sha256,
            notes = json.optString("notes").takeIf { it.isNotBlank() && it != "null" }
        )
    }
}
