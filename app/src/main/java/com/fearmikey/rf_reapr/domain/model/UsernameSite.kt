package com.fearmikey.rf_reapr.domain.model

enum class UsernameSiteCategory(val title: String) {
    DEV("Developer & Tech"),
    SOCIAL("Social & Community"),
    GAMING("Gaming & Media")
}

/** How a site reveals whether a profile exists. */
enum class UsernameDetection {
    /** 2xx means found, 404 means not found (and the final URL must still name the user). */
    STATUS_CODE,
    /** The site answers 200 either way; the profile exists only if a marker is in the body. */
    BODY_PRESENT,
    /** The site answers 200 either way; the profile exists unless a marker is in the body. */
    BODY_ABSENT
}

data class UsernameSite(
    val name: String,
    val category: UsernameSiteCategory,
    /** URL a person would open. */
    val profileUrlTemplate: String,
    /** URL the check requests, when an API gives a cleaner answer than the profile page. */
    val checkUrlTemplate: String? = null,
    val detection: UsernameDetection = UsernameDetection.STATUS_CODE,
    val markers: List<String> = emptyList(),
    /** Usernames the site can't have are skipped instead of producing a misleading miss. */
    val usernamePattern: String? = null
) {
    fun getProfileUrl(username: String): String = profileUrlTemplate.replace("{username}", username)
    fun getCheckUrl(username: String): String = (checkUrlTemplate ?: profileUrlTemplate).replace("{username}", username)
}

enum class UsernameStatus(val label: String) {
    FOUND("Profile found"),
    NOT_FOUND("Not found"),
    /** The site blocked or rate-limited the automated check; a person has to look. */
    UNCERTAIN("Check manually"),
    /** The username can't exist on this site (length / characters). */
    INVALID("Not a valid name here"),
    ERROR("Request failed")
}

data class UsernameCheckResult(
    val site: UsernameSite,
    val username: String,
    val status: UsernameStatus,
    val profileUrl: String,
    val responseCode: Int? = null,
    val note: String? = null
) {
    val exists: Boolean get() = status == UsernameStatus.FOUND
}

/**
 * Sites checked by the Username OSINT tool. Every detection rule here was checked against a
 * known account and a nonexistent one; sites that answer 200 for everything or always block
 * automated requests are deliberately left out, because a wrong "found" is worse than no answer.
 */
object UsernameSitesDatabase {
    private const val GITHUB_NAME = "^[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?$"

    val sites = listOf(
        // Developer & Tech
        UsernameSite("GitHub", UsernameSiteCategory.DEV, "https://github.com/{username}", usernamePattern = GITHUB_NAME),
        UsernameSite("GitLab", UsernameSiteCategory.DEV, "https://gitlab.com/{username}"),
        UsernameSite(
            "Docker Hub", UsernameSiteCategory.DEV, "https://hub.docker.com/u/{username}",
            checkUrlTemplate = "https://hub.docker.com/v2/users/{username}/"
        ),
        UsernameSite(
            "Hacker News", UsernameSiteCategory.DEV, "https://news.ycombinator.com/user?id={username}",
            checkUrlTemplate = "https://hacker-news.firebaseio.com/v0/user/{username}.json",
            detection = UsernameDetection.BODY_PRESENT, markers = listOf("\"id\"")
        ),
        UsernameSite(
            "Keybase", UsernameSiteCategory.DEV, "https://keybase.io/{username}",
            checkUrlTemplate = "https://keybase.io/_/api/1.0/user/lookup.json?usernames={username}",
            detection = UsernameDetection.BODY_PRESENT, markers = listOf("\"basics\"")
        ),
        UsernameSite(
            "npm (maintainers)", UsernameSiteCategory.DEV, "https://www.npmjs.com/~{username}",
            checkUrlTemplate = "https://registry.npmjs.org/-/v1/search?text=maintainer:{username}&size=1",
            detection = UsernameDetection.BODY_ABSENT, markers = listOf("\"total\":0")
        ),
        UsernameSite(
            "Dev.to", UsernameSiteCategory.DEV, "https://dev.to/{username}",
            checkUrlTemplate = "https://dev.to/api/users/by_username?url={username}"
        ),
        UsernameSite(
            "Hugging Face", UsernameSiteCategory.DEV, "https://huggingface.co/{username}",
            checkUrlTemplate = "https://huggingface.co/api/users/{username}/overview"
        ),
        UsernameSite("Replit", UsernameSiteCategory.DEV, "https://replit.com/@{username}"),
        UsernameSite("Launchpad", UsernameSiteCategory.DEV, "https://launchpad.net/~{username}"),

        // Social & Community
        UsernameSite(
            "Reddit", UsernameSiteCategory.SOCIAL, "https://www.reddit.com/user/{username}",
            checkUrlTemplate = "https://www.reddit.com/user/{username}/about.json",
            usernamePattern = "^[A-Za-z0-9_-]{3,20}$"
        ),
        UsernameSite("Medium", UsernameSiteCategory.SOCIAL, "https://medium.com/@{username}"),
        UsernameSite(
            "Telegram", UsernameSiteCategory.SOCIAL, "https://t.me/{username}",
            detection = UsernameDetection.BODY_PRESENT, markers = listOf("tgme_page_title"),
            usernamePattern = "^[A-Za-z][A-Za-z0-9_]{3,31}$"
        ),
        UsernameSite(
            "Wikipedia", UsernameSiteCategory.SOCIAL, "https://en.wikipedia.org/wiki/User:{username}",
            checkUrlTemplate = "https://en.wikipedia.org/w/api.php?action=query&list=users&ususers={username}&format=json",
            detection = UsernameDetection.BODY_PRESENT, markers = listOf("\"userid\"")
        ),
        UsernameSite(
            "Mastodon.social", UsernameSiteCategory.SOCIAL, "https://mastodon.social/@{username}",
            checkUrlTemplate = "https://mastodon.social/api/v1/accounts/lookup?acct={username}",
            usernamePattern = "^[A-Za-z0-9_]{1,30}$"
        ),
        UsernameSite(
            "Bluesky", UsernameSiteCategory.SOCIAL, "https://bsky.app/profile/{username}.bsky.social",
            checkUrlTemplate = "https://public.api.bsky.app/xrpc/app.bsky.actor.getProfile?actor={username}.bsky.social",
            usernamePattern = "^[A-Za-z0-9](?:[A-Za-z0-9-]{1,16}[A-Za-z0-9])$"
        ),
        UsernameSite("Disqus", UsernameSiteCategory.SOCIAL, "https://disqus.com/by/{username}/"),
        UsernameSite(
            "Gravatar", UsernameSiteCategory.SOCIAL, "https://gravatar.com/{username}",
            checkUrlTemplate = "https://en.gravatar.com/{username}.json"
        ),
        UsernameSite("About.me", UsernameSiteCategory.SOCIAL, "https://about.me/{username}"),
        UsernameSite("Linktree", UsernameSiteCategory.SOCIAL, "https://linktr.ee/{username}"),

        // Gaming & Media
        UsernameSite(
            "Steam", UsernameSiteCategory.GAMING, "https://steamcommunity.com/id/{username}",
            detection = UsernameDetection.BODY_ABSENT, markers = listOf("The specified profile could not be found")
        ),
        UsernameSite(
            "Chess.com", UsernameSiteCategory.GAMING, "https://www.chess.com/member/{username}",
            checkUrlTemplate = "https://api.chess.com/pub/player/{username}"
        ),
        UsernameSite(
            "Lichess", UsernameSiteCategory.GAMING, "https://lichess.org/@/{username}",
            checkUrlTemplate = "https://lichess.org/api/user/{username}"
        ),
        UsernameSite("SoundCloud", UsernameSiteCategory.GAMING, "https://soundcloud.com/{username}"),
        UsernameSite("Vimeo", UsernameSiteCategory.GAMING, "https://vimeo.com/{username}"),
        UsernameSite("Flickr", UsernameSiteCategory.GAMING, "https://www.flickr.com/people/{username}/")
    )
}
