package com.fearmikey.rf_reapr.domain.service

import com.fearmikey.rf_reapr.domain.model.FindingDraft
import com.fearmikey.rf_reapr.domain.model.FindingTools
import com.fearmikey.rf_reapr.domain.model.NetworkNode
import com.fearmikey.rf_reapr.domain.model.OpenPort
import com.fearmikey.rf_reapr.domain.model.RiskLevel
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.InputStream
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

data class NmapScript(val id: String, val output: String)

data class NmapPort(
    val port: Int,
    val protocol: String,
    val state: String,
    val service: String? = null,
    val product: String? = null,
    val version: String? = null,
    val scripts: List<NmapScript> = emptyList()
) {
    val isOpen: Boolean get() = state == "open"

    /** "OpenSSH 8.9p1" style banner, when service detection ran. */
    val banner: String? get() = listOfNotNull(product, version).joinToString(" ").ifBlank { null }
}

data class NmapHost(
    val address: String,
    val mac: String? = null,
    val vendor: String? = null,
    val hostnames: List<String> = emptyList(),
    val osGuess: String? = null,
    val isUp: Boolean = true,
    val ports: List<NmapPort> = emptyList()
)

data class NmapScan(
    val scanner: String? = null,
    val args: String? = null,
    /** Scan start, epoch millis. */
    val startedAt: Long? = null,
    val hosts: List<NmapHost> = emptyList()
)

class NmapParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Reads Nmap's `-oX` XML output. External entities and DTDs are never fetched. */
object NmapXmlParser {

    fun parse(input: InputStream): NmapScan {
        val document = try {
            val factory = DocumentBuilderFactory.newInstance()
            // Not every parser implementation supports every feature, so each is best-effort;
            // the entity resolver below is what reliably prevents any external fetch.
            listOf(
                "http://xml.org/sax/features/external-general-entities" to false,
                "http://xml.org/sax/features/external-parameter-entities" to false,
                "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false
            ).forEach { (feature, value) -> runCatching { factory.setFeature(feature, value) } }
            runCatching { factory.isXIncludeAware = false }
            runCatching { factory.isExpandEntityReferences = false }
            val builder = factory.newDocumentBuilder()
            builder.setEntityResolver { _, _ -> InputSource(StringReader("")) }
            builder.setErrorHandler(null)
            builder.parse(input)
        } catch (e: Exception) {
            throw NmapParseException("Not a readable XML file: ${e.message ?: e.javaClass.simpleName}", e)
        }
        val root = document.documentElement
        if (root == null || root.tagName != "nmaprun") {
            throw NmapParseException("This isn't Nmap XML output (expected <nmaprun>, found <${root?.tagName ?: "nothing"}>). Use nmap -oX.")
        }
        return NmapScan(
            scanner = root.getAttribute("scanner").ifBlank { null }?.let { name ->
                name + (root.getAttribute("version").ifBlank { null }?.let { " $it" } ?: "")
            },
            args = root.getAttribute("args").ifBlank { null },
            startedAt = root.getAttribute("start").toLongOrNull()?.let { it * 1000 },
            hosts = root.children("host").mapNotNull(::parseHost)
        )
    }

    private fun parseHost(host: Element): NmapHost? {
        val addresses = host.children("address")
        val ip = addresses.firstOrNull { it.getAttribute("addrtype") == "ipv4" }
            ?: addresses.firstOrNull { it.getAttribute("addrtype") == "ipv6" }
            ?: return null
        val mac = addresses.firstOrNull { it.getAttribute("addrtype") == "mac" }
        val os = host.children("os").flatMap { it.children("osmatch") }
            .maxByOrNull { it.getAttribute("accuracy").toIntOrNull() ?: 0 }
        return NmapHost(
            address = ip.getAttribute("addr"),
            mac = mac?.getAttribute("addr")?.ifBlank { null },
            vendor = mac?.getAttribute("vendor")?.ifBlank { null },
            hostnames = host.children("hostnames").flatMap { it.children("hostname") }
                .map { it.getAttribute("name") }.filter { it.isNotBlank() }.distinct(),
            osGuess = os?.getAttribute("name")?.ifBlank { null },
            isUp = host.children("status").firstOrNull()?.getAttribute("state")?.let { it == "up" } ?: true,
            ports = host.children("ports").flatMap { it.children("port") }.mapNotNull(::parsePort)
        ).takeIf { it.address.isNotBlank() }
    }

    private fun parsePort(port: Element): NmapPort? {
        val number = port.getAttribute("portid").toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        val service = port.children("service").firstOrNull()
        return NmapPort(
            port = number,
            protocol = port.getAttribute("protocol").ifBlank { "tcp" },
            state = port.children("state").firstOrNull()?.getAttribute("state").orEmpty(),
            service = service?.getAttribute("name")?.ifBlank { null },
            product = service?.getAttribute("product")?.ifBlank { null },
            version = service?.getAttribute("version")?.ifBlank { null },
            scripts = port.children("script").map { NmapScript(it.getAttribute("id"), it.getAttribute("output")) }
        )
    }

    private fun Element.children(tag: String): List<Element> {
        val nodes = childNodes
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }.filter { it.tagName == tag }
    }
}

/** Maps a parsed Nmap scan onto RF-REAPR's network map and findings. */
object NmapImporter {

    data class Summary(val hosts: Int, val openPorts: Int, val findings: Int)

    /** Hosts that were up, with their open TCP ports. */
    fun toNodes(scan: NmapScan): List<NetworkNode> = scan.hosts.filter { it.isUp }.map { host ->
        val open = openTcp(host)
        NetworkNode(
            id = host.address,
            ipAddress = host.address,
            macAddress = host.mac,
            hostname = host.hostnames.firstOrNull(),
            riskLevel = PortCatalog.exposureRisk(open.map { it.port }) ?: RiskLevel.LOW,
            openPorts = open,
            manufacturer = host.vendor
        )
    }

    fun toFindings(scan: NmapScan): List<FindingDraft> = buildList {
        scan.hosts.filter { it.isUp }.forEach { host ->
            // Same exposure rules as the built-in port scanner, attributed to the import.
            addAll(FindingAdapters.openPorts(host.address, openTcp(host)).map { it.copy(tool = FindingTools.NMAP) })
            host.ports.filter { it.isOpen }.forEach { port ->
                port.scripts.filter { it.output.contains("VULNERABLE", ignoreCase = false) }.forEach { script ->
                    add(
                        FindingDraft(
                            FindingTools.NMAP, host.address, RiskLevel.HIGH,
                            "Nmap script ${script.id} reports a vulnerability (${port.port}/${port.protocol})",
                            script.output.trim().take(1500),
                            "Review the script output and patch or reconfigure the affected service."
                        )
                    )
                }
            }
        }
    }

    fun summarize(scan: NmapScan): Summary {
        val up = scan.hosts.filter { it.isUp }
        return Summary(up.size, up.sumOf { openTcp(it).size }, toFindings(scan).size)
    }

    private fun openTcp(host: NmapHost): List<OpenPort> =
        host.ports.filter { it.isOpen && it.protocol == "tcp" }.sortedBy { it.port }.map { p ->
            OpenPort(
                port = p.port,
                serviceName = p.service ?: PortCatalog.info(p.port)?.name ?: "Unknown",
                banner = p.banner
            )
        }
}
