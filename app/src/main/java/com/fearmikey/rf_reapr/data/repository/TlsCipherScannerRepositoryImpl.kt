package com.fearmikey.rf_reapr.data.repository

import com.fearmikey.rf_reapr.domain.model.RiskLevel
import com.fearmikey.rf_reapr.domain.repository.TlsCipherScannerRepository
import com.fearmikey.rf_reapr.domain.repository.TlsCipherScannerRepository.CipherSuiteInfo
import com.fearmikey.rf_reapr.domain.repository.TlsCipherScannerRepository.TlsScanResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import java.net.URL
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class TlsCipherScannerRepositoryImpl : TlsCipherScannerRepository {

    override fun scanCiphers(url: String): Flow<TlsScanResult> = channelFlow {
        val cleanUrl = if (!url.startsWith("http")) "https://$url" else url
        val urlObj = try { URL(cleanUrl) } catch (e: Exception) {
            send(TlsScanResult(error = "Invalid URL: ${e.message}"))
            return@channelFlow
        }
        val host = urlObj.host
        val port = if (urlObj.port == -1) 443 else urlObj.port

        val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
        val supportedProtocols = listOf("TLSv1.3", "TLSv1.2", "TLSv1.1", "TLSv1")
        
        val foundCiphers = mutableListOf<CipherSuiteInfo>()
        var totalSteps = 0
        var currentStep = 0

        // Get all supported ciphers first to calculate progress
        val allCiphers = try {
            withContext(Dispatchers.IO) {
                (factory.createSocket() as SSLSocket).supportedCipherSuites
            }
        } catch (e: Exception) {
            send(TlsScanResult(error = "Could not initialize SSL context"))
            return@channelFlow
        }

        totalSteps = supportedProtocols.size * allCiphers.size

        coroutineScope {
            for (protocol in supportedProtocols) {
                for (cipher in allCiphers) {
                    if (!isActive) break
                    
                    try {
                        withTimeout(2000) {
                            withContext(Dispatchers.IO) {
                                (factory.createSocket(host, port) as SSLSocket).use { socket ->
                                    socket.enabledProtocols = arrayOf(protocol)
                                    socket.enabledCipherSuites = arrayOf(cipher)
                                    socket.startHandshake()
                                    
                                    val risk = analyzeCipherRisk(protocol, cipher)
                                    foundCiphers.add(CipherSuiteInfo(protocol, cipher, risk.first, risk.second))
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // Handshake failed or cipher not supported by server
                    }
                    
                    currentStep++
                    if (currentStep % 10 == 0 || currentStep == totalSteps) {
                        send(TlsScanResult(foundCiphers.toList(), currentStep.toFloat() / totalSteps))
                    }
                }
            }
        }
        
        send(TlsScanResult(foundCiphers.toList(), 1f, isFinished = true))
    }

    private fun analyzeCipherRisk(protocol: String, cipher: String): Pair<RiskLevel, String?> {
        if (protocol == "TLSv1" || protocol == "TLSv1.1") {
            return RiskLevel.CRITICAL to "Protocol is outdated and vulnerable to POODLE, BEAST, etc."
        }
        
        val weakCiphers = listOf("RC4", "3DES", "DES", "MD5", "EXPORT", "NULL", "anon")
        if (weakCiphers.any { cipher.contains(it, ignoreCase = true) }) {
            return RiskLevel.HIGH to "Cipher uses weak encryption algorithms."
        }
        
        if (cipher.contains("CBC", ignoreCase = true) && protocol == "TLSv1.2") {
            return RiskLevel.MEDIUM to "CBC ciphers in TLS 1.2 are susceptible to Lucky13 and other padding attacks."
        }
        
        return RiskLevel.LOW to null
    }
}
