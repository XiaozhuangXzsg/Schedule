package com.university.timetable.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

/** 从 APK 文件或已安装应用中读出的关键信息。 */
data class ApkInfo(
    val packageName: String,
    val versionCode: Long,
    val versionName: String?,
    /** 签名证书的 SHA-256 摘要（小写十六进制）。 */
    val signerDigests: Set<String>
)

object ApkInspector {

    @Suppress("DEPRECATION")
    private fun signatureFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }

    fun inspectArchive(context: Context, file: File): ApkInfo {
        val info = try {
            context.packageManager.getPackageArchiveInfo(file.absolutePath, signatureFlags())
        } catch (error: Exception) {
            null
        } ?: throw UpdateException(
            "无法解析安装包，文件可能已损坏，请重新下载。",
            UpdateException.Kind.VERIFY
        )
        return info.toApkInfo()
    }

    fun installedInfo(context: Context): ApkInfo {
        val info = try {
            context.packageManager.getPackageInfo(context.packageName, signatureFlags())
        } catch (error: Exception) {
            throw UpdateException(
                "无法读取当前应用信息，请稍后重试。",
                UpdateException.Kind.VERIFY
            )
        }
        return info.toApkInfo()
    }

    private fun PackageInfo.toApkInfo(): ApkInfo = ApkInfo(
        packageName = packageName,
        versionCode = versionCodeLong(),
        versionName = versionName,
        signerDigests = signerDigests()
    )

    private fun PackageInfo.versionCodeLong(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            longVersionCode
        } else {
            @Suppress("DEPRECATION")
            versionCode.toLong()
        }

    @Suppress("DEPRECATION")
    private fun PackageInfo.signerDigests(): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            signingInfo?.apkContentsSigners ?: return emptySet()
        } else {
            signatures ?: return emptySet()
        }
        val digest = MessageDigest.getInstance("SHA-256")
        return signatures.mapNotNull { signature ->
            val hash = digest.digest(signature.toByteArray())
            hash.joinToString("") { byte -> hexByte(byte) }
        }.toSet()
    }

    private fun hexByte(value: Byte): String {
        val unsigned = value.toInt() and 0xff
        return (((unsigned + 0x100).toString(16)).substring(1))
    }
}
