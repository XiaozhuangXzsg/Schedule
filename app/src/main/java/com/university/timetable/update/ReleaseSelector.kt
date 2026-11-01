package com.university.timetable.update

/**
 * 从 Release 列表中挑出「真正可用的最新稳定版」。
 *
 * 规则（与需求一一对应）：
 * - 忽略 draft（草稿）与 prerelease（预发布）；
 * - Tag 必须能解析成 x.y.z，按 versionCode 比较；
 * - 必须带有 https 的 .apk 资产；
 * - **必须带有 version.json 元数据**（见下）；
 * - 只接受 versionCode 大于当前安装版本的，杜绝降级安装。
 *
 * 为什么强制要求 version.json：
 * 1.0.0 之前的版本用的是另一套 versionCode 算法（v2.2.1 = 221），
 * 而按新算法同一个 Tag 会算成 20201。若允许从 Tag 推导版本号，
 * 这些历史 Release 会永远排在新版本前面，让用户看到「有新版本 v2.2.1」
 * 却下载到一个 versionCode 更小的旧包。因此只有同时附带 version.json
 * （由发版工作流自动生成，内含真实 versionCode）的 Release 才被当作
 * 可应用内更新的候选；历史 Release 只是被忽略，不会误报。
 */
object ReleaseSelector {

    data class Ranked(
        val release: RemoteRelease,
        val versionCode: Long,
        val versionName: String
    )

    /** 通过基础门槛（非草稿/非预发布、Tag 可解析、带版本元数据）的候选。 */
    private fun eligible(release: RemoteRelease): Ranked? {
        if (release.draft || release.prerelease) return null
        val code = VersionNumbers.parseCode(release.tagName) ?: return null
        if (metadataAsset(release) == null) return null
        return Ranked(release, code, VersionNumbers.displayName(release.tagName))
    }

    fun select(releases: List<RemoteRelease>, currentCode: Long): Ranked? =
        releases.asSequence()
            .mapNotNull(::eligible)
            .filter { it.versionCode > currentCode && apkAsset(it.release) != null }
            .maxByOrNull { it.versionCode }

    /**
     * 找出「比当前版本更新、但 Release 里没有 APK」的最新稳定版。
     * 用于给出「Release 缺少安装包」的友好提示，而不是误报「已是最新版本」。
     */
    fun newestStableWithoutApk(releases: List<RemoteRelease>, currentCode: Long): Ranked? =
        releases.asSequence()
            .mapNotNull(::eligible)
            .filter { it.versionCode > currentCode && apkAsset(it.release) == null }
            .maxByOrNull { it.versionCode }

    fun apkAsset(release: RemoteRelease): ReleaseAsset? =
        release.assets.firstOrNull { asset ->
            asset.name.endsWith(".apk", ignoreCase = true) &&
                asset.downloadUrl.startsWith("https://", ignoreCase = true)
        }

    fun metadataAsset(release: RemoteRelease): ReleaseAsset? =
        release.assets.firstOrNull { asset ->
            asset.name.equals(UpdateConfig.METADATA_ASSET_NAME, ignoreCase = true) &&
                asset.downloadUrl.startsWith("https://", ignoreCase = true)
        }
}
