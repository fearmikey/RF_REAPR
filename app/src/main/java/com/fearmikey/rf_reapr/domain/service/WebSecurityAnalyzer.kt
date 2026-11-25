package com.fearmikey.rf_reapr.domain.service

import com.fearmikey.rf_reapr.domain.model.FindingDraft
import com.fearmikey.rf_reapr.domain.model.FindingTools
import com.fearmikey.rf_reapr.domain.model.HttpSecurityResult
import com.fearmikey.rf_reapr.domain.model.RiskLevel
import com.fearmikey.rf_reapr.domain.model.SecurityMisconfiguration

data class HeaderAudit(
    /** A (best) to F. */
    val grade: String,
    /** 0..100. */
    val score: Int,
    val issues: List<SecurityMisconfiguration>,
    /** Protections that are present and correctly configured, for the "what's good" list. */
    val passed: List<String>
)

/**
 * Grades a site's HTTP response headers. Pure: takes what the server sent, so it can be
 * tested without a network.
 */
object SecurityHeaderAnalyzer {

    private const val SIX_MONTHS_SECONDS = 15_552_000L
    private val versionPattern = Regex("\\d+\\.\\d+")

    /**
     * @param headers response headers (any case); for repeated headers the last value.
     * @param setCookies every Set-Cookie header value (they can't be folded into one map entry).
     */
    fun analyze(url: String, headers: Map<String, String>, setCookies: List<String> = emptyList()): HeaderAudit {
        val h = headers.mapKeys { it.key.lowercase() }
        val https = url.startsWith("https://", ignoreCase = true)
        val issues = mutableListOf<SecurityMisconfiguration>()
        val passed = mutableListOf<String>()
        fun issue(header: String, text: String, severity: RiskLevel, fix: String) {
            issues += SecurityMisconfiguration(header, text, severity, fix)
        }

        if (!https) {
            issue("Transport", "Served over unencrypted HTTP", RiskLevel.HIGH, "Serve the site over HTTPS and redirect HTTP to it.")
        }

        // Strict-Transport-Security (meaningless over plain HTTP, where browsers ignore it)
        if (https) {
            val hsts = h["strict-transport-security"]
            val maxAge = hsts?.let { Regex("max-age\\s*=\\s*\"?(\\d+)", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1)?.toLongOrNull() }
            when {
                hsts == null -> issue("Strict-Transport-Security", "HSTS is not enabled", RiskLevel.MEDIUM, "Send Strict-Transport-Security: max-age=31536000; includeSubDomains to prevent SSL stripping.")
                maxAge == null || maxAge == 0L -> issue("Strict-Transport-Security", "HSTS max-age is missing or zero", RiskLevel.MEDIUM, "Set max-age to at least 15552000 (180 days).")
                maxAge < SIX_MONTHS_SECONDS -> issue("Strict-Transport-Security", "HSTS max-age is short", RiskLevel.LOW, "Raise max-age to at least 15552000 (180 days); it is currently $maxAge seconds.")
                else -> passed += "HSTS enabled"
            }
        }

        // Content-Security-Policy
        val csp = h["content-security-policy"]
        if (csp == null) {
            issue("Content-Security-Policy", "Header is missing", RiskLevel.MEDIUM, "Implement a strict CSP to prevent XSS and data injection attacks.")
        } else {
            val directives = csp.split(';').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            val script = directives.firstOrNull { it.startsWith("script-src") } ?: directives.firstOrNull { it.startsWith("default-src") }
            when {
                script == null -> issue("Content-Security-Policy", "Policy doesn't restrict scripts", RiskLevel.LOW, "Add a default-src or script-src directive.")
                "'unsafe-inline'" in script && !script.contains("'nonce-") && !script.contains("'sha") ->
                    issue("Content-Security-Policy", "Policy allows inline scripts", RiskLevel.LOW, "Remove 'unsafe-inline' from script-src; use nonces or hashes.")
                "'unsafe-eval'" in script -> issue("Content-Security-Policy", "Policy allows eval()", RiskLevel.LOW, "Remove 'unsafe-eval' from script-src.")
                script.split(Regex("\\s+")).drop(1).any { it == "*" || it == "http:" || it == "https:" } ->
                    issue("Content-Security-Policy", "Policy allows scripts from any origin", RiskLevel.LOW, "List specific script origins instead of a wildcard or bare scheme.")
                else -> passed += "Content-Security-Policy restricts scripts"
            }
        }

        // Clickjacking: X-Frame-Options or CSP frame-ancestors
        if (h.containsKey("x-frame-options") || csp?.contains("frame-ancestors", ignoreCase = true) == true) {
            passed += "Framing restricted"
        } else {
            issue("X-Frame-Options", "Header is missing", RiskLevel.LOW, "Set X-Frame-Options to DENY or SAMEORIGIN (or CSP frame-ancestors) to prevent clickjacking.")
        }

        when (h["x-content-type-options"]?.trim()?.lowercase()) {
            "nosniff" -> passed += "MIME sniffing disabled"
            null -> issue("X-Content-Type-Options", "Header is missing", RiskLevel.LOW, "Set to 'nosniff' to prevent MIME-type sniffing.")
            else -> issue("X-Content-Type-Options", "Header has an invalid value", RiskLevel.LOW, "The only valid value is 'nosniff'.")
        }

        when (val referrer = h["referrer-policy"]?.trim()?.lowercase()) {
            null -> issue("Referrer-Policy", "Header is missing", RiskLevel.LOW, "Set Referrer-Policy: strict-origin-when-cross-origin (or stricter).")
            "unsafe-url", "no-referrer-when-downgrade" ->
                issue("Referrer-Policy", "Policy leaks full URLs to other sites", RiskLevel.LOW, "Use strict-origin-when-cross-origin or no-referrer instead of $referrer.")
            else -> passed += "Referrer-Policy set"
        }

        if (h.containsKey("permissions-policy") || h.containsKey("feature-policy")) passed += "Permissions-Policy set"
        else issue("Permissions-Policy", "Header is missing", RiskLevel.LOW, "Restrict powerful browser features (camera, microphone, geolocation) to what the site needs.")

        // Version disclosure
        listOf("server" to "Server", "x-powered-by" to "X-Powered-By", "x-aspnet-version" to "X-AspNet-Version").forEach { (key, name) ->
            val value = h[key] ?: return@forEach
            if (key != "server" || versionPattern.containsMatchIn(value)) {
                issue(name, "Header discloses software details", RiskLevel.LOW, "Remove or genericize $name (currently \"${value.take(80)}\") so attackers can't match it to known CVEs.")
            }
        }

        // CORS
        if (h["access-control-allow-origin"]?.trim() == "*" && h["access-control-allow-credentials"]?.trim().equals("true", ignoreCase = true)) {
            issue("Access-Control-Allow-Origin", "Wildcard origin with credentials allowed", RiskLevel.MEDIUM, "Never combine a wildcard origin with Access-Control-Allow-Credentials: true; echo only trusted origins.")
        }

        // Cookies
        val insecureCookies = mutableListOf<String>()
        val scriptReadable = mutableListOf<String>()
        val noSameSite = mutableListOf<String>()
        setCookies.forEach { cookie ->
            val name = cookie.substringBefore('=').trim().take(40)
            val attributes = cookie.split(';').drop(1).map { it.trim().lowercase() }
            if (https && attributes.none { it == "secure" }) insecureCookies += name
            if (attributes.none { it == "httponly" }) scriptReadable += name
            if (attributes.none { it.startsWith("samesite") }) noSameSite += name
        }
        if (insecureCookies.isNotEmpty()) {
            issue("Set-Cookie", "Cookies set without the Secure flag", RiskLevel.MEDIUM, "Add Secure to: ${insecureCookies.distinct().joinToString()} so they are never sent over HTTP.")
        }
        if (scriptReadable.isNotEmpty()) {
            issue("Set-Cookie", "Cookies set without the HttpOnly flag", RiskLevel.LOW, "Add HttpOnly to: ${scriptReadable.distinct().joinToString()} unless page scripts must read them.")
        }
        if (noSameSite.isNotEmpty()) {
            issue("Set-Cookie", "Cookies set without a SameSite attribute", RiskLevel.LOW, "Add SameSite=Lax or Strict to: ${noSameSite.distinct().joinToString()} to limit cross-site request forgery.")
        }
        if (setCookies.isNotEmpty() && insecureCookies.isEmpty() && scriptReadable.isEmpty() && noSameSite.isEmpty()) passed += "Cookies are hardened"

        var penalty = 0
        issues.forEach {
            penalty += when (it.severity) {
                RiskLevel.CRITICAL -> 50
                RiskLevel.HIGH -> 40
                RiskLevel.MEDIUM -> 18
                RiskLevel.LOW -> 6
            }
        }
        val score = (100 - penalty).coerceIn(0, 100)
        val grade = when {
            score >= 90 -> "A"
            score >= 75 -> "B"
            score >= 60 -> "C"
            score >= 40 -> "D"
            else -> "F"
        }
        return HeaderAudit(grade, score, issues, passed)
    }

    /** Findings for the shared store; titles are stable ("<header>: <issue>"). */
    fun toDrafts(result: HttpSecurityResult): List<FindingDraft> {
        val host = hostOf(result.url)
        return result.misconfigurations.map {
            FindingDraft(FindingTools.WEB, host, it.severity, "${it.header}: ${it.issue}", "Observed at ${result.url} (HTTP ${result.statusCode}).", it.recommendation)
        }
    }

    fun hostOf(url: String): String =
        url.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#').substringAfterLast('@').ifBlank { url }
}

enum class TakeoverRisk { NONE, HOSTED, DANGLING }

data class TakeoverHint(
    val risk: TakeoverRisk,
    /** Third-party platform the name points at. */
    val service: String,
    val cname: String
)

/**
 * Spots subdomains that could be taken over: a CNAME to a self-service hosting platform
 * whose target no longer exists can be claimed by anyone who registers that resource.
 * This is a hint from DNS alone; confirming it means checking the platform.
 */
object SubdomainTakeoverAnalyzer {

    private val services = listOf(
        "github.io" to "GitHub Pages",
        "herokuapp.com" to "Heroku",
        "herokudns.com" to "Heroku",
        "elasticbeanstalk.com" to "AWS Elastic Beanstalk",
        "cloudfront.net" to "Amazon CloudFront",
        "azurewebsites.net" to "Azure App Service",
        "cloudapp.net" to "Azure Cloud Services",
        "cloudapp.azure.com" to "Azure Cloud Services",
        "trafficmanager.net" to "Azure Traffic Manager",
        "blob.core.windows.net" to "Azure Blob Storage",
        "azureedge.net" to "Azure CDN",
        "azure-api.net" to "Azure API Management",
        "pantheonsite.io" to "Pantheon",
        "ghost.io" to "Ghost",
        "myshopify.com" to "Shopify",
        "surge.sh" to "Surge",
        "bitbucket.io" to "Bitbucket",
        "readthedocs.io" to "Read the Docs",
        "netlify.app" to "Netlify",
        "netlify.com" to "Netlify",
        "zendesk.com" to "Zendesk",
        "helpscoutdocs.com" to "Help Scout",
        "unbouncepages.com" to "Unbounce",
        "wpengine.com" to "WP Engine",
        "domains.tumblr.com" to "Tumblr",
        "statuspage.io" to "Statuspage",
        "uservoice.com" to "UserVoice",
        "fly.dev" to "Fly.io",
        "webflow.io" to "Webflow",
        "proxy-ssl.webflow.com" to "Webflow",
        "fastly.net" to "Fastly",
        "desk.com" to "Desk",
        "cargocollective.com" to "Cargo",
        "launchrock.com" to "LaunchRock",
        "agilecrm.com" to "Agile CRM",
        "strikinglydns.com" to "Strikingly"
    )

    /** bucket.s3.amazonaws.com, bucket.s3.eu-west-1.amazonaws.com, bucket.s3-website-us-east-1.amazonaws.com ... */
    private val s3Pattern = Regex("(^|\\.)s3([.-][a-z0-9-]+)*\\.amazonaws\\.com$")

    /** Platform a CNAME target belongs to, or null if it isn't a known self-service host. */
    fun serviceFor(cname: String): String? {
        val target = cname.trim().trimEnd('.').lowercase()
        if (s3Pattern.containsMatchIn(target)) return "Amazon S3"
        return services.firstOrNull { (suffix, _) -> target == suffix || target.endsWith(".$suffix") }?.second
    }

    /**
     * @param cname the name's CNAME target, if it has one.
     * @param resolves whether the name ultimately resolves to an address.
     */
    fun assess(cname: String?, resolves: Boolean): TakeoverHint? {
        val target = cname?.trim()?.trimEnd('.')?.takeIf { it.isNotBlank() } ?: return null
        val service = serviceFor(target) ?: return null
        return TakeoverHint(if (resolves) TakeoverRisk.HOSTED else TakeoverRisk.DANGLING, service, target)
    }

    /** Only dangling records are findings; a live third-party host is normal. */
    fun toDrafts(hints: List<Pair<String, TakeoverHint>>): List<FindingDraft> =
        hints.filter { it.second.risk == TakeoverRisk.DANGLING }.map { (hostname, hint) ->
            FindingDraft(
                FindingTools.SUBDOMAIN, hostname, RiskLevel.HIGH, "Possible subdomain takeover (dangling CNAME)",
                "$hostname is a CNAME to ${hint.cname} (${hint.service}), which does not resolve. If that ${hint.service} resource was deleted, " +
                    "anyone who re-creates it could serve content from $hostname. This is inferred from DNS only and needs confirming on the platform.",
                "Remove the DNS record, or re-claim the ${hint.service} resource it points to."
            )
        }
}
