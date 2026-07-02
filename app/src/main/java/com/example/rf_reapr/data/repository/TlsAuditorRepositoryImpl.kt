package com.example.rf_reapr.data.repository

import com.example.rf_reapr.domain.model.RiskLevel
import com.example.rf_reapr.domain.model.TlsAuditResult
import com.example.rf_reapr.domain.model.TlsVulnerability
import com.example.rf_reapr.domain.repository.TlsAuditorRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.net.URL
import java.security.cert.X509Certificate
import java.util.*
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class TlsAuditorRepositoryImpl : TlsAuditorRepository {

    override fun auditUrl(url: String): Flow<TlsAuditorRepository.AuditStatus> = flow {
        emit(TlsAuditorRepository.AuditStatus.Loading)
        try {
            val formattedUrl = if (!url.startsWith("http")) "https://$url" else url
            if (!formattedUrl.startsWith("https://")) {
                emit(TlsAuditorRepository.AuditStatus.Error("Only HTTPS URLs can be audited for TLS."))
                return@flow
            }

            val urlObj = URL(formattedUrl)
            val host = urlObj.host
            val port = if (urlObj.port == -1) 443 else urlObj.port

            val socketFactory = SSLSocketFactory.getDefault()
            val socket = socketFactory.createSocket(host, port) as SSLSocket
            socket.soTimeout = 5000
            
            socket.startHandshake()
            
            val session = socket.session
            val protocol = session.protocol
            val cipherSuite = session.cipherSuite
            val certs = session.peerCertificates
            val primaryCert = certs.firstOrNull() as? X509Certificate
            
            val result = TlsAuditResult(
                url = formattedUrl,
                protocol = protocol,
                cipherSuite = cipherSuite,
                certificateSubject = primaryCert?.subjectX500Principal?.name,
                certificateIssuer = primaryCert?.issuerX500Principal?.name,
                expiryDate = primaryCert?.notAfter,
                vulnerabilities = analyzeTls(protocol, cipherSuite, primaryCert)
            )
            
            socket.close()
            emit(TlsAuditorRepository.AuditStatus.Success(result))

        } catch (e: Exception) {
            emit(TlsAuditorRepository.AuditStatus.Error(e.message ?: "TLS Handshake failed"))
        }
    }.flowOn(Dispatchers.IO)

    private fun analyzeTls(protocol: String?, cipherSuite: String, cert: X509Certificate?): List<TlsVulnerability> {
        val vulns = mutableListOf<TlsVulnerability>()

        // Check Cipher Suites
        val weakCiphers = listOf("RC4", "3DES", "DES", "MD5", "EXPORT", "NULL", "anon")
        if (weakCiphers.any { cipherSuite.contains(it, ignoreCase = true) }) {
            vulns.add(TlsVulnerability(
                "Weak Cipher Suite",
                "The connection uses a weak cipher ($cipherSuite) vulnerable to attacks like SWEET32 or POODLE.",
                RiskLevel.HIGH
            ))
        }

        // Check Protocol
        val outdatedProtocols = listOf("SSLv3", "TLSv1", "TLSv1.1")
        if (protocol != null && outdatedProtocols.any { protocol.equals(it, ignoreCase = true) }) {
            vulns.add(TlsVulnerability(
                "Outdated Protocol",
                "$protocol detected. This version is outdated and has known vulnerabilities (e.g. POODLE, BEAST).",
                RiskLevel.CRITICAL
            ))
        }

        if (protocol?.contains("SSL", ignoreCase = true) == true && !vulns.any { it.type == "Outdated Protocol" }) {
            vulns.add(TlsVulnerability(
                "Outdated Protocol",
                "Legacy SSL detected. Vulnerable to POODLE.",
                RiskLevel.CRITICAL
            ))
        }

        // Check Certificate Expiry
        cert?.let {
            val now = Date()
            if (now.after(it.notAfter)) {
                vulns.add(TlsVulnerability(
                    "Expired Certificate",
                    "The server certificate expired on ${it.notAfter}.",
                    RiskLevel.CRITICAL
                ))
            } else {
                // Warning if expiring soon (within 30 days)
                val thirtyDaysOut = Date(now.time + (1000L * 60 * 60 * 24 * 30))
                if (thirtyDaysOut.after(it.notAfter)) {
                    vulns.add(TlsVulnerability(
                        "Certificate Expiring Soon",
                        "The certificate will expire shortly on ${it.notAfter}.",
                        RiskLevel.MEDIUM
                    ))
                }
            }
        }

        return vulns
    }
}
