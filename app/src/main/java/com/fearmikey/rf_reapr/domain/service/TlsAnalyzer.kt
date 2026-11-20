package com.fearmikey.rf_reapr.domain.service

import com.fearmikey.rf_reapr.domain.model.RiskLevel

data class CipherGrade(val risk: RiskLevel, val forwardSecret: Boolean, val aead: Boolean, val note: String?)

/** Grades TLS protocols and cipher suites. Pure Kotlin so it's unit-tested. */
object TlsAnalyzer {

    /** Ordered newest-first; used both to probe and to grade. */
    val PROTOCOLS = listOf("TLSv1.3", "TLSv1.2", "TLSv1.1", "TLSv1", "SSLv3")

    fun protocolRisk(protocol: String): RiskLevel = when (protocol) {
        "TLSv1.3", "TLSv1.2" -> RiskLevel.LOW
        "TLSv1.1", "TLSv1" -> RiskLevel.HIGH
        else -> RiskLevel.CRITICAL // SSLv3 and older
    }

    fun protocolNote(protocol: String): String? = when (protocol) {
        "TLSv1.1", "TLSv1" -> "Deprecated since 2021 (RFC 8996); vulnerable to BEAST/POODLE-style attacks. Disable it."
        "SSLv3" -> "SSLv3 is broken (POODLE). Must be disabled."
        else -> null
    }

    fun gradeCipher(protocol: String, cipher: String): CipherGrade {
        val c = cipher.uppercase()
        val forwardSecret = c.contains("ECDHE") || c.contains("DHE") || protocol == "TLSv1.3"
        val aead = c.contains("GCM") || c.contains("CHACHA20") || c.contains("CCM") || protocol == "TLSv1.3"
        return when {
            c.contains("NULL") || c.contains("ANON") || c.contains("_ADH") ->
                CipherGrade(RiskLevel.CRITICAL, forwardSecret, aead, "No encryption or no authentication.")
            c.contains("EXPORT") || c.contains("_DES_") || c.contains("DES-CBC") || c.contains("RC4") || c.contains("RC2") || c.contains("MD5") ->
                CipherGrade(RiskLevel.CRITICAL, forwardSecret, false, "Broken cipher (export-grade, DES, RC4 or MD5).")
            c.contains("3DES") || c.contains("DES_EDE") ->
                CipherGrade(RiskLevel.HIGH, forwardSecret, false, "3DES is weak (Sweet32) and slow.")
            protocol == "TLSv1.1" || protocol == "TLSv1" || protocol == "SSLv3" ->
                CipherGrade(RiskLevel.HIGH, forwardSecret, aead, "Offered over a deprecated protocol.")
            !forwardSecret ->
                CipherGrade(RiskLevel.MEDIUM, false, aead, "No forward secrecy (static RSA key exchange).")
            !aead && c.contains("CBC") ->
                CipherGrade(RiskLevel.MEDIUM, forwardSecret, false, "CBC mode is prone to padding-oracle attacks (Lucky13); prefer GCM/ChaCha20.")
            else -> CipherGrade(RiskLevel.LOW, forwardSecret, aead, null)
        }
    }

    /** Overall A–F style grade from the worst finding. */
    fun grade(worstRisk: RiskLevel?, hasForwardSecrecy: Boolean, tls13: Boolean, certProblem: Boolean): String = when {
        worstRisk == null -> "?"
        worstRisk == RiskLevel.CRITICAL || certProblem -> "F"
        worstRisk == RiskLevel.HIGH -> "C"
        worstRisk == RiskLevel.MEDIUM -> "B"
        !hasForwardSecrecy -> "B"
        tls13 -> "A+"
        else -> "A"
    }

    fun keyStrengthRisk(algorithm: String, bits: Int): Pair<RiskLevel, String?> = when {
        algorithm.contains("RSA", true) && bits < 2048 -> RiskLevel.HIGH to "RSA key is only $bits bits (2048+ recommended)."
        algorithm.contains("RSA", true) && bits < 3072 -> RiskLevel.LOW to null
        (algorithm.contains("EC", true)) && bits < 256 -> RiskLevel.MEDIUM to "EC key is only $bits bits."
        else -> RiskLevel.LOW to null
    }

    fun signatureRisk(sigAlg: String): Pair<RiskLevel, String?> {
        val s = sigAlg.uppercase()
        return when {
            s.contains("MD5") -> RiskLevel.CRITICAL to "MD5 signature is forgeable."
            s.contains("SHA1") -> RiskLevel.HIGH to "SHA-1 signature is deprecated and collision-prone."
            else -> RiskLevel.LOW to null
        }
    }
}
