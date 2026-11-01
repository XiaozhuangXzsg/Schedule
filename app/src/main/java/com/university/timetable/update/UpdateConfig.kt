package com.university.timetable.update

import android.content.Context
import android.os.Build
import com.university.timetable.BuildConfig
import java.io.File

/**
 * 应用内更新的集中配置。
 *
 * 仓库地址只有这一个来源：`gradle.properties` 中的
 * `timetable.github.owner` / `timetable.github.repo`（构建时注入 BuildConfig）。
 * 换仓库只改 gradle.properties，代码里没有第二处硬编码。
 */
object UpdateConfig {

    val OWNER: String = BuildConfig.GITHUB_OWNER
    val REPO: String = BuildConfig.GITHUB_REPO

    /** 仓库主页，设置页与「关于」弹窗统一从这里取。 */
    val REPO_URL: String get() = "https://github.com/$OWNER/$REPO"
    val RELEASES_URL: String get() = "$REPO_URL/releases"
    val API_RELEASES_URL: String
        get() = "https://api.github.com/repos/$OWNER/$REPO/releases?per_page=30"

    /** 当前安装的版本信息（来自 BuildConfig）。 */
    val CURRENT_VERSION_CODE: Long get() = BuildConfig.VERSION_CODE.toLong()
    val CURRENT_VERSION_NAME: String get() = BuildConfig.VERSION_NAME

    /**
     * 正式版标识。3.0.0 起娅娅课表改用「3.0.0 正式版」这套版本号，
     * 界面上凡是显示版本号的地方都带上它，避免和 2.x 的四位版本号混淆。
     */
    const val RELEASE_CHANNEL = "正式版"

    /** 界面上展示的完整版本号，例如「v3.0.0 正式版」。 */
    val CURRENT_VERSION_LABEL: String get() = "v$CURRENT_VERSION_NAME $RELEASE_CHANNEL"

    /** 默认每天最多自动检查一次。 */
    const val AUTO_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

    /** 用户点「稍后再说」后，同一版本至少隔 72 小时再提示。 */
    const val POSTPONE_INTERVAL_MS = 72L * 60 * 60 * 1000

    const val CONNECT_TIMEOUT_MS = 15_000
    const val READ_TIMEOUT_MS = 30_000

    /** 单个安装包最大 300MB，防止异常响应把磁盘写满。 */
    const val MAX_APK_BYTES = 300L * 1024 * 1024

    const val UPDATE_DIR = "updates"
    const val APK_FILE_NAME = "yaya-update.apk"
    const val PART_SUFFIX = ".part"

    /** Release 中上传的版本元数据文件名，由 CI 自动生成。 */
    const val METADATA_ASSET_NAME = "version.json"

    const val FILE_PROVIDER_SUFFIX = ".fileprovider"

    const val USER_AGENT = "YayaTimetable-Updater"

    /**
     * 内置更新日志：没有检查到新版本时，设置页的「更新日志」展示这段文字。
     * 一旦检查到新版本，会改用 Release 里的更新说明。
     */
    val BUILTIN_NOTES: String = """
        v3.1.0 正式版
        • 新增「液态玻璃风格」：更薄更透的玻璃面板、镜面扫光、环境光背景与柔和投影
        • 玻璃卡片改为真半透明 + 接触阴影，透出底下的色彩，不再是纯色块
        • 页面背景加入随主题色变化的环境光，深色与浅色模式各有一套配比
        • 底部胶囊、开关、子页转场统一接入系统「减少动态效果」设置
        • 可在「个性化 → 液态玻璃风格」随时开关，立即生效、不影响任何数据

        v3.0.0 正式版
        • 版本号进入 3.x：界面上的版本号统一带「正式版」标识
        • 支持在应用内检查、下载并覆盖安装新版本，无需卸载旧版
        • 更新过程不会清除课程、设置、待办、收藏与聊天记录
        • 新增「导出数据备份 / 导入数据恢复」，可保存到系统文件选择器指定的位置
        • 修正设置页中失效的 GitHub 仓库链接
        • 优化深色模式下的更新弹窗与下载进度展示
    """.trimIndent()

    fun fileProviderAuthority(context: Context): String =
        context.packageName + FILE_PROVIDER_SUFFIX

    fun updateDir(context: Context): File =
        File(context.filesDir, UPDATE_DIR).apply { if (!exists()) mkdirs() }

    fun apkFile(context: Context): File = File(updateDir(context), APK_FILE_NAME)

    fun partFile(context: Context): File = File(updateDir(context), APK_FILE_NAME + PART_SUFFIX)
}

/**
 * 分发渠道策略。
 *
 * 如果应用是从 Google Play 安装的，Play 政策不允许应用绕过商店用外部 APK 自更新，
 * 此时只提示用户去商店更新，绝不把 GitHub 的 APK 强塞给商店版本。
 */
object UpdatePolicy {

    private const val PLAY_STORE_PACKAGE = "com.android.vending"

    fun isInstalledFromPlay(context: Context): Boolean {
        val pm = context.packageManager
        val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                pm.getInstallSourceInfo(context.packageName).installingPackageName
            }.getOrNull()
        } else {
            @Suppress("DEPRECATION")
            runCatching { pm.getInstallerPackageName(context.packageName) }.getOrNull()
        }
        return installer == PLAY_STORE_PACKAGE
    }
}
