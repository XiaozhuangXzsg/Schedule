package com.university.timetable.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * 调用系统安装器做「覆盖安装」。
 *
 * 这里绝不调用卸载、绝不清除数据：只是把一个 content:// 的 APK 交给系统安装界面，
 * 由用户自己确认。安装完成后 Android 会保留原有的数据库与偏好文件。
 */
object UpdateInstaller {

    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    /** 跳到系统「安装未知应用」授权页（部分 ROM 可能跳到应用详情页）。 */
    fun openInstallPermissionSettings(context: Context) {
        val packageUri = Uri.parse("package:" + context.packageName)
        val primary = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageUri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(primary)
            return
        } catch (error: ActivityNotFoundException) {
            // 继续尝试应用详情页
        }
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(fallback)
        } catch (error: ActivityNotFoundException) {
            throw UpdateException(
                "无法打开系统授权页面，请手动到「设置 → 应用 → 特殊权限 → 安装未知应用」中允许本应用。",
                UpdateException.Kind.PERMISSION
            )
        }
    }

    fun launchInstaller(context: Context, apk: File) {
        if (!canInstall(context)) {
            throw UpdateException(
                "尚未授予「安装未知应用」权限，请授权后再继续安装。",
                UpdateException.Kind.PERMISSION
            )
        }
        if (!apk.exists() || apk.length() <= 0L) {
            throw UpdateException("安装包不存在，请重新下载。", UpdateException.Kind.INSTALL)
        }
        val uri = try {
            FileProvider.getUriForFile(
                context,
                UpdateConfig.fileProviderAuthority(context),
                apk
            )
        } catch (error: IllegalArgumentException) {
            throw UpdateException(
                "无法把安装包交给系统安装器，请重试或联系开发者。",
                UpdateException.Kind.INSTALL
            )
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (error: ActivityNotFoundException) {
            throw UpdateException(
                "没有找到可用的系统安装器，请检查是否禁用了「软件包安装程序」。",
                UpdateException.Kind.INSTALL
            )
        } catch (error: SecurityException) {
            throw UpdateException(
                "系统拒绝了安装请求，请确认已允许本应用安装未知应用。",
                UpdateException.Kind.PERMISSION
            )
        }
    }
}
