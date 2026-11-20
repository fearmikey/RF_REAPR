package com.fearmikey.rf_reapr.domain.service

import com.fearmikey.rf_reapr.domain.model.RiskLevel

/** Well-known TCP service names, exposure notes and port-list parsing for the port scanner. */
object PortCatalog {

    data class PortInfo(
        val name: String,
        val description: String? = null,
        /** Why this port being open matters, if it's a common finding. */
        val exposureNote: String? = null,
        val exposureRisk: RiskLevel? = null
    )

    private val services: Map<Int, PortInfo> = mapOf(
        7 to PortInfo("Echo"),
        20 to PortInfo("FTP-Data"),
        21 to PortInfo("FTP", "File Transfer Protocol.", "FTP sends credentials and data in cleartext; check for anonymous login.", RiskLevel.MEDIUM),
        22 to PortInfo("SSH", "Secure Shell remote administration."),
        23 to PortInfo("Telnet", "Unencrypted remote shell.", "Telnet sends passwords in cleartext and is a top IoT botnet target.", RiskLevel.HIGH),
        25 to PortInfo("SMTP", "Mail transfer between servers.", "Check that the server isn't an open relay."),
        37 to PortInfo("Time"),
        43 to PortInfo("WHOIS"),
        53 to PortInfo("DNS", "Domain name resolution.", "An open resolver can be abused for DNS amplification."),
        69 to PortInfo("TFTP", "Trivial FTP (normally UDP).", "No authentication; often used to pull device configs.", RiskLevel.MEDIUM),
        79 to PortInfo("Finger", null, "Leaks user account information.", RiskLevel.LOW),
        80 to PortInfo("HTTP", "Unencrypted web server."),
        81 to PortInfo("HTTP-Alt"),
        88 to PortInfo("Kerberos", "Active Directory authentication."),
        102 to PortInfo("S7comm", "Siemens S7 PLC protocol.", "Industrial controller exposed; scanning can disrupt PLCs.", RiskLevel.HIGH),
        110 to PortInfo("POP3", "Mail retrieval.", "Often cleartext credentials.", RiskLevel.LOW),
        111 to PortInfo("RPCbind", "Sun RPC portmapper.", "Reveals NFS and other RPC services.", RiskLevel.MEDIUM),
        113 to PortInfo("Ident"),
        119 to PortInfo("NNTP"),
        123 to PortInfo("NTP"),
        135 to PortInfo("MS-RPC", "Windows RPC endpoint mapper.", "Should not be reachable outside a trusted LAN.", RiskLevel.MEDIUM),
        137 to PortInfo("NetBIOS-NS"),
        139 to PortInfo("NetBIOS-SSN", "Legacy Windows file sharing.", "Legacy SMB; check SMBv1 is disabled.", RiskLevel.MEDIUM),
        143 to PortInfo("IMAP", "Mail retrieval.", "Often cleartext credentials.", RiskLevel.LOW),
        161 to PortInfo("SNMP", "Device management (normally UDP).", "Default 'public' community strings leak device config.", RiskLevel.MEDIUM),
        179 to PortInfo("BGP"),
        389 to PortInfo("LDAP", "Directory service.", "Anonymous binds can expose the whole directory.", RiskLevel.MEDIUM),
        427 to PortInfo("SLP", null, "SLP has been abused for large DDoS amplification.", RiskLevel.MEDIUM),
        443 to PortInfo("HTTPS", "TLS-encrypted web server."),
        445 to PortInfo("SMB", "Windows file sharing.", "SMB is a common ransomware/worm entry point (EternalBlue).", RiskLevel.HIGH),
        465 to PortInfo("SMTPS"),
        502 to PortInfo("Modbus", "Industrial control protocol.", "Modbus has no authentication; any client can write registers.", RiskLevel.CRITICAL),
        514 to PortInfo("RSH/Syslog", null, "rsh trusts the source address; trivially abused.", RiskLevel.HIGH),
        515 to PortInfo("LPD", "Line printer daemon."),
        548 to PortInfo("AFP", "Apple file sharing."),
        554 to PortInfo("RTSP", "Camera/media streaming.", "IP cameras often allow unauthenticated stream access.", RiskLevel.MEDIUM),
        587 to PortInfo("SMTP-Submit"),
        593 to PortInfo("RPC-over-HTTP"),
        623 to PortInfo("IPMI", "Server baseboard management.", "IPMI leaks password hashes (cipher zero / RAKP).", RiskLevel.HIGH),
        631 to PortInfo("IPP", "Internet Printing Protocol / CUPS."),
        636 to PortInfo("LDAPS"),
        873 to PortInfo("rsync", null, "Unauthenticated rsync modules can expose files.", RiskLevel.MEDIUM),
        902 to PortInfo("VMware-Auth"),
        993 to PortInfo("IMAPS"),
        995 to PortInfo("POP3S"),
        1080 to PortInfo("SOCKS", "Proxy.", "An open proxy lets others route traffic through this network.", RiskLevel.MEDIUM),
        1194 to PortInfo("OpenVPN"),
        1433 to PortInfo("MSSQL", "Microsoft SQL Server.", "Database directly reachable on the network.", RiskLevel.HIGH),
        1434 to PortInfo("MSSQL-Browser"),
        1521 to PortInfo("Oracle DB", "Oracle TNS listener.", "Database directly reachable on the network.", RiskLevel.HIGH),
        1723 to PortInfo("PPTP", "Legacy VPN.", "PPTP encryption (MS-CHAPv2) is broken.", RiskLevel.MEDIUM),
        1883 to PortInfo("MQTT", "IoT message broker.", "Brokers without auth expose and accept IoT commands.", RiskLevel.HIGH),
        1900 to PortInfo("UPnP/SSDP"),
        2049 to PortInfo("NFS", "Network file system.", "Check exports aren't world-readable.", RiskLevel.MEDIUM),
        2082 to PortInfo("cPanel"),
        2083 to PortInfo("cPanel-SSL"),
        2181 to PortInfo("ZooKeeper", null, "Often deployed without authentication.", RiskLevel.MEDIUM),
        2375 to PortInfo("Docker API", "Docker daemon (unencrypted).", "Unauthenticated Docker API gives root on the host.", RiskLevel.CRITICAL),
        2376 to PortInfo("Docker API (TLS)"),
        3000 to PortInfo("HTTP-Dev", "Dev server / Grafana."),
        3128 to PortInfo("Squid Proxy"),
        3260 to PortInfo("iSCSI"),
        3306 to PortInfo("MySQL", "MySQL/MariaDB database.", "Database directly reachable on the network.", RiskLevel.HIGH),
        3389 to PortInfo("RDP", "Windows Remote Desktop.", "RDP is heavily brute-forced; restrict to VPN.", RiskLevel.HIGH),
        3478 to PortInfo("STUN/TURN"),
        3689 to PortInfo("DAAP"),
        4369 to PortInfo("Erlang EPMD"),
        5000 to PortInfo("UPnP/HTTP-Alt"),
        5001 to PortInfo("HTTPS-Alt"),
        5060 to PortInfo("SIP", "VoIP signalling.", "Exposed SIP is targeted for toll fraud.", RiskLevel.MEDIUM),
        5061 to PortInfo("SIP-TLS"),
        5222 to PortInfo("XMPP"),
        5353 to PortInfo("mDNS"),
        5432 to PortInfo("PostgreSQL", "PostgreSQL database.", "Database directly reachable on the network.", RiskLevel.HIGH),
        5555 to PortInfo("ADB", "Android Debug Bridge over network.", "Network ADB gives full shell access to the device.", RiskLevel.CRITICAL),
        5601 to PortInfo("Kibana"),
        5672 to PortInfo("AMQP"),
        5800 to PortInfo("VNC-HTTP"),
        5900 to PortInfo("VNC", "Remote desktop.", "VNC often has weak or no password and no encryption.", RiskLevel.HIGH),
        5985 to PortInfo("WinRM"),
        5986 to PortInfo("WinRM-HTTPS"),
        6000 to PortInfo("X11", null, "Open X servers allow keystroke capture.", RiskLevel.HIGH),
        6379 to PortInfo("Redis", "In-memory database.", "Redis without a password allows remote code execution.", RiskLevel.CRITICAL),
        6443 to PortInfo("Kubernetes API"),
        6667 to PortInfo("IRC"),
        7547 to PortInfo("TR-069", "ISP router management (CWMP).", "TR-069 was exploited by the Mirai variants.", RiskLevel.HIGH),
        8000 to PortInfo("HTTP-Alt"),
        8008 to PortInfo("HTTP-Alt"),
        8009 to PortInfo("AJP", "Tomcat AJP connector.", "Ghostcat (CVE-2020-1938) file read via AJP.", RiskLevel.HIGH),
        8080 to PortInfo("HTTP-Proxy", "Alternate web server / proxy."),
        8081 to PortInfo("HTTP-Alt"),
        8086 to PortInfo("InfluxDB"),
        8088 to PortInfo("HTTP-Alt"),
        8443 to PortInfo("HTTPS-Alt"),
        8843 to PortInfo("HTTPS-Alt"),
        8883 to PortInfo("MQTT-TLS"),
        8888 to PortInfo("HTTP-Alt"),
        9000 to PortInfo("HTTP-Alt"),
        9090 to PortInfo("HTTP-Alt"),
        9100 to PortInfo("JetDirect", "Raw printing.", "Anyone can send print jobs or PJL commands.", RiskLevel.LOW),
        9200 to PortInfo("Elasticsearch", "Search database.", "Elasticsearch without auth exposes all indexed data.", RiskLevel.CRITICAL),
        9443 to PortInfo("HTTPS-Alt"),
        10000 to PortInfo("Webmin"),
        11211 to PortInfo("Memcached", null, "No authentication; used for massive DDoS amplification.", RiskLevel.HIGH),
        20000 to PortInfo("DNP3", "Industrial SCADA protocol.", "SCADA protocol exposed on the network.", RiskLevel.CRITICAL),
        27017 to PortInfo("MongoDB", "Document database.", "MongoDB without auth exposes all data.", RiskLevel.CRITICAL),
        44818 to PortInfo("EtherNet/IP", "Industrial control protocol.", "Industrial controller exposed; scanning can disrupt it.", RiskLevel.HIGH),
        47808 to PortInfo("BACnet", "Building automation."),
        49152 to PortInfo("UPnP-Dynamic")
    )

    private val webPorts = setOf(80, 81, 443, 591, 3000, 5000, 5001, 7080, 8000, 8008, 8080, 8081, 8088, 8443, 8843, 8888, 9000, 9090, 9443, 10000)
    private val tlsPorts = setOf(443, 465, 636, 993, 995, 2083, 5001, 5061, 5986, 8443, 8843, 8883, 9443)

    /** ~170 ports covering the services most worth finding on a LAN. */
    val COMMON_PORTS: List<Int> = (
        services.keys + listOf(
            1, 42, 106, 115, 177, 194, 311, 464, 497, 532, 600, 625, 660, 687, 749, 985, 1085, 1099, 1220, 1522, 1525,
            1529, 1640, 1649, 1990, 1998, 2195, 2196, 2336, 3004, 3031, 3283, 4111, 4488, 5003, 5009, 5010, 5100, 5190,
            5200, 5223, 5269, 5298, 5500, 5632, 5988, 7070, 7777, 8005, 8043, 8085, 8087, 8089, 8096, 8170, 8171, 8175,
            8200, 8800, 8821, 8826, 8880, 8891, 9006, 10001, 10002, 10010, 20005
        )
    ).distinct().sorted()

    fun info(port: Int): PortInfo? = services[port]

    fun serviceName(port: Int): String = services[port]?.name ?: "Unknown"

    fun isWeb(port: Int, serviceName: String? = null, banner: String? = null): Boolean =
        port in webPorts ||
            serviceName?.contains("http", ignoreCase = true) == true ||
            banner?.let { it.startsWith("HTTP/") || it.contains("Server:", ignoreCase = true) } == true

    fun isTls(port: Int, serviceName: String? = null): Boolean =
        port in tlsPorts || serviceName?.contains("https", ignoreCase = true) == true

    /** Browser URL for a web service, or null if the port doesn't look like HTTP(S). */
    fun webUrl(host: String, port: Int, serviceName: String?, banner: String?): String? {
        if (!isWeb(port, serviceName, banner)) return null
        val scheme = if (isTls(port, serviceName)) "https" else "http"
        val hostPart = if (host.contains(':')) "[$host]" else host
        val default = (scheme == "http" && port == 80) || (scheme == "https" && port == 443)
        return if (default) "$scheme://$hostPart" else "$scheme://$hostPart:$port"
    }

    /** Ports where we should send an HTTP request to get a useful banner. */
    fun shouldProbeHttp(port: Int): Boolean = port in webPorts && port !in tlsPorts

    sealed class ParseResult {
        data class Ok(val ports: List<Int>) : ParseResult()
        data class Invalid(val message: String) : ParseResult()
    }

    /**
     * Parses a port spec like `22,80,443,8000-8100`. Ranges are inclusive; duplicates are
     * removed and the result is sorted.
     */
    fun parsePortSpec(spec: String): ParseResult {
        val tokens = spec.split(',', ' ', ';').map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return ParseResult.Invalid("Enter at least one port")
        val result = sortedSetOf<Int>()
        for (token in tokens) {
            if (token.contains('-')) {
                val parts = token.split('-')
                val start = parts.getOrNull(0)?.trim()?.toIntOrNull()
                val end = parts.getOrNull(1)?.trim()?.toIntOrNull()
                if (parts.size != 2 || start == null || end == null) return ParseResult.Invalid("Bad range \"$token\"")
                if (start !in 1..65535 || end !in 1..65535) return ParseResult.Invalid("Ports must be 1–65535")
                if (start > end) return ParseResult.Invalid("Range \"$token\" is backwards")
                for (p in start..end) result.add(p)
            } else {
                val p = token.toIntOrNull() ?: return ParseResult.Invalid("\"$token\" isn't a port number")
                if (p !in 1..65535) return ParseResult.Invalid("Ports must be 1–65535")
                result.add(p)
            }
        }
        return ParseResult.Ok(result.toList())
    }

    /** Highest exposure risk among open ports (port-based only, independent of CVEs). */
    fun exposureRisk(ports: Collection<Int>): RiskLevel? =
        ports.mapNotNull { services[it]?.exposureRisk }.maxByOrNull { it.ordinal }
}
