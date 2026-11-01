package com.university.timetable.update

import android.content.Context
import java.io.File

/**
 * 更新流程的仓库层：检查 → 下载 → 校验 → 调起安装。
 * 所有「能不能装」的判断都在这里集中处理，UI 只负责展示。
 */
class UpdateRepository(
    private val context: Context,
    private val api: GitHubReleaseApi = GitHubReleaseApi(),
    private val downloader: ApkDownloader = ApkDownloader()
) {

    /** 返回可更新的正式版；已经是最新或没有可用 Release 时返回 null。 */
    fun fetchLatest(): UpdateRelease? {
        val releases = api.fetchReleases()
        val ranked = ReleaseSelector.select(releases, UpdateConfig.CURRENT_VERSION_CODE)
            ?: run {
                val missing = ReleaseSelector.newestStableWithoutApk(
                    releases,
                    UpdateConfig.CURRENT_VERSION_CODE
                )
                if (missing != null) {
                    throw UpdateException(
                        "最新版本 v" + missing.versionName +
                            " 的 Release 还没有上传 APK 安装包，请稍后重试或前往 GitHub 发布页手动下载。",
                        UpdateException.Kind.NO_APK
                    )
                }
                return null
            }
        val release = ranked.release
        val apkAsset = ReleaseSelector.apkAsset(release)
            ?: throw UpdateException(
                "该版本没有附带 APK 安装包，请前往 GitHub 发布页查看。",
                UpdateException.Kind.NO_APK
            )

        val metadataAsset = ReleaseSelector.metadataAsset(release)
        val metadata = metadataAsset?.let {
            ReleaseMetadataParser.parse(api.fetchAssetText(it.downloadUrl))
        }
        if (metadata != null && metadata.versionCode != ranked.versionCode) {
            throw UpdateException(
                "发布信息中的 versionCode 与 Release Tag 不一致，已停止更新以保护数据安全。",
                UpdateException.Kind.BAD_METADATA
            )
        }

        val sha256 = metadata?.sha256
            ?: apkAsset.digest
                ?.let(ReleaseMetadataParser::normalizeSha256)
                ?.takeIf(ReleaseMetadataParser::isSha256)
            ?: throw UpdateException(
                "该版本缺少 SHA-256 校验信息，出于安全考虑暂不支持应用内更新，请前往 GitHub 发布页手动下载。",
                UpdateException.Kind.BAD_METADATA
            )

        val downloadUrl = metadata?.apkUrl ?: apkAsset.downloadUrl
        if (!downloadUrl.startsWith("https://", ignoreCase = true)) {
            throw UpdateException(
                "安装包下载地址不是 HTTPS，已停止更新。",
                UpdateException.Kind.BAD_METADATA
            )
        }

        return UpdateRelease(
            versionCode = ranked.versionCode,
            versionName = metadata?.versionName ?: ranked.versionName,
            tagName = release.tagName,
            notes = metadata?.notes ?: release.body,
            apkUrl = downloadUrl,
            apkName = metadata?.apkName ?: apkAsset.name,
            apkSize = if (apkAsset.size > 0L) apkAsset.size else 0L,
            sha256 = sha256,
            htmlUrl = release.htmlUrl
        )
    }

    suspend fun download(
        release: UpdateRelease,
        onProgress: (downloaded: Long, total: Long, bytesPerSecond: Long) -> Unit
    ): File {
        val dir = UpdateConfig.updateDir(context)
        if (!dir.exists()) dir.mkdirs()
        return downloader.download(
            url = release.apkUrl,
            target = UpdateConfig.apkFile(context),
            expectedSize = release.apkSize,
            onProgress = onProgress
        )
    }

    fun verify(release: UpdateRelease, apk: File): ApkVerifier.Result =
        ApkVerifier.verify(context, apk, release.sha256, release.versionCode)

    fun install(apk: File) = UpdateInstaller.launchInstaller(context, apk)

    /** 清理上次留下的安装包与断点文件，避免占用空间。 */
    fun cleanup() {
        runCatching {
            val apk = UpdateConfig.apkFile(context)
            if (apk.exists()) apk.delete()
            UpdateConfig.partFile(context).delete()
        }
    }
}
