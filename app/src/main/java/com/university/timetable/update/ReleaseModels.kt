package com.university.timetable.update

/** GitHub Release 中的一个资产（APK、version.json 等）。 */
data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val size: Long,
    /** GitHub 新版 API 提供的 "sha256:..." 摘要，旧仓库可能为 null。 */
    val digest: String?
)

/** 从 GitHub Releases API 解析出的原始条目（未校验、未排序）。 */
data class RemoteRelease(
    val tagName: String,
    val name: String,
    val body: String,
    val draft: Boolean,
    val prerelease: Boolean,
    val publishedAt: String?,
    val htmlUrl: String,
    val assets: List<ReleaseAsset>
)

/** 经过筛选、补全校验信息后的可更新版本。 */
data class UpdateRelease(
    val versionCode: Long,
    val versionName: String,
    val tagName: String,
    val notes: String,
    val apkUrl: String,
    val apkName: String,
    val apkSize: Long,
    val sha256: String,
    val htmlUrl: String
)

/** 更新过程中的可读异常；message 直接展示给用户，必须是友好中文。 */
class UpdateException(
    message: String,
    val kind: Kind = Kind.OTHER
) : Exception(message) {

    enum class Kind {
        NETWORK,
        RATE_LIMIT,
        NOT_FOUND,
        NO_APK,
        BAD_METADATA,
        VERIFY,
        PERMISSION,
        INSTALL,
        IO,
        UNSUPPORTED,
        /** 其他未归类的失败，message 已经是可读中文。 */
        OTHER
    }
}
