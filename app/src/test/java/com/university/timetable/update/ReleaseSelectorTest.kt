package com.university.timetable.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseSelectorTest {

    private fun release(
        tag: String,
        draft: Boolean = false,
        prerelease: Boolean = false,
        assets: List<ReleaseAsset> = defaultAssets(tag)
    ) = RemoteRelease(
        tagName = tag,
        name = "娅娅课表 " + tag,
        body = "notes",
        draft = draft,
        prerelease = prerelease,
        publishedAt = null,
        htmlUrl = "https://github.com/XiaozhuangXzsg/Schedule/releases/tag/$tag",
        assets = assets
    )

    /** 正常发版产物：APK + version.json。两者齐备才允许应用内更新。 */
    private fun defaultAssets(tag: String): List<ReleaseAsset> = listOf(
        ReleaseAsset(
            name = "yaya-timetable.apk",
            downloadUrl = "https://github.com/XiaozhuangXzsg/Schedule/releases/download/$tag/app-release.apk",
            size = 20_000_000L,
            digest = null
        ),
        ReleaseAsset(
            name = "version.json",
            downloadUrl = "https://github.com/XiaozhuangXzsg/Schedule/releases/download/$tag/version.json",
            size = 200L,
            digest = null
        )
    )

    @Test
    fun select_picksHighestStableVersionAboveCurrent() {
        val releases = listOf(
            release("v1.0.0"),
            release("v1.2.0"),
            release("v1.1.0")
        )
        val ranked = ReleaseSelector.select(releases, currentCode = 10000L)
        assertEquals("1.2.0", ranked?.versionName)
        assertEquals(10200L, ranked?.versionCode)
    }

    @Test
    fun select_ignoresDraftAndPrerelease() {
        val releases = listOf(
            release("v1.5.0", prerelease = true),
            release("v1.6.0", draft = true),
            release("v1.1.0")
        )
        assertEquals("1.1.0", ReleaseSelector.select(releases, 10000L)?.versionName)
    }

    @Test
    fun select_ignoresReleaseWithoutApkAsset() {
        val noApk = release(
            "v2.0.0",
            assets = listOf(
                ReleaseAsset("version.json", "https://example.com/version.json", 100L, null)
            )
        )
        assertNull(ReleaseSelector.select(listOf(noApk), 10000L))
    }

    @Test
    fun select_ignoresNonHttpsDownloadUrl() {
        val insecure = release(
            "v2.0.0",
            assets = listOf(
                ReleaseAsset("app.apk", "http://example.com/app.apk", 100L, null),
                ReleaseAsset("version.json", "https://example.com/version.json", 100L, null)
            )
        )
        assertNull(ReleaseSelector.select(listOf(insecure), 10000L))
    }

    @Test
    fun select_ignoresLegacyReleaseWithoutMetadata() {
        // v2.2.1 是 1.0.0 之前的发布，只有 APK、没有 version.json；
        // 它的 Tag 按新算法会算成 20201，若允许入选会永远盖住真正的新版本。
        val legacy = release(
            "v2.2.1",
            assets = listOf(
                ReleaseAsset(
                    "yaya-timetable-v2.2.1-release.apk",
                    "https://github.com/XiaozhuangXzsg/Schedule/releases/download/v2.2.1/yaya-timetable-v2.2.1-release.apk",
                    60_890_000L,
                    null
                )
            )
        )
        assertNull(ReleaseSelector.select(listOf(legacy), 10000L))
        assertNull(ReleaseSelector.newestStableWithoutApk(listOf(legacy), 10000L))
    }

    @Test
    fun select_picksRealUpdateNextToLegacyRelease() {
        val legacy = release(
            "v2.2.1",
            assets = listOf(
                ReleaseAsset("a.apk", "https://example.com/a.apk", 1L, null)
            )
        )
        val ranked = ReleaseSelector.select(listOf(legacy, release("v1.0.1")), 10000L)
        assertEquals("1.0.1", ranked?.versionName)
        assertEquals(10001L, ranked?.versionCode)
    }

    @Test
    fun select_rejectsDowngradeAndSameVersion() {
        assertNull(ReleaseSelector.select(listOf(release("v1.0.0")), 10000L))
        assertNull(ReleaseSelector.select(listOf(release("v0.9.0")), 10000L))
    }

    @Test
    fun select_ignoresUnparseableTags() {
        val releases = listOf(release("nightly"), release("v1.0.1"))
        assertEquals("1.0.1", ReleaseSelector.select(releases, 10000L)?.versionName)
    }

    @Test
    fun select_returnsNullWhenNothingNewer() {
        assertNull(ReleaseSelector.select(emptyList(), 10000L))
    }

    @Test
    fun metadataAsset_findsVersionJson() {
        val item = release(
            "v1.3.0",
            assets = listOf(
                ReleaseAsset("app-release.apk", "https://example.com/app.apk", 1L, null),
                ReleaseAsset("version.json", "https://example.com/version.json", 2L, null)
            )
        )
        assertEquals("version.json", ReleaseSelector.metadataAsset(item)?.name)
    }

    @Test
    fun newestStableWithoutApk_findsNewerReleaseMissingApk() {
        val noApk = release(
            "v2.0.0",
            assets = listOf(
                ReleaseAsset("version.json", "https://example.com/version.json", 1L, null)
            )
        )
        assertEquals(
            "2.0.0",
            ReleaseSelector.newestStableWithoutApk(listOf(noApk), 10000L)?.versionName
        )
    }

    @Test
    fun newestStableWithoutApk_ignoresReleasesThatHaveApk() {
        assertNull(ReleaseSelector.newestStableWithoutApk(listOf(release("v2.0.0")), 10000L))
    }

    @Test
    fun newestStableWithoutApk_ignoresPrereleaseAndOlderVersions() {
        val prereleaseNoApk = release(
            "v3.0.0",
            prerelease = true,
            assets = listOf(
                ReleaseAsset("version.json", "https://example.com/version.json", 1L, null)
            )
        )
        val olderNoApk = release(
            "v0.9.0",
            assets = listOf(
                ReleaseAsset("version.json", "https://example.com/version.json", 1L, null)
            )
        )
        assertNull(
            ReleaseSelector.newestStableWithoutApk(listOf(prereleaseNoApk, olderNoApk), 10000L)
        )
    }
}
