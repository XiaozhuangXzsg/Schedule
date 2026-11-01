package com.university.timetable.update

/** GitHub Releases API 的最小封装。 */
class GitHubReleaseApi(private val http: HttpText = HttpText()) {

    fun fetchReleases(): List<RemoteRelease> {
        val response = http.get(UpdateConfig.API_RELEASES_URL, "application/vnd.github+json")
        when {
            response.code == 403 || response.code == 429 -> {
                val rateLimited = response.rateRemaining == "0" ||
                    response.body.contains("rate limit", ignoreCase = true)
                if (rateLimited) {
                    throw UpdateException(
                        "GitHub 接口访问次数已达上限（限流），请稍后再试。",
                        UpdateException.Kind.RATE_LIMIT
                    )
                }
                throw UpdateException(
                    "GitHub 拒绝了本次请求（HTTP " + response.code + "），请稍后重试。",
                    UpdateException.Kind.NETWORK
                )
            }

            response.code == 404 -> throw UpdateException(
                "没有找到仓库 " + UpdateConfig.OWNER + "/" + UpdateConfig.REPO +
                    " 的 Release。请确认仓库地址正确且已发布正式版本。",
                UpdateException.Kind.NOT_FOUND
            )

            response.code !in 200..299 -> throw UpdateException(
                "GitHub 服务暂时不可用（HTTP " + response.code + "），请稍后重试。",
                UpdateException.Kind.NETWORK
            )
        }
        return try {
            ReleaseJsonParser.parseReleases(response.body)
        } catch (error: UpdateException) {
            throw error
        } catch (error: Exception) {
            throw UpdateException(
                "解析 GitHub 发布信息失败，请稍后重试。",
                UpdateException.Kind.NETWORK
            )
        }
    }

    /** 下载小体积文本资产（version.json）。 */
    fun fetchAssetText(url: String): String {
        val response = http.get(url)
        if (response.code !in 200..299) {
            throw UpdateException(
                "下载版本信息失败（HTTP " + response.code + "），请稍后重试。",
                UpdateException.Kind.NETWORK
            )
        }
        return response.body
    }
}
