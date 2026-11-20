package com.fearmikey.rf_reapr.domain.service

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.InetAddress

data class DnsRecord(val name: String, val type: Int, val ttl: Int, val data: String) {
    val typeName: String get() = DnsRecordTypes.name(type)
}

data class DnsQueryResult(
    val query: String,
    val type: Int,
    val resolver: String,
    val status: Int = -1,
    /** AD flag: the resolver validated the answer with DNSSEC. */
    val authenticated: Boolean = false,
    val truncated: Boolean = false,
    val answers: List<DnsRecord> = emptyList(),
    val authority: List<DnsRecord> = emptyList(),
    val elapsedMs: Long = 0,
    val error: String? = null
) {
    val statusName: String get() = DnsRecordTypes.statusName(status)
    val typeName: String get() = DnsRecordTypes.name(type)
}

object DnsRecordTypes {
    const val A = 1
    const val NS = 2
    const val CNAME = 5
    const val SOA = 6
    const val PTR = 12
    const val MX = 15
    const val TXT = 16
    const val AAAA = 28
    const val SRV = 33
    const val DS = 43
    const val DNSKEY = 48
    const val HTTPS = 65
    const val CAA = 257

    private val names = mapOf(
        A to "A", NS to "NS", CNAME to "CNAME", SOA to "SOA", PTR to "PTR", MX to "MX", TXT to "TXT",
        AAAA to "AAAA", SRV to "SRV", DS to "DS", DNSKEY to "DNSKEY", HTTPS to "HTTPS", CAA to "CAA"
    )

    /** Types queried by the "All" lookup. */
    val overviewTypes = listOf(A, AAAA, CNAME, MX, NS, TXT, SOA, CAA)

    fun name(type: Int): String = names[type] ?: "TYPE$type"

    fun statusName(code: Int): String = when (code) {
        0 -> "NOERROR"
        1 -> "FORMERR"
        2 -> "SERVFAIL"
        3 -> "NXDOMAIN"
        4 -> "NOTIMP"
        5 -> "REFUSED"
        -1 -> "ERROR"
        else -> "RCODE $code"
    }
}

/** Parses DNS-over-HTTPS JSON responses (Google / Cloudflare `application/dns-json`). */
object DohResponseParser {

    fun parse(json: String, query: String, type: Int, resolver: String, elapsedMs: Long): DnsQueryResult {
        val root = try {
            JsonParser.parseString(json).asJsonObject
        } catch (_: Exception) {
            return DnsQueryResult(query, type, resolver, elapsedMs = elapsedMs, error = "Malformed response")
        }
        return DnsQueryResult(
            query = query,
            type = type,
            resolver = resolver,
            status = root.get("Status")?.takeIf { it.isJsonPrimitive }?.asInt ?: -1,
            authenticated = root.bool("AD"),
            truncated = root.bool("TC"),
            answers = records(root, "Answer"),
            authority = records(root, "Authority"),
            elapsedMs = elapsedMs,
            error = root.get("Comment")?.takeIf { it.isJsonPrimitive && root.get("Status")?.asInt != 0 }?.asString
        )
    }

    private fun records(root: JsonObject, key: String): List<DnsRecord> =
        root.get(key)?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { el ->
            val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            DnsRecord(
                name = o.get("name")?.asString?.removeSuffix(".") ?: "",
                type = o.get("type")?.asInt ?: 0,
                ttl = o.get("TTL")?.asInt ?: 0,
                data = o.get("data")?.asString?.trim() ?: ""
            )
        }.orEmpty()

    private fun JsonObject.bool(key: String) = get(key)?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
}

object DnsInput {
    /**
     * Normalizes what users paste into a query name: strips scheme, path, port, `user@`,
     * and trailing dot. Returns null if it isn't a valid hostname or IP.
     */
    fun normalize(input: String): String? = HostValidator.normalize(input)?.removeSuffix(".")?.lowercase()

    /** Builds the reverse-lookup name for an IP (in-addr.arpa / ip6.arpa), else null. */
    fun reverseName(ip: String): String? = when {
        HostValidator.isIpv4(ip) -> ip.split('.').asReversed().joinToString(".") + ".in-addr.arpa"
        HostValidator.isIpv6(ip) -> runCatching {
            // getByName on a literal parses it without any DNS lookup.
            val bytes = InetAddress.getByName(ip.substringBefore('%')).address
            bytes.joinToString("") { "%02x".format(it) }.reversed().toCharArray().joinToString(".") + ".ip6.arpa"
        }.getOrNull()
        else -> null
    }

    fun formatTtl(seconds: Int): String = when {
        seconds >= 86_400 -> "${seconds / 86_400}d ${seconds % 86_400 / 3600}h"
        seconds >= 3600 -> "${seconds / 3600}h ${seconds % 3600 / 60}m"
        seconds >= 60 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds}s"
    }
}

data class EmailSecurityReport(
    val spfRecord: String?,
    /** SPF `all` qualifier: "-all" (fail), "~all" (softfail), "?all", "+all", or null. */
    val spfAll: String?,
    val dmarcRecord: String?,
    /** DMARC policy: none / quarantine / reject. */
    val dmarcPolicy: String?,
    val findings: List<String>
)

/** Evaluates SPF and DMARC from TXT data (quoted strings are tolerated). */
object EmailSecurityAnalyzer {

    fun analyze(domainTxt: List<String>, dmarcTxt: List<String>): EmailSecurityReport {
        val clean = { s: String -> s.replace("\" \"", "").trim('"', ' ') }
        val spf = domainTxt.map(clean).firstOrNull { it.startsWith("v=spf1", ignoreCase = true) }
        val spfCount = domainTxt.map(clean).count { it.startsWith("v=spf1", ignoreCase = true) }
        val spfAll = spf?.let { Regex("""(?:^|\s)([-~?+]?)all(?:\s|$)""").find(it) }?.let { m ->
            m.groupValues[1].ifEmpty { "+" } + "all"
        }
        val dmarc = dmarcTxt.map(clean).firstOrNull { it.startsWith("v=DMARC1", ignoreCase = true) }
        val policy = dmarc?.let { Regex("""(?:^|;)\s*p\s*=\s*(\w+)""", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1)?.lowercase() }

        val findings = buildList {
            when {
                spf == null -> add("No SPF record: anyone can send mail claiming to be this domain")
                spfCount > 1 -> add("Multiple SPF records: receivers treat this as a permanent error")
                spfAll == "+all" -> add("SPF ends in +all: authorizes every server on the internet")
                spfAll == "?all" -> add("SPF ends in ?all (neutral): provides no protection")
                spfAll == "~all" -> add("SPF ends in ~all (softfail): spoofed mail is usually still delivered")
                spfAll == null -> add("SPF has no 'all' mechanism")
            }
            when {
                dmarc == null -> add("No DMARC record at _dmarc: spoofing protection isn't enforced")
                policy == "none" -> add("DMARC policy is p=none (monitor only)")
            }
        }
        return EmailSecurityReport(spf, spfAll, dmarc, policy, findings)
    }
}
