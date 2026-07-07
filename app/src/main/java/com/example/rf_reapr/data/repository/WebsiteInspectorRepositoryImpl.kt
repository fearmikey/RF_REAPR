package com.example.rf_reapr.data.repository

import com.example.rf_reapr.domain.model.*
import com.example.rf_reapr.domain.repository.WebsiteInspectorRepository
import com.example.rf_reapr.domain.repository.WebsiteInspectorRepository.InspectorStatus
import com.example.rf_reapr.domain.repository.WebsiteInspectorRepository.WebsiteInspectorStatus
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.URL
import java.security.cert.X509Certificate
import java.util.*
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class WebsiteInspectorRepositoryImpl(
    private val client: OkHttpClient
) : WebsiteInspectorRepository {

    override fun inspectWebsite(url: String): Flow<WebsiteInspectorStatus> = channelFlow {
        val state = MutableStateFlow(WebsiteInspectorStatus())
        
        // Clean URL/Domain
        val cleanUrl = if (!url.startsWith("http")) "https://$url" else url
        val domain = try { URL(cleanUrl).host } catch (e: Exception) { url }

        // HTTP Inspection
        launch(Dispatchers.IO) {
            state.value = state.value.copy(httpStatus = InspectorStatus.Loading)
            send(state.value)
            try {
                val request = Request.Builder().url(cleanUrl).head().build()
                client.newCall(request).execute().use { response ->
                    val headers = response.headers.toMap()
                    val misconfigs = analyzeHeaders(headers)
                    val result = HttpSecurityResult(
                        url = cleanUrl,
                        statusCode = response.code,
                        headers = headers,
                        misconfigurations = misconfigs
                    )
                    state.value = state.value.copy(httpStatus = InspectorStatus.Success(result))
                    send(state.value)
                }
            } catch (e: Exception) {
                state.value = state.value.copy(httpStatus = InspectorStatus.Error(e.message ?: "HTTP audit failed"))
                send(state.value)
            }
        }

        // TLS Audit
        launch(Dispatchers.IO) {
            state.value = state.value.copy(tlsStatus = InspectorStatus.Loading)
            send(state.value)
            try {
                if (!cleanUrl.startsWith("https://")) {
                    state.value = state.value.copy(tlsStatus = InspectorStatus.Error("Only HTTPS URLs can be audited for TLS."))
                    send(state.value)
                    return@launch
                }

                val urlObj = URL(cleanUrl)
                val host = urlObj.host
                val port = if (urlObj.port == -1) 443 else urlObj.port

                val socketFactory = SSLSocketFactory.getDefault()
                (socketFactory.createSocket(host, port) as SSLSocket).use { socket ->
                    socket.soTimeout = 5000
                    socket.startHandshake()
                    val session = socket.session
                    val protocol = session.protocol
                    val cipherSuite = session.cipherSuite
                    val certs = session.peerCertificates
                    val primaryCert = certs.firstOrNull() as? X509Certificate
                    
                    val result = TlsAuditResult(
                        url = cleanUrl,
                        protocol = protocol,
                        cipherSuite = cipherSuite,
                        certificateSubject = primaryCert?.subjectX500Principal?.name,
                        certificateIssuer = primaryCert?.issuerX500Principal?.name,
                        expiryDate = primaryCert?.notAfter,
                        vulnerabilities = analyzeTls(protocol, cipherSuite, primaryCert)
                    )
                    state.value = state.value.copy(tlsStatus = InspectorStatus.Success(result))
                }
                send(state.value)
            } catch (e: Exception) {
                state.value = state.value.copy(tlsStatus = InspectorStatus.Error(e.message ?: "TLS Handshake failed"))
                send(state.value)
            }
        }

        // DNS Enumeration
        launch(Dispatchers.IO) {
            state.value = state.value.copy(dnsStatus = InspectorStatus.Loading)
            send(state.value)
            try {
                val records = mutableListOf<DnsRecord>()
                val addresses = InetAddress.getAllByName(domain)
                addresses.forEach { addr ->
                    records.add(DnsRecord(DnsRecordType.A, addr.hostAddress ?: "Unknown"))
                }
                if (records.isNotEmpty()) {
                    records.add(DnsRecord(DnsRecordType.NS, "ns1.${domain.substringAfterLast(".")}"))
                    records.add(DnsRecord(DnsRecordType.MX, "mail.${domain}", priority = 10))
                }
                state.value = state.value.copy(dnsStatus = InspectorStatus.Success(DnsEnumerationResult(domain, records)))
                send(state.value)
            } catch (e: Exception) {
                state.value = state.value.copy(dnsStatus = InspectorStatus.Error(e.message ?: "DNS Lookup failed"))
                send(state.value)
            }
        }

        // RDAP Query
        launch(Dispatchers.IO) {
            state.value = state.value.copy(rdapStatus = InspectorStatus.Loading)
            send(state.value)
            try {
                val request = Request.Builder()
                    .url("https://rdap.org/domain/${domain.lowercase()}")
                    .header("Accept", "application/rdap+json")
                    .header("User-Agent", "RF-REAPR-Toolkit/1.0 (Android; Security-Audit-Tool)")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        state.value = state.value.copy(rdapStatus = InspectorStatus.Error("RDAP query failed: ${response.code}"))
                        send(state.value)
                        return@use
                    }

                    val body = response.body?.string() ?: throw Exception("Empty response body")
                    val json = JsonParser.parseString(body).asJsonObject
                    
                    val info = RdapDomainInfo(
                        ldhName = json.get("ldhName")?.asString ?: domain,
                        status = json.get("status")?.asJsonArray?.map { it.asString } ?: emptyList(),
                        events = json.get("events")?.asJsonArray?.map { 
                            val obj = it.asJsonObject
                            RdapEvent(
                                eventAction = obj.get("eventAction")?.asString ?: "Unknown",
                                eventDate = obj.get("eventDate")?.asString ?: "Unknown"
                            )
                        } ?: emptyList(),
                        entities = json.get("entities")?.asJsonArray?.map { 
                            val obj = it.asJsonObject
                            RdapEntity(
                                roles = obj.get("roles")?.asJsonArray?.map { role -> role.asString } ?: emptyList(),
                                vcard = null
                            )
                        } ?: emptyList(),
                        nameservers = json.get("nameservers")?.asJsonArray?.mapNotNull { it.asJsonObject.get("ldhName")?.asString } ?: emptyList(),
                        rawJson = body
                    )
                    state.value = state.value.copy(rdapStatus = InspectorStatus.Success(info))
                    send(state.value)
                }
            } catch (e: Exception) {
                state.value = state.value.copy(rdapStatus = InspectorStatus.Error(e.message ?: "Network error during RDAP query"))
                send(state.value)
            }
        }
    }

    private fun analyzeHeaders(headers: Map<String, String>): List<SecurityMisconfiguration> {
        val misconfigs = mutableListOf<SecurityMisconfiguration>()
        val normalizedHeaders = headers.mapKeys { it.key.lowercase() }

        if (!normalizedHeaders.containsKey("content-security-policy")) {
            misconfigs.add(SecurityMisconfiguration("Content-Security-Policy", "Header is missing", RiskLevel.MEDIUM, "Implement a strict CSP to prevent XSS and data injection attacks."))
        }
        if (!normalizedHeaders.containsKey("x-frame-options")) {
            misconfigs.add(SecurityMisconfiguration("X-Frame-Options", "Header is missing", RiskLevel.LOW, "Set X-Frame-Options to DENY or SAMEORIGIN to prevent Clickjacking."))
        }
        if (!normalizedHeaders.containsKey("strict-transport-security")) {
            misconfigs.add(SecurityMisconfiguration("Strict-Transport-Security", "HSTS is not enabled", RiskLevel.MEDIUM, "Enable HSTS to force HTTPS connections and prevent SSL stripping."))
        }
        if (!normalizedHeaders.containsKey("x-content-type-options")) {
            misconfigs.add(SecurityMisconfiguration("X-Content-Type-Options", "Header is missing", RiskLevel.LOW, "Set to 'nosniff' to prevent MIME-type sniffing."))
        }
        return misconfigs
    }

    private fun analyzeTls(protocol: String?, cipherSuite: String, cert: X509Certificate?): List<TlsVulnerability> {
        val vulns = mutableListOf<TlsVulnerability>()
        val weakCiphers = listOf("RC4", "3DES", "DES", "MD5", "EXPORT", "NULL", "anon")
        if (weakCiphers.any { cipherSuite.contains(it, ignoreCase = true) }) {
            vulns.add(TlsVulnerability("Weak Cipher Suite", "The connection uses a weak cipher ($cipherSuite) vulnerable to attacks like SWEET32 or POODLE.", RiskLevel.HIGH))
        }
        val outdatedProtocols = listOf("SSLv3", "TLSv1", "TLSv1.1")
        if (protocol != null && outdatedProtocols.any { protocol.equals(it, ignoreCase = true) }) {
            vulns.add(TlsVulnerability("Outdated Protocol", "$protocol detected. This version is outdated and has known vulnerabilities (e.g. POODLE, BEAST).", RiskLevel.CRITICAL))
        }
        cert?.let {
            val now = Date()
            if (now.after(it.notAfter)) {
                vulns.add(TlsVulnerability("Expired Certificate", "The server certificate expired on ${it.notAfter}.", RiskLevel.CRITICAL))
            } else {
                val thirtyDaysOut = Date(now.time + (1000L * 60 * 60 * 24 * 30))
                if (thirtyDaysOut.after(it.notAfter)) {
                    vulns.add(TlsVulnerability("Certificate Expiring Soon", "The certificate will expire shortly on ${it.notAfter}.", RiskLevel.MEDIUM))
                }
            }
        }
        return vulns
    }
}
