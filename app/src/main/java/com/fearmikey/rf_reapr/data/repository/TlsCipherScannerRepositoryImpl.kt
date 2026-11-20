package com.fearmikey.rf_reapr.data.repository

import com.fearmikey.rf_reapr.domain.model.RiskLevel
import com.fearmikey.rf_reapr.domain.repository.TlsCipherScannerRepository
import com.fearmikey.rf_reapr.domain.repository.TlsCipherScannerRepository.CertIssue
import com.fearmikey.rf_reapr.domain.repository.TlsCipherScannerRepository.CertificateInfo
import com.fearmikey.rf_reapr.domain.repository.TlsCipherScannerRepository.CipherSuiteInfo
import com.fearmikey.rf_reapr.domain.repository.TlsCipherScannerRepository.ProtocolSupport
import com.fearmikey.rf_reapr.domain.repository.TlsCipherScannerRepository.TlsScanResult
import com.fearmikey.rf_reapr.domain.service.HostValidator
import com.fearmikey.rf_reapr.domain.service.TlsAnalyzer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Efficient TLS auditor. Instead of a handshake per (protocol × cipher) pair, it does one
 * handshake per protocol with every cipher enabled to learn protocol support and the
 * server's preferred cipher, then enumerates the remaining accepted ciphers per protocol.
 * It also inspects the leaf certificate.
 */
class TlsCipherScannerRepositoryImpl : TlsCipherScannerRepository {

    private val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
    private val platformCiphers: Array<String> = (factory.createSocket() as SSLSocket).supportedCipherSuites

    override fun scanCiphers(url: String): Flow<TlsScanResult> = flow {
        val (host, port) = parseTarget(url)
            ?: run { emit(TlsScanResult(error = "Enter a valid host or https URL")); return@flow }

        emit(TlsScanResult(host = host, port = port, phase = "Checking protocol versions…"))

        val protocolResults = mutableListOf<ProtocolSupport>()
        val ciphers = mutableListOf<CipherSuiteInfo>()
        var certificate: CertificateInfo? = null
        val probeProtocols = TlsAnalyzer.PROTOCOLS

        for ((pIndex, protocol) in probeProtocols.withIndex()) {
            val negotiated = withTimeoutOrNull(5000) {
                runInterruptible(Dispatchers.IO) { handshake(host, port, arrayOf(protocol), platformCiphers) }
            }
            val enabled = negotiated != null
            protocolResults += ProtocolSupport(protocol, enabled, TlsAnalyzer.protocolRisk(protocol), TlsAnalyzer.protocolNote(protocol).takeIf { enabled })
            if (negotiated != null) {
                // Grab the certificate once, from the first successful (newest) protocol.
                if (certificate == null) certificate = buildCertInfo(host, negotiated.chain)
                ciphers += grade(protocol, negotiated.cipher)
                // Enumerate the remaining accepted ciphers for this protocol.
                enumerateCiphers(host, port, protocol, negotiated.cipher) { ciphers += it }
            }
            emit(
                TlsScanResult(
                    host = host, port = port,
                    phase = "Testing $protocol…",
                    protocols = protocolResults.toList(),
                    supportedCiphers = ciphers.sortedWith(compareByDescending<CipherSuiteInfo> { it.riskLevel.ordinal }.thenBy { it.protocol }),
                    certificate = certificate,
                    progress = (pIndex + 1f) / probeProtocols.size
                )
            )
        }

        if (protocolResults.none { it.enabled }) {
            emit(TlsScanResult(host = host, port = port, protocols = protocolResults, isFinished = true, error = "Could not complete a TLS handshake with $host:$port."))
            return@flow
        }

        val worst = (ciphers.map { it.riskLevel } + protocolResults.filter { it.enabled }.map { it.riskLevel } +
            (certificate?.issues?.map { it.severity } ?: emptyList())).maxByOrNull { it.ordinal }
        val grade = TlsAnalyzer.grade(
            worstRisk = worst,
            hasForwardSecrecy = ciphers.any { it.forwardSecrecy },
            tls13 = protocolResults.any { it.protocol == "TLSv1.3" && it.enabled },
            certProblem = certificate?.issues?.any { it.severity >= RiskLevel.HIGH } == true
        )
        emit(
            TlsScanResult(
                host = host, port = port, phase = "Complete",
                protocols = protocolResults,
                supportedCiphers = ciphers.sortedWith(compareByDescending<CipherSuiteInfo> { it.riskLevel.ordinal }.thenBy { it.protocol }),
                certificate = certificate,
                grade = grade,
                progress = 1f,
                isFinished = true
            )
        )
    }.flowOn(Dispatchers.IO)

    private data class HandshakeResult(val cipher: String, val chain: Array<X509Certificate>)

    private fun handshake(host: String, port: Int, protocols: Array<String>, ciphers: Array<String>): HandshakeResult? {
        val socket = factory.createSocket() as SSLSocket
        return try {
            socket.connect(InetSocketAddress(host, port), 4000)
            socket.soTimeout = 4000
            socket.enabledProtocols = protocols.filter { it in socket.supportedProtocols }.toTypedArray().ifEmpty { return null }
            socket.enabledCipherSuites = ciphers.filter { it in socket.supportedCipherSuites }.toTypedArray().ifEmpty { return null }
            socket.sslParameters = (socket.sslParameters ?: SSLParameters()).apply {
                serverNames = listOf(SNIHostName(host))
            }
            socket.startHandshake()
            val chain = socket.session.peerCertificates.filterIsInstance<X509Certificate>().toTypedArray()
            HandshakeResult(socket.session.cipherSuite, chain)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } finally {
            runCatching { socket.close() }
        }
    }

    /** Removes the already-found cipher and retries; each round removes the next accepted one. */
    private fun enumerateCiphers(host: String, port: Int, protocol: String, firstCipher: String, onFound: (CipherSuiteInfo) -> Unit) {
        val remaining = platformCiphers.filter { it != firstCipher && !it.startsWith("TLS_EMPTY") }.toMutableList()
        var rounds = 0
        while (remaining.isNotEmpty() && rounds < 60) {
            rounds++
            val result = handshake(host, port, arrayOf(protocol), remaining.toTypedArray()) ?: break
            val cipher = result.cipher
            if (!remaining.remove(cipher)) break
            onFound(grade(protocol, cipher))
        }
    }

    private fun grade(protocol: String, cipher: String): CipherSuiteInfo {
        val g = TlsAnalyzer.gradeCipher(protocol, cipher)
        return CipherSuiteInfo(protocol, cipher, g.risk, g.forwardSecret, g.aead, g.note)
    }

    private fun buildCertInfo(host: String, chain: Array<X509Certificate>): CertificateInfo? {
        val leaf = chain.firstOrNull() ?: return null
        val now = System.currentTimeMillis()
        val notBefore = leaf.notBefore.time
        val notAfter = leaf.notAfter.time
        val days = (notAfter - now) / 86_400_000L
        val expired = now > notAfter
        val notYet = now < notBefore
        val selfSigned = leaf.subjectX500Principal == leaf.issuerX500Principal && chain.size == 1
        val sans = runCatching { leaf.subjectAlternativeNames?.mapNotNull { it.getOrNull(1)?.toString() } }.getOrNull().orEmpty()
        val hostMatch = matchesHost(host, leaf, sans)
        val bits = when (val pk = leaf.publicKey) {
            is RSAPublicKey -> pk.modulus.bitLength()
            is ECPublicKey -> pk.params.curve.field.fieldSize
            else -> 0
        }
        val keyAlg = leaf.publicKey.algorithm
        val issues = mutableListOf<CertIssue>()
        if (expired) issues += CertIssue(RiskLevel.CRITICAL, "Certificate expired ${-days} days ago.")
        else if (days in 0..14) issues += CertIssue(RiskLevel.HIGH, "Certificate expires in $days days.")
        else if (days in 15..30) issues += CertIssue(RiskLevel.MEDIUM, "Certificate expires in $days days.")
        if (notYet) issues += CertIssue(RiskLevel.HIGH, "Certificate is not valid yet.")
        if (!hostMatch) issues += CertIssue(RiskLevel.HIGH, "Certificate is not valid for $host (name mismatch).")
        if (selfSigned) issues += CertIssue(RiskLevel.MEDIUM, "Self-signed certificate: not trusted by browsers.")
        TlsAnalyzer.keyStrengthRisk(keyAlg, bits).let { (r, m) -> if (m != null) issues += CertIssue(r, m) }
        TlsAnalyzer.signatureRisk(leaf.sigAlgName).let { (r, m) -> if (m != null) issues += CertIssue(r, m) }

        return CertificateInfo(
            subject = leaf.subjectX500Principal.name.let { cn(it) ?: it },
            issuer = leaf.issuerX500Principal.name.let { cn(it) ?: it },
            notBefore = notBefore,
            notAfter = notAfter,
            daysUntilExpiry = days,
            expired = expired,
            notYetValid = notYet,
            selfSigned = selfSigned,
            hostnameMatches = hostMatch,
            keyAlgorithm = keyAlg,
            keyBits = bits,
            signatureAlgorithm = leaf.sigAlgName,
            serialNumber = leaf.serialNumber.toString(16),
            sha256Fingerprint = sha256(leaf.encoded),
            sans = sans,
            chainLength = chain.size,
            issues = issues.sortedByDescending { it.severity.ordinal }
        )
    }

    private fun matchesHost(host: String, leaf: X509Certificate, sans: List<String>): Boolean {
        val names = (sans + (cn(leaf.subjectX500Principal.name) ?: "")).map { it.lowercase() }.filter { it.isNotBlank() }
        val h = host.lowercase()
        return names.any { name ->
            if (name.startsWith("*.")) {
                val suffix = name.substring(1) // ".example.com"
                h.endsWith(suffix) && h.removeSuffix(suffix).none { it == '.' }
            } else name == h
        }
    }

    private fun cn(dn: String): String? = Regex("CN=([^,]+)").find(dn)?.groupValues?.get(1)?.trim()

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(":") { "%02X".format(it) }

    private fun parseTarget(url: String): Pair<String, Int>? {
        var value = url.trim()
        var port = 443
        value = value.substringAfter("://")
        value = value.substringBefore('/')
        if (value.contains(':') && !value.startsWith("[")) {
            value.substringAfterLast(':').toIntOrNull()?.let { port = it }
            value = value.substringBeforeLast(':')
        }
        val host = HostValidator.normalize(value) ?: return null
        return host to port
    }
}
