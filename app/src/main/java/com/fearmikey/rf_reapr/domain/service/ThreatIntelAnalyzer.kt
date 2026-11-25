package com.fearmikey.rf_reapr.domain.service

import com.fearmikey.rf_reapr.domain.model.FindingDraft
import com.fearmikey.rf_reapr.domain.model.FindingTools
import com.fearmikey.rf_reapr.domain.model.RiskLevel
import com.fearmikey.rf_reapr.domain.model.ThreatIntelReport
import com.fearmikey.rf_reapr.domain.model.ThreatIntelResult
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.OffsetDateTime

/**
 * Decides whether an address is worth sending to a third-party reputation / recon service.
 * Private, loopback and link-local addresses mean nothing to those services, and submitting
 * them only leaks the layout of an internal network. Never performs a DNS lookup.
 */
object PublicIpGuard {

    sealed class Verdict {
        /** [ip] is the normalised literal; [version] is 4 or 6. */
        data class Public(val ip: String, val version: Int) : Verdict()
        data class NotRoutable(val ip: String, val reason: String) : Verdict()
        data object NotAnIp : Verdict()
    }

    fun check(input: String): Verdict {
        val text = input.trim().removePrefix("[").removeSuffix("]")
        if (text.isEmpty()) return Verdict.NotAnIp

        SubnetCalculator.parseIpv4(text)?.let { return checkIpv4(it) }

        // Parsed by hand: InetAddress would fall back to a DNS query for anything it can't parse.
        val words = parseIpv6(text) ?: return Verdict.NotAnIp
        val ip = text.lowercase()
        if (words.take(5).all { it == 0 } && words[5] == 0xFFFF) {
            // IPv4-mapped (::ffff:a.b.c.d): judge the embedded IPv4 address.
            return checkIpv4((words[6].toLong() shl 16) or words[7].toLong())
        }
        val reason = when {
            words.all { it == 0 } -> "Unspecified address"
            words.take(7).all { it == 0 } && words[7] == 1 -> "Loopback"
            words[0] and 0xFFC0 == 0xFE80 -> "Link-local (fe80::/10)"
            words[0] and 0xFFC0 == 0xFEC0 -> "Site-local (deprecated)"
            words[0] and 0xFE00 == 0xFC00 -> "Unique local (fc00::/7)"
            words[0] and 0xFF00 == 0xFF00 -> "Multicast"
            words[0] == 0x2001 && words[1] == 0x0DB8 -> "Documentation (2001:db8::/32)"
            else -> null
        }
        return if (reason == null) Verdict.Public(ip, 6) else Verdict.NotRoutable(ip, reason)
    }

    private fun checkIpv4(value: Long): Verdict {
        val ip = SubnetCalculator.format(value)
        val type = SubnetCalculator.classify(value)
        return if (type == Ipv4AddressType.PUBLIC) Verdict.Public(ip, 4) else Verdict.NotRoutable(ip, type.label)
    }

    /** The eight 16-bit groups of an IPv6 literal, or null when [text] isn't one. */
    private fun parseIpv6(text: String): List<Int>? {
        if (':' !in text) return null
        val compressed = text.indexOf("::")
        if (compressed >= 0 && text.indexOf("::", compressed + 1) >= 0) return null

        fun groups(part: String): List<String> = if (part.isEmpty()) emptyList() else part.split(':')
        val head = groups(if (compressed >= 0) text.substring(0, compressed) else text)
        val tail = if (compressed >= 0) groups(text.substring(compressed + 2)) else emptyList()
        val all = head + tail

        fun words(parts: List<String>, offset: Int): List<Int>? = buildList {
            parts.forEachIndexed { index, group ->
                val isLast = offset + index == all.lastIndex
                if ('.' in group) {
                    // An embedded IPv4 address is only valid as the final 32 bits.
                    val v4 = SubnetCalculator.parseIpv4(group)?.takeIf { isLast } ?: return null
                    add((v4 ushr 16).toInt() and 0xFFFF)
                    add(v4.toInt() and 0xFFFF)
                } else {
                    if (group.length !in 1..4 || !group.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
                    add(group.toInt(16))
                }
            }
        }

        val headWords = words(head, 0) ?: return null
        val tailWords = words(tail, head.size) ?: return null
        val total = headWords.size + tailWords.size
        return when {
            compressed < 0 -> headWords.takeIf { total == 8 }
            total > 7 -> null
            else -> headWords + List(8 - total) { 0 } + tailWords
        }
    }

    /** User-facing explanation for a target that must not be sent to an online service. */
    fun refusal(verdict: Verdict.NotRoutable, service: String): String =
        "${verdict.ip} is not a public address (${verdict.reason}). $service only knows internet-routable " +
            "addresses, so nothing was sent."
}

/** Parses AbuseIPDB `/api/v2/check` responses without relying on reflection. */
object AbuseIpDbParser {

    fun parse(body: String, fallbackIp: String): ThreatIntelResult? {
        val data = runCatching { JsonParser.parseString(body).asJsonObject.getAsJsonObject("data") }.getOrNull()
            ?: return null
        val reports = data.array("reports").mapNotNull { element ->
            val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            ThreatIntelReport(
                reportedAt = obj.string("reportedAt").orEmpty(),
                comment = obj.string("comment").orEmpty(),
                categories = obj.array("categories").mapNotNull { runCatching { it.asInt }.getOrNull() },
                reporterCountryCode = obj.string("reporterCountryCode")
            )
        }
        return ThreatIntelResult(
            ipAddress = data.string("ipAddress") ?: fallbackIp,
            isPublic = data.boolean("isPublic") ?: true,
            ipVersion = data.int("ipVersion") ?: 4,
            isWhitelisted = data.boolean("isWhitelisted") ?: false,
            abuseConfidenceScore = (data.int("abuseConfidenceScore") ?: 0).coerceIn(0, 100),
            countryCode = data.string("countryCode"),
            countryName = data.string("countryName"),
            usageType = data.string("usageType"),
            isp = data.string("isp"),
            domain = data.string("domain"),
            hostnames = data.array("hostnames").mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString },
            totalReports = data.int("totalReports") ?: 0,
            numDistinctUsers = data.int("numDistinctUsers") ?: 0,
            lastReportedAt = data.string("lastReportedAt"),
            reports = reports,
            isTor = data.boolean("isTor") ?: false
        )
    }

    /** AbuseIPDB reports problems as `{"errors":[{"detail":"…","status":422}]}`. */
    fun parseError(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return runCatching {
            JsonParser.parseString(body).asJsonObject.array("errors")
                .firstOrNull()?.asJsonObject?.string("detail")
        }.getOrNull()
    }

    private fun JsonObject.field(name: String): JsonElement? = get(name)?.takeUnless { it.isJsonNull }
    private fun JsonObject.string(name: String): String? =
        field(name)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
    private fun JsonObject.int(name: String): Int? = runCatching { field(name)?.asInt }.getOrNull()
    private fun JsonObject.boolean(name: String): Boolean? = runCatching { field(name)?.asBoolean }.getOrNull()
    private fun JsonObject.array(name: String): List<JsonElement> =
        field(name)?.takeIf { it.isJsonArray }?.asJsonArray?.toList().orEmpty()
}

enum class ThreatLevel(val label: String) {
    WHITELISTED("Whitelisted"),
    CLEAN("No abuse reported"),
    LOW("Low confidence"),
    SUSPICIOUS("Suspicious"),
    MALICIOUS("Malicious")
}

data class ThreatAssessment(
    val level: ThreatLevel,
    val headline: String,
    val notes: List<String>,
    /** Report categories, most frequent first. */
    val topCategories: List<Pair<String, Int>>,
    val reportsLast7Days: Int,
    val reportsLast30Days: Int
)

/** Turns an AbuseIPDB result into a verdict, readable categories and findings. */
object ThreatIntelAnalyzer {

    /** AbuseIPDB report category IDs (https://www.abuseipdb.com/categories). */
    private val categories = mapOf(
        1 to "DNS Compromise", 2 to "DNS Poisoning", 3 to "Fraud Orders", 4 to "DDoS Attack",
        5 to "FTP Brute-Force", 6 to "Ping of Death", 7 to "Phishing", 8 to "Fraud VoIP",
        9 to "Open Proxy", 10 to "Web Spam", 11 to "Email Spam", 12 to "Blog Spam", 13 to "VPN IP",
        14 to "Port Scan", 15 to "Hacking", 16 to "SQL Injection", 17 to "Spoofing", 18 to "Brute-Force",
        19 to "Bad Web Bot", 20 to "Exploited Host", 21 to "Web App Attack", 22 to "SSH", 23 to "IoT Targeted"
    )

    fun categoryName(id: Int): String = categories[id] ?: "Category $id"

    fun categoryNames(report: ThreatIntelReport): List<String> = report.categories.distinct().map(::categoryName)

    fun level(result: ThreatIntelResult): ThreatLevel = when {
        result.isWhitelisted -> ThreatLevel.WHITELISTED
        result.abuseConfidenceScore >= 75 -> ThreatLevel.MALICIOUS
        result.abuseConfidenceScore >= 25 -> ThreatLevel.SUSPICIOUS
        result.abuseConfidenceScore > 0 || result.totalReports > 0 -> ThreatLevel.LOW
        else -> ThreatLevel.CLEAN
    }

    fun assess(result: ThreatIntelResult, now: OffsetDateTime = OffsetDateTime.now()): ThreatAssessment {
        val level = level(result)
        val times = result.reports.mapNotNull { runCatching { OffsetDateTime.parse(it.reportedAt) }.getOrNull() }
        val last7 = times.count { it.isAfter(now.minusDays(7)) }
        val last30 = times.count { it.isAfter(now.minusDays(30)) }
        val top = result.reports.flatMap { it.categories.distinct() }
            .groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
            .map { categoryName(it.key) to it.value }

        val headline = when (level) {
            ThreatLevel.WHITELISTED -> "On AbuseIPDB's whitelist of known-good infrastructure."
            ThreatLevel.CLEAN -> "No abuse reports in the last 90 days."
            ThreatLevel.LOW -> "A few reports, but low confidence that the address is abusive."
            ThreatLevel.SUSPICIOUS -> "Reported by multiple sources; treat traffic from this address with caution."
            ThreatLevel.MALICIOUS -> "High confidence of abuse. Block or investigate connections to this address."
        }
        val notes = buildList {
            if (result.isTor) add("Tor exit node: the reports describe Tor users, not the operator of this address.")
            if (result.isWhitelisted && result.totalReports > 0) {
                add("Whitelisted despite ${result.totalReports} reports (shared infrastructure such as a CDN or search crawler).")
            }
            result.usageType?.let { usage ->
                when {
                    usage.contains("Data Center", true) || usage.contains("Hosting", true) ->
                        add("Hosting / data-centre address: rented servers are a common source of scanning.")
                    usage.contains("Mobile", true) || usage.contains("Fixed Line ISP", true) ->
                        add("Consumer ISP address: likely dynamic, so reports may concern a previous subscriber.")
                    usage.contains("Content Delivery", true) ->
                        add("CDN address shared by many sites; reports rarely concern a single customer.")
                }
            }
            if (result.totalReports > 0 && result.numDistinctUsers == 1) {
                add("All reports come from a single reporter, so the score rests on one source.")
            }
            if (result.totalReports > result.reports.size && result.reports.isNotEmpty()) {
                add("Showing the ${result.reports.size} most recent of ${result.totalReports} reports.")
            }
        }
        return ThreatAssessment(level, headline, notes, top, last7, last30)
    }

    /** Only addresses AbuseIPDB itself considers suspicious become findings. */
    fun toDrafts(result: ThreatIntelResult): List<FindingDraft> {
        val level = level(result)
        if (level != ThreatLevel.SUSPICIOUS && level != ThreatLevel.MALICIOUS) return emptyList()
        val top = assess(result).topCategories.take(5).joinToString(", ") { "${it.first} (${it.second})" }
        val detail = buildString {
            append("AbuseIPDB confidence of abuse: ${result.abuseConfidenceScore}%. ")
            append("${result.totalReports} reports from ${result.numDistinctUsers} reporters in the last 90 days.")
            result.lastReportedAt?.let { append("\nLast reported: ${it.take(10)}") }
            val owner = listOfNotNull(result.isp, result.countryName ?: result.countryCode).joinToString(", ")
            if (owner.isNotBlank()) append("\nNetwork: $owner")
            if (top.isNotBlank()) append("\nCategories: $top")
            if (result.isTor) append("\nTor exit node.")
        }
        return listOf(
            FindingDraft(
                FindingTools.THREAT_INTEL, result.ipAddress,
                if (level == ThreatLevel.MALICIOUS) RiskLevel.HIGH else RiskLevel.MEDIUM,
                "IP address reported for abuse (AbuseIPDB)",
                detail,
                "Confirm whether the address should be talking to the environment at all; block it at the " +
                    "perimeter and review logs for the reported activity."
            )
        )
    }
}
