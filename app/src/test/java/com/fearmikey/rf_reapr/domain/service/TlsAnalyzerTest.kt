package com.fearmikey.rf_reapr.domain.service

import com.fearmikey.rf_reapr.domain.model.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TlsAnalyzerTest {

    @Test
    fun `grades protocols`() {
        assertEquals(RiskLevel.LOW, TlsAnalyzer.protocolRisk("TLSv1.3"))
        assertEquals(RiskLevel.HIGH, TlsAnalyzer.protocolRisk("TLSv1"))
        assertEquals(RiskLevel.CRITICAL, TlsAnalyzer.protocolRisk("SSLv3"))
    }

    @Test
    fun `grades ciphers`() {
        val modern = TlsAnalyzer.gradeCipher("TLSv1.3", "TLS_AES_256_GCM_SHA384")
        assertEquals(RiskLevel.LOW, modern.risk)
        assertTrue(modern.forwardSecret && modern.aead)

        assertEquals(RiskLevel.CRITICAL, TlsAnalyzer.gradeCipher("TLSv1.2", "TLS_RSA_WITH_RC4_128_SHA").risk)
        assertEquals(RiskLevel.HIGH, TlsAnalyzer.gradeCipher("TLSv1.2", "TLS_RSA_WITH_3DES_EDE_CBC_SHA").risk)

        val noFs = TlsAnalyzer.gradeCipher("TLSv1.2", "TLS_RSA_WITH_AES_128_GCM_SHA256")
        assertEquals(RiskLevel.MEDIUM, noFs.risk)
        assertTrue(!noFs.forwardSecret)

        val cbc = TlsAnalyzer.gradeCipher("TLSv1.2", "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA")
        assertEquals(RiskLevel.MEDIUM, cbc.risk)
    }

    @Test
    fun `overall grade`() {
        assertEquals("A+", TlsAnalyzer.grade(RiskLevel.LOW, hasForwardSecrecy = true, tls13 = true, certProblem = false))
        assertEquals("F", TlsAnalyzer.grade(RiskLevel.LOW, hasForwardSecrecy = true, tls13 = true, certProblem = true))
        assertEquals("F", TlsAnalyzer.grade(RiskLevel.CRITICAL, hasForwardSecrecy = true, tls13 = true, certProblem = false))
        assertEquals("C", TlsAnalyzer.grade(RiskLevel.HIGH, hasForwardSecrecy = true, tls13 = false, certProblem = false))
    }

    @Test
    fun `key and signature risks`() {
        assertEquals(RiskLevel.HIGH, TlsAnalyzer.keyStrengthRisk("RSA", 1024).first)
        assertEquals(RiskLevel.LOW, TlsAnalyzer.keyStrengthRisk("RSA", 2048).first)
        assertEquals(RiskLevel.CRITICAL, TlsAnalyzer.signatureRisk("MD5withRSA").first)
        assertEquals(RiskLevel.HIGH, TlsAnalyzer.signatureRisk("SHA1withRSA").first)
    }
}
