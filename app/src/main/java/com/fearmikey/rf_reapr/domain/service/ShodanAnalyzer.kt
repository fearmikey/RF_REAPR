package com.fearmikey.rf_reapr.domain.service

import com.fearmikey.rf_reapr.domain.model.FindingDraft
import com.fearmikey.rf_reapr.domain.model.FindingTools
import com.fearmikey.rf_reapr.domain.model.InternetDbResponse
import com.fearmikey.rf_reapr.domain.model.RiskLevel
import com.fearmikey.rf_reapr.domain.model.ShodanHostReport
import com.fearmikey.rf_reapr.domain.model.ShodanMatch
import com.fearmikey.rf_reapr.domain.model.ShodanSearchResponse
import com.fearmikey.rf_reapr.domain.model.ShodanService
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Parses Shodan and InternetDB JSON with the Gson tree API. Reflection-based parsing fills
 * Kotlin non-null fields with null whenever Shodan omits a key, which then crashes far from
 * the parser; here every missing field becomes an explicit default.
 */
object ShodanParser {

    fun parseHost(body: String): ShodanHostReport? {
        val root = body.asObject() ?: return null
        val ip = root.string("ip_str") ?: return null
        val services = root.array("data").mapNotNull { it.asObjectOrNull()?.let(::parseService) }
        // Host-level "vulns" is a list of IDs; per-service "vulns" is an object keyed by ID.
        val vulns = (root.strings("vulns") + services.flatMap { it.vulns }).map { it.uppercase() }
            .distinct().sortedWith(cveOrder)
        return ShodanHostReport(
            ip = ip,
            org = root.string("org"),
            isp = root.string("isp"),
            asn = root.string("asn"),
            os = root.string("os"),
            ports = (root.ints("ports") + services.map { it.port }).distinct().sorted(),
            hostnames = root.strings("hostnames"),
            domains = root.strings("domains"),
            tags = root.strings("tags"),
            city = root.string("city"),
            country = root.string("country_name"),
            latitude = root.double("latitude"),
            longitude = root.double("longitude"),
            lastUpdate = root.string("last_update"),
            vulns = vulns,
            services = services.sortedBy { it.port }
        )
    }

    private fun parseService(obj: JsonObject): ShodanService? {
        val port = obj.int("port") ?: return null
        val vulnIds = obj.get("vulns")?.takeIf { it.isJsonObject }?.asJsonObject?.keySet()?.toList().orEmpty()
        return ShodanService(
            port = port,
            transport = obj.string("transport"),
            product = obj.string("product"),
            version = obj.string("version"),
            module = obj.get("_shodan")?.asObjectOrNull()?.string("module"),
            banner = obj.string("data")?.trim(),
            timestamp = obj.string("timestamp"),
            cpes = (obj.strings("cpe23") + obj.strings("cpe")).distinct(),
            vulns = vulnIds
        )
    }

    fun parseSearch(body: String): ShodanSearchResponse? {
        val root = body.asObject() ?: return null
        val matches = root.array("matches").mapNotNull { element ->
            val obj = element.asObjectOrNull() ?: return@mapNotNull null
            val location = obj.get("location")?.asObjectOrNull()
            ShodanMatch(
                ip = obj.string("ip_str") ?: return@mapNotNull null,
                port = obj.int("port") ?: return@mapNotNull null,
                transport = obj.string("transport"),
                org = obj.string("org"),
                product = listOfNotNull(obj.string("product"), obj.string("version")).joinToString(" ").ifBlank { null },
                banner = obj.string("data")?.trim(),
                hostnames = obj.strings("hostnames"),
                location = listOfNotNull(location?.string("city"), location?.string("country_name"))
                    .joinToString(", ").ifBlank { null },
                timestamp = obj.string("timestamp")
            )
        }
        return ShodanSearchResponse(total = root.int("total") ?: matches.size, matches = matches)
    }

    fun parseInternetDb(body: String): InternetDbResponse? {
        val root = body.asObject() ?: return null
        return InternetDbResponse(
            ip = root.string("ip") ?: return null,
            ports = root.ints("ports").sorted(),
            hostnames = root.strings("hostnames"),
            cpes = root.strings("cpes"),
            tags = root.strings("tags"),
            vulns = root.strings("vulns").map { it.uppercase() }.distinct().sortedWith(cveOrder)
        )
    }

    /** Shodan reports failures as `{"error": "…"}`; InternetDB as `{"detail": "…"}`. */
    fun parseError(body: String?): String? {
        val root = body?.asObject() ?: return null
        return root.string("error") ?: root.string("detail")
    }

    /** Newest CVEs first: by year, then by sequence number. */
    val cveOrder: Comparator<String> = compareByDescending<String> { cveParts(it).first }
        .thenByDescending { cveParts(it).second }
        .thenBy { it }

    private fun cveParts(id: String): Pair<Int, Int> {
        val parts = id.split('-')
        return (parts.getOrNull(1)?.toIntOrNull() ?: 0) to (parts.getOrNull(2)?.toIntOrNull() ?: 0)
    }

    private fun String.asObject(): JsonObject? = runCatching { JsonParser.parseString(this).asJsonObject }.getOrNull()
    private fun JsonElement.asObjectOrNull(): JsonObject? = takeIf { it.isJsonObject }?.asJsonObject
    private fun JsonObject.field(name: String): JsonElement? = get(name)?.takeUnless { it.isJsonNull }
    private fun JsonObject.string(name: String): String? =
        field(name)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
    private fun JsonObject.int(name: String): Int? = runCatching { field(name)?.asInt }.getOrNull()
    private fun JsonObject.double(name: String): Double? = runCatching { field(name)?.asDouble }.getOrNull()
    private fun JsonObject.array(name: String): List<JsonElement> =
        field(name)?.takeIf { it.isJsonArray }?.asJsonArray?.toList().orEmpty()
    private fun JsonObject.strings(name: String): List<String> =
        array(name).mapNotNull { e -> e.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() } }
    private fun JsonObject.ints(name: String): List<Int> = array(name).mapNotNull { runCatching { it.asInt }.getOrNull() }
}

/** A port that Shodan saw open from the internet and that is risky to expose there. */
data class InternetExposure(val port: Int, val service: String, val note: String, val risk: RiskLevel)

object ShodanAnalyzer {

    /** Search filters offered as chips; the trailing colon marks where the value goes. */
    val searchFilters: List<Pair<String, String>> = listOf(
        "Network" to "net:",
        "Organisation" to "org:",
        "Hostname" to "hostname:",
        "Port" to "port:",
        "Product" to "product:",
        "Country" to "country:",
        "TLS subject" to "ssl.cert.subject.cn:",
        "HTTP title" to "http.title:"
    )

    fun exposures(ports: List<Int>): List<InternetExposure> = ports.distinct().sorted().mapNotNull { port ->
        val info = PortCatalog.info(port) ?: return@mapNotNull null
        val risk = info.exposureRisk ?: return@mapNotNull null
        val note = info.exposureNote ?: return@mapNotNull null
        InternetExposure(port, info.name, note, risk)
    }

    fun serviceName(port: Int): String? = PortCatalog.info(port)?.name

    fun toDrafts(report: ShodanHostReport): List<FindingDraft> =
        drafts(report.ip, report.ports, report.vulns, "Shodan", report.lastUpdate)

    fun toDrafts(data: InternetDbResponse): List<FindingDraft> =
        drafts(data.ip, data.ports, data.vulns, "Shodan InternetDB", null)

    private fun drafts(ip: String, ports: List<Int>, vulns: List<String>, source: String, seen: String?): List<FindingDraft> =
        buildList {
            exposures(ports).forEach { e ->
                add(
                    FindingDraft(
                        FindingTools.SHODAN, ip, e.risk,
                        "${e.service} exposed to the internet (${e.port}/tcp)",
                        e.note + "\nObserved by $source" + (seen?.let { " (last update ${it.take(10)})" } ?: "") +
                            ". Shodan data can be days old; confirm with a live scan.",
                        "Close the port at the perimeter firewall or restrict it to a VPN / allowlisted sources."
                    )
                )
            }
            if (vulns.isNotEmpty()) {
                add(
                    FindingDraft(
                        FindingTools.SHODAN, ip, RiskLevel.HIGH,
                        "Known CVEs associated with internet-facing services",
                        "$source lists ${vulns.size} CVE${if (vulns.size == 1) "" else "s"}: " +
                            vulns.take(25).joinToString(", ") + (if (vulns.size > 25) ", …" else "") +
                            "\nThese are inferred from service banners and version strings, not confirmed by exploitation.",
                        "Verify the software versions on the host and patch or remove the affected services."
                    )
                )
            }
        }

    /** Plain-text summary for the clipboard. */
    fun summary(report: ShodanHostReport): String = buildString {
        appendLine("Shodan host report: ${report.ip}")
        listOfNotNull(report.org, report.isp?.takeIf { it != report.org }, report.asn).joinToString(" · ")
            .takeIf { it.isNotBlank() }?.let { appendLine(it) }
        listOfNotNull(report.city, report.country).joinToString(", ").takeIf { it.isNotBlank() }?.let { appendLine("Location: $it") }
        report.os?.let { appendLine("OS: $it") }
        if (report.hostnames.isNotEmpty()) appendLine("Hostnames: ${report.hostnames.joinToString(", ")}")
        if (report.tags.isNotEmpty()) appendLine("Tags: ${report.tags.joinToString(", ")}")
        report.lastUpdate?.let { appendLine("Last update: ${it.take(19)}") }
        if (report.ports.isNotEmpty()) appendLine("Open ports: ${report.ports.joinToString(", ")}")
        report.services.forEach { s ->
            appendLine("  ${s.port}/${s.transport ?: "tcp"} ${s.productLabel ?: s.module ?: serviceName(s.port) ?: ""}".trimEnd())
        }
        if (report.vulns.isNotEmpty()) appendLine("CVEs (unverified): ${report.vulns.joinToString(", ")}")
    }.trim()

    fun summary(data: InternetDbResponse): String = buildString {
        appendLine("InternetDB report: ${data.ip}")
        if (data.hostnames.isNotEmpty()) appendLine("Hostnames: ${data.hostnames.joinToString(", ")}")
        if (data.ports.isNotEmpty()) appendLine("Open ports: ${data.ports.joinToString(", ")}")
        if (data.cpes.isNotEmpty()) appendLine("Software: ${data.cpes.joinToString(", ")}")
        if (data.tags.isNotEmpty()) appendLine("Tags: ${data.tags.joinToString(", ")}")
        if (data.vulns.isNotEmpty()) appendLine("CVEs (unverified): ${data.vulns.joinToString(", ")}")
    }.trim()

    // ---- Log payloads -----------------------------------------------------------------
    // ReportRepositoryImpl reads these keys back out of the SHODAN log entries, so the key
    // names are part of a stored format and must not change.

    fun hostLog(report: ShodanHostReport): Map<String, Any?> = mapOf(
        "ip_str" to report.ip,
        "org" to report.org,
        "isp" to report.isp,
        "asn" to report.asn,
        "os" to report.os,
        "city" to report.city,
        "country_name" to report.country,
        "hostnames" to report.hostnames,
        "domains" to report.domains,
        "ports" to report.ports,
        "tags" to report.tags,
        "vulns" to report.vulns
    )

    fun searchLog(query: String, response: ShodanSearchResponse): Map<String, Any?> = mapOf(
        "query" to query,
        "total" to response.total,
        "matches" to response.matches.map {
            mapOf("ip_str" to it.ip, "port" to it.port, "hostnames" to it.hostnames, "org" to it.org)
        }
    )

    fun internetDbLog(data: InternetDbResponse): Map<String, Any?> = mapOf(
        "type" to "InternetDB",
        "ip_str" to data.ip,
        "org" to "N/A",
        "ports" to data.ports,
        "hostnames" to data.hostnames,
        "tags" to data.tags,
        "vulns" to data.vulns
    )
}
