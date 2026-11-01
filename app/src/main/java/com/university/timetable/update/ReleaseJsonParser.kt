package com.university.timetable.update

import org.json.JSONArray

/** GitHub Releases API 返回的 JSON 解析（只取客户端需要的字段）。 */
object ReleaseJsonParser {

    fun parseReleases(json: String): List<RemoteRelease> {
        val array = JSONArray(json)
        val result = ArrayList<RemoteRelease>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val assetsArray = item.optJSONArray("assets")
            val assets = ArrayList<ReleaseAsset>(assetsArray?.length() ?: 0)
            if (assetsArray != null) {
                for (assetIndex in 0 until assetsArray.length()) {
                    val asset = assetsArray.optJSONObject(assetIndex) ?: continue
                    val url = asset.optString("browser_download_url")
                    if (url.isBlank()) continue
                    assets += ReleaseAsset(
                        name = asset.optString("name"),
                        downloadUrl = url,
                        size = asset.optLong("size", 0L),
                        digest = asset.optString("digest")
                            .takeIf { it.isNotBlank() && it != "null" }
                    )
                }
            }
            val published = item.optString("published_at")
            result += RemoteRelease(
                tagName = item.optString("tag_name"),
                name = item.optString("name"),
                body = item.optString("body"),
                draft = item.optBoolean("draft", false),
                prerelease = item.optBoolean("prerelease", false),
                publishedAt = published.takeIf { it.isNotBlank() && it != "null" },
                htmlUrl = item.optString("html_url"),
                assets = assets
            )
        }
        return result
    }
}
