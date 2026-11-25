package com.fearmikey.rf_reapr.domain.service

import com.fearmikey.rf_reapr.domain.model.UsernameCheckResult
import com.fearmikey.rf_reapr.domain.model.UsernameDetection
import com.fearmikey.rf_reapr.domain.model.UsernameSite
import com.fearmikey.rf_reapr.domain.model.UsernameStatus

/**
 * Decides whether a site's answer means "this username exists". A bare "HTTP 200 = found"
 * is wrong for most sites (soft 404s, login redirects, bot challenges), so the verdict also
 * looks at where redirects ended up and at markers in the page.
 */
object UsernameCheckEvaluator {

    data class Verdict(val status: UsernameStatus, val note: String? = null)

    private val allowed = Regex("^[A-Za-z0-9._-]{1,64}$")

    /** Signs that an anti-bot page answered instead of the site. */
    private val challengeMarkers = listOf(
        "Just a moment...", "cf-chl-", "Attention Required!", "captcha-delivery", "Verifying you are human",
        "px-captcha", "Access to this page has been denied"
    )

    /**
     * Cleans user input into a username that is safe to put in a URL path: a leading "@" and
     * pasted profile URLs are reduced to the name, and anything with other characters is rejected.
     */
    fun normalize(input: String): String? {
        val name = input.trim().trimEnd('/').substringAfterLast('/').removePrefix("@")
        return name.takeIf { allowed.matches(it) && it != "." && it != ".." }
    }

    fun isAllowedFor(site: UsernameSite, username: String): Boolean =
        site.usernamePattern?.let { Regex(it).matches(username) } ?: true

    fun evaluate(site: UsernameSite, username: String, code: Int, body: String, finalUrl: String): Verdict {
        if (code == 404 || code == 410) return Verdict(UsernameStatus.NOT_FOUND)
        if (code == 401 || code == 403 || code == 429 || code == 999 || code >= 500) {
            return Verdict(UsernameStatus.UNCERTAIN, blockedNote(code))
        }
        // APIs answer 400/422 for a name they don't know (e.g. Bluesky's getProfile).
        if (code == 400 || code == 422) return Verdict(UsernameStatus.NOT_FOUND)
        if (code !in 200..299) return Verdict(UsernameStatus.UNCERTAIN, "Unexpected HTTP $code")
        if (challengeMarkers.any { it in body }) {
            return Verdict(UsernameStatus.UNCERTAIN, "Bot challenge page instead of the profile")
        }

        return when (site.detection) {
            UsernameDetection.STATUS_CODE ->
                // A missing profile often redirects to the home page or a sign-in form, which is a 200.
                if (finalUrl.contains(username, ignoreCase = true)) Verdict(UsernameStatus.FOUND)
                else Verdict(UsernameStatus.NOT_FOUND, "Redirected away from the profile")
            UsernameDetection.BODY_PRESENT ->
                if (site.markers.any { it in body }) Verdict(UsernameStatus.FOUND) else Verdict(UsernameStatus.NOT_FOUND)
            UsernameDetection.BODY_ABSENT ->
                if (site.markers.any { it in body }) Verdict(UsernameStatus.NOT_FOUND) else Verdict(UsernameStatus.FOUND)
        }
    }

    private fun blockedNote(code: Int): String = when (code) {
        429 -> "Rate limited (HTTP 429)"
        401, 403, 999 -> "Site blocks automated checks (HTTP $code)"
        else -> "Site error (HTTP $code)"
    }

    /** Hits first, then the ones a person needs to look at, then the rest; stable by site name. */
    fun sorted(results: List<UsernameCheckResult>): List<UsernameCheckResult> =
        results.sortedWith(compareBy<UsernameCheckResult> { order(it.status) }.thenBy { it.site.name.lowercase() })

    private fun order(status: UsernameStatus): Int = when (status) {
        UsernameStatus.FOUND -> 0
        UsernameStatus.UNCERTAIN -> 1
        UsernameStatus.ERROR -> 2
        UsernameStatus.NOT_FOUND -> 3
        UsernameStatus.INVALID -> 4
    }

    fun report(username: String, results: List<UsernameCheckResult>): String = buildString {
        val found = results.filter { it.status == UsernameStatus.FOUND }
        val manual = results.filter { it.status == UsernameStatus.UNCERTAIN }
        appendLine("Username: $username")
        appendLine("Found on ${found.size} of ${results.size} sites checked")
        found.sortedBy { it.site.name.lowercase() }.forEach { appendLine("  ${it.site.name}: ${it.profileUrl}") }
        if (manual.isNotEmpty()) {
            appendLine("Could not be checked automatically:")
            manual.sortedBy { it.site.name.lowercase() }.forEach { appendLine("  ${it.site.name}: ${it.profileUrl}") }
        }
    }.trim()
}
