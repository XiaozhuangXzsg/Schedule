package com.university.timetable.update

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * 安装包校验。
 *
 * 校验顺序：文件存在 → SHA-256（对实际下载的文件实算）→ 包名 → 版本号（必须等于发布信息、
 * 且高于已安装版本）→ 签名是否与当前应用兼容。任意一步失败都拒绝安装。
 */
object ApkVerifier {

    data class Result(val ok: Boolean, val message: String)

    fun isSha256(value: String): Boolean = ReleaseMetadataParser.isSha256(value)

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte ->
            val unsigned = byte.toInt() and 0xff
            ((unsigned + 0x100).toString(16)).substring(1)
        }
    }

    fun verify(
        context: Context,
        file: File,
        expectedSha256: String,
        expectedVersionCode: Long
    ): Result {
        if (!file.exists() || file.length() <= 0L) {
            return Result(false, "安装包不存在或为空，请重新下载。")
        }
        val expected = ReleaseMetadataParser.normalizeSha256(expectedSha256)
        if (!isSha256(expected)) {
            return Result(false, "发布信息缺少有效的 SHA-256，已拒绝安装。")
        }
        val actual = sha256(file)
        if (!actual.equals(expected, ignoreCase = true)) {
            return Result(false, "安装包校验失败：SHA-256 与发布信息不一致，已拒绝安装。请重新下载。")
        }

        val archive = try {
            ApkInspector.inspectArchive(context, file)
        } catch (error: UpdateException) {
            return Result(false, error.message ?: "无法解析安装包。")
        }

        if (archive.packageName != context.packageName) {
            return Result(
                false,
                "安装包包名（" + archive.packageName + "）与当前应用不一致，已拒绝安装。"
            )
        }
        if (archive.versionCode != expectedVersionCode) {
            return Result(
                false,
                "安装包版本号（" + archive.versionCode + "）与发布信息（" + expectedVersionCode +
                    "）不一致，已拒绝安装。"
            )
        }

        val installed = try {
            ApkInspector.installedInfo(context)
        } catch (error: UpdateException) {
            return Result(false, error.message ?: "无法读取当前版本信息，已拒绝安装。")
        }
        if (archive.versionCode <= installed.versionCode) {
            return Result(
                false,
                "安装包版本号（" + archive.versionCode + "）不高于当前版本（" +
                    installed.versionCode + "），已阻止降级安装。"
            )
        }
        if (installed.signerDigests.isEmpty()) {
            return Result(false, "无法读取当前应用的签名信息，出于安全考虑已拒绝安装。")
        }
        if (archive.signerDigests.isEmpty()) {
            return Result(false, "安装包没有可验证的签名，已拒绝安装。")
        }
        if (!installed.signerDigests.containsAll(archive.signerDigests)) {
            return Result(
                false,
                "安装包签名与当前应用不一致，可能是被第三方重新打包，已拒绝安装。"
            )
        }
        return Result(true, "校验通过")
    }
}
