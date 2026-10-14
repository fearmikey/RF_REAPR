package com.fearmikey.rf_reapr.domain.model

enum class UsernameSiteCategory(val title: String) {
    DEV("Developer & Tech"),
    SOCIAL("Social & Community"),
    GAMING("Gaming & Media")
}

data class UsernameSite(
    val name: String,
    val category: UsernameSiteCategory,
    val profileUrlTemplate: String,
    val checkUrlTemplate: String? = null,
    val errorType: ErrorType = ErrorType.STATUS_CODE
) {
    enum class ErrorType {
        STATUS_CODE
    }

    fun getProfileUrl(username: String): String = profileUrlTemplate.replace("{username}", username)
    fun getCheckUrl(username: String): String = (checkUrlTemplate ?: profileUrlTemplate).replace("{username}", username)
}

data class UsernameCheckResult(
    val site: UsernameSite,
    val username: String,
    val exists: Boolean,
    val profileUrl: String,
    val responseCode: Int? = null,
    val error: String? = null
)

object UsernameSitesDatabase {
    val sites = listOf(
        // Developer & Tech
        UsernameSite("GitHub", UsernameSiteCategory.DEV, "https://github.com/{username}"),
        UsernameSite("GitLab", UsernameSiteCategory.DEV, "https://gitlab.com/{username}"),
        UsernameSite("DockerHub", UsernameSiteCategory.DEV, "https://hub.docker.com/u/{username}/"),
        UsernameSite("HackerNews", UsernameSiteCategory.DEV, "https://news.ycombinator.com/user?id={username}"),
        UsernameSite("Keybase", UsernameSiteCategory.DEV, "https://keybase.io/{username}"),
        UsernameSite("PyPI", UsernameSiteCategory.DEV, "https://pypi.org/user/{username}/"),
        UsernameSite("Replit", UsernameSiteCategory.DEV, "https://replit.com/@{username}"),
        UsernameSite("Dev.to", UsernameSiteCategory.DEV, "https://dev.to/{username}"),
        UsernameSite("Codepen", UsernameSiteCategory.DEV, "https://codepen.io/{username}"),
        UsernameSite("LeetCode", UsernameSiteCategory.DEV, "https://leetcode.com/{username}/"),

        // Social & Community
        UsernameSite("Reddit", UsernameSiteCategory.SOCIAL, "https://www.reddit.com/user/{username}/about.json"),
        UsernameSite("Medium", UsernameSiteCategory.SOCIAL, "https://medium.com/@{username}"),
        UsernameSite("Pinterest", UsernameSiteCategory.SOCIAL, "https://www.pinterest.com/{username}/"),
        UsernameSite("Telegram", UsernameSiteCategory.SOCIAL, "https://t.me/{username}"),
        UsernameSite("Wikipedia", UsernameSiteCategory.SOCIAL, "https://en.wikipedia.org/wiki/User:{username}"),
        UsernameSite("Mastodon.social", UsernameSiteCategory.SOCIAL, "https://mastodon.social/@{username}"),
        UsernameSite("Disqus", UsernameSiteCategory.SOCIAL, "https://disqus.com/by/{username}/"),
        UsernameSite("Substack", UsernameSiteCategory.SOCIAL, "https://{username}.substack.com"),

        // Gaming & Media
        UsernameSite("Steam", UsernameSiteCategory.GAMING, "https://steamcommunity.com/id/{username}"),
        UsernameSite("Twitch", UsernameSiteCategory.GAMING, "https://m.twitch.tv/{username}"),
        UsernameSite("SoundCloud", UsernameSiteCategory.GAMING, "https://soundcloud.com/{username}"),
        UsernameSite("Vimeo", UsernameSiteCategory.GAMING, "https://vimeo.com/{username}"),
        UsernameSite("Chess.com", UsernameSiteCategory.GAMING, "https://api.chess.com/pub/player/{username}")
    )
}
