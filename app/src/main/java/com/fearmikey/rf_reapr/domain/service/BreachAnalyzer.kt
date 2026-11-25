package com.fearmikey.rf_reapr.domain.service

import com.fearmikey.rf_reapr.domain.model.Breach
import com.fearmikey.rf_reapr.domain.model.FindingDraft
import com.fearmikey.rf_reapr.domain.model.FindingTools
import com.fearmikey.rf_reapr.domain.model.Paste
import com.fearmikey.rf_reapr.domain.model.RiskLevel
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.security.MessageDigest
import java.util.Locale

/** Parses Have I Been Pwned v3 responses with explicit defaults for missing fields. */
object HibpParser {

    fun parseBreaches(body: String): List<Breach> = body.asArray().mapNotNull { element ->
        val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
        val name = obj.string("Name") ?: return@mapNotNull null
        Breach(
            name = name,
            title = obj.string("Title") ?: name,
            domain = obj.string("Domain").orEmpty(),
            breachDate = obj.string("BreachDate").orEmpty(),
            addedDate = obj.string("AddedDate").orEmpty(),
            pwnCount = runCatching { obj.field("PwnCount")?.asLong }.getOrNull() ?: 0L,
            description = obj.string("Description").orEmpty(),
            logoPath = obj.string("LogoPath").orEmpty(),
            dataClasses = obj.field("DataClasses")?.takeIf { it.isJsonArray }?.asJsonArray
                ?.mapNotNull { e -> e.takeIf { it.isJsonPrimitive }?.asString }.orEmpty(),
            isVerified = obj.boolean("IsVerified", true),
            isFabricated = obj.boolean("IsFabricated", false),
            isSensitive = obj.boolean("IsSensitive", false),
            isRetired = obj.boolean("IsRetired", false),
            isSpamList = obj.boolean("IsSpamList", false),
            isMalware = obj.boolean("IsMalware", false),
            isStealerLog = obj.boolean("IsStealerLog", false)
        )
    }

    fun parsePastes(body: String): List<Paste> = body.asArray().mapNotNull { element ->
        val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
        Paste(
            source = obj.string("Source") ?: return@mapNotNull null,
            id = obj.string("Id").orEmpty(),
            title = obj.string("Title"),
            date = obj.string("Date"),
            emailCount = runCatching { obj.field("EmailCount")?.asInt }.getOrNull() ?: 0
        )
    }

    private fun String.asArray(): List<JsonElement> =
        runCatching { JsonParser.parseString(this).asJsonArray.toList() }.getOrDefault(emptyList())

    // HIBP answers in PascalCase; older stored logs and tests may use camelCase.
    private fun JsonObject.field(name: String): JsonElement? =
        (get(name) ?: get(name.replaceFirstChar { it.lowercase() }))?.takeUnless { it.isJsonNull }

    private fun JsonObject.string(name: String): String? =
        field(name)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }

    private fun JsonObject.boolean(name: String, default: Boolean): Boolean =
        runCatching { field(name)?.asBoolean }.getOrNull() ?: default
}

data class BreachSummary(
    val total: Int,
    val passwordExposures: Int,
    val mostRecent: String?,
    val earliest: String?,
    /** Exposed data classes, most frequent first. */
    val exposedClasses: List<Pair<String, Int>>,
    val severity: RiskLevel,
    val advice: List<String>
)

/** Rates breaches by what was exposed and turns a lookup into advice and findings. */
object BreachAnalyzer {

    private val credentialClasses = setOf(
        "passwords", "password hints", "security questions and answers", "auth tokens",
        "historical passwords", "encrypted keys", "private messages", "pins"
    )
    private val identityClasses = setOf(
        "credit cards", "partial credit card data", "bank account numbers", "social security numbers",
        "passport numbers", "government issued ids", "driver's licenses", "tax identification numbers",
        "credit card cvv", "financial transactions", "biometric data", "health records"
    )
    private val personalClasses = setOf(
        "phone numbers", "physical addresses", "dates of birth", "ip addresses", "geographic locations",
        "device information", "names"
    )

    fun isEmail(account: String): Boolean = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(account.trim())

    fun exposesCredentials(breach: Breach): Boolean = breach.dataClasses.any { it.lowercase() in credentialClasses }

    fun severity(breach: Breach): RiskLevel {
        val classes = breach.dataClasses.map { it.lowercase() }
        val base = when {
            // Stealer logs hold credentials captured in plaintext from an infected device.
            breach.isStealerLog || breach.isMalware -> RiskLevel.CRITICAL
            classes.any { it in credentialClasses || it in identityClasses } -> RiskLevel.HIGH
            classes.any { it in personalClasses } -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
        // Unverified, fabricated and spam-list entries are weak evidence of a real compromise.
        return if (breach.isFabricated || breach.isSpamList || !breach.isVerified) minOf(base, RiskLevel.LOW) else base
    }

    /** Newest first; breaches without a date sort last. */
    fun sorted(breaches: List<Breach>): List<Breach> =
        breaches.sortedWith(compareByDescending<Breach> { it.breachDate }.thenBy { it.title })

    fun summarize(breaches: List<Breach>, pastes: List<Paste>): BreachSummary {
        val dates = breaches.map { it.breachDate }.filter { it.isNotBlank() }.sorted()
        val classes = breaches.flatMap { it.dataClasses }.groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key to it.value }
        val passwordExposures = breaches.count(::exposesCredentials)
        val severity = breaches.maxOfOrNull(::severity) ?: RiskLevel.LOW
        val advice = buildList {
            if (breaches.any { it.isStealerLog || it.isMalware }) {
                add("Credentials were harvested by malware: check the devices this account was used on, then change every password stored on them.")
            }
            if (passwordExposures > 0) {
                add("Change the password on the affected services and anywhere the same password was reused.")
                add("Enable multi-factor authentication; breached passwords are replayed against other sites (credential stuffing).")
            }
            if (classes.any { it.first.lowercase() in identityClasses }) {
                add("Financial or identity data was exposed: watch statements and consider a fraud alert or credit freeze.")
            }
            if (classes.any { it.first.equals("Phone numbers", true) }) {
                add("Expect targeted phishing and SIM-swap attempts against the exposed phone number.")
            }
            if (pastes.isNotEmpty()) add("The address appears in public pastes, which usually means it circulates in combo lists.")
            if (isEmpty() && breaches.isNotEmpty()) add("Expect more convincing phishing that references the breached services.")
        }
        return BreachSummary(
            total = breaches.size,
            passwordExposures = passwordExposures,
            mostRecent = dates.lastOrNull(),
            earliest = dates.firstOrNull(),
            exposedClasses = classes,
            severity = severity,
            advice = advice
        )
    }

    /** HIBP descriptions are HTML fragments; reduce them to plain text. */
    fun cleanDescription(html: String): String = html
        .replace(Regex("<[^>]+>"), "")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ").replace("&amp;", "&")
        .replace(Regex("\\s+"), " ").trim()

    fun formatCount(count: Long): String = when {
        count >= 1_000_000_000 -> "%.1fB".format(Locale.US, count / 1_000_000_000.0)
        count >= 1_000_000 -> "%.1fM".format(Locale.US, count / 1_000_000.0)
        count >= 1_000 -> "%.1fK".format(Locale.US, count / 1_000.0)
        else -> count.toString()
    }

    fun toDrafts(account: String, breaches: List<Breach>, pastes: List<Paste>): List<FindingDraft> = buildList {
        val real = breaches.filterNot { it.isFabricated || it.isSpamList }
        if (real.isNotEmpty()) {
            val summary = summarize(real, emptyList())
            add(
                FindingDraft(
                    FindingTools.BREACH, account, summary.severity,
                    "Account found in public data breaches",
                    "${real.size} breach${if (real.size == 1) "" else "es"}" +
                        (if (summary.passwordExposures > 0) ", ${summary.passwordExposures} exposing passwords or other credentials" else "") +
                        ".\n" + sorted(real).take(25).joinToString("\n") { b ->
                            "${b.title} (${b.breachDate.ifBlank { "undated" }}): ${b.dataClasses.joinToString(", ")}"
                        },
                    "Reset the account's passwords wherever they were reused, enforce multi-factor authentication " +
                        "and brief the user on phishing that references the breached services."
                )
            )
        }
        if (pastes.isNotEmpty()) {
            add(
                FindingDraft(
                    FindingTools.BREACH, account, RiskLevel.LOW,
                    "Account found in public pastes",
                    "${pastes.size} paste${if (pastes.size == 1) "" else "s"}: " +
                        pastes.take(15).joinToString(", ") { "${it.source} ${it.title ?: it.id}".trim() },
                    "Treat the address as known to spammers and credential-stuffing lists; make sure it uses a unique password."
                )
            )
        }
    }
}

/**
 * Pwned Passwords range lookup (k-anonymity). Only the first five characters of the
 * password's SHA-1 leave the device; the match against the returned suffixes is local.
 */
object PwnedPasswords {

    const val PREFIX_LENGTH = 5

    fun sha1Hex(password: String): String =
        MessageDigest.getInstance("SHA-1").digest(password.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02X".format(it) }

    fun prefix(hash: String): String = hash.take(PREFIX_LENGTH)

    /**
     * Times the password with [hash] appears in the range response, or 0 when absent.
     * Padding rows (count 0, sent when `Add-Padding: true` is requested) never match.
     */
    fun match(hash: String, rangeBody: String): Int {
        val suffix = hash.drop(PREFIX_LENGTH)
        if (suffix.isEmpty()) return 0
        return rangeBody.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.substringBefore(':').equals(suffix, ignoreCase = true) }
            ?.substringAfter(':', "0")?.trim()?.toIntOrNull() ?: 0
    }

    fun verdict(count: Int): String = when {
        count <= 0 -> "Not found in any known breach. That does not make it a strong password, only an unlisted one."
        count < 10 -> "Seen $count time${if (count == 1) "" else "s"} in breach data. It is in attackers' wordlists; do not use it."
        count < 1000 -> "Seen $count times in breach data. Commonly tried in credential-stuffing attacks."
        else -> "Seen ${BreachAnalyzer.formatCount(count.toLong())} times in breach data. One of the first passwords any attacker tries."
    }
}
