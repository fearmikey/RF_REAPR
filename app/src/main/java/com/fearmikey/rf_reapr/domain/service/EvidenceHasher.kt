package com.fearmikey.rf_reapr.domain.service

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/** Result of comparing an evidence file with the hash recorded when it was captured. */
enum class EvidenceIntegrity(val label: String) {
    VERIFIED("Verified"),
    MODIFIED("Modified since capture"),
    MISSING("File missing"),
    UNHASHED("No hash recorded")
}

/**
 * SHA-256 hashing for evidence chain-of-custody: a hash is stored when an item is captured
 * or imported and re-checked on demand, on export and in reports.
 */
object EvidenceHasher {

    fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Null when the file doesn't exist or can't be read. */
    fun sha256(file: File): String? =
        if (!file.isFile) null else runCatching { file.inputStream().use(::sha256) }.getOrNull()

    fun verify(file: File, expected: String?): EvidenceIntegrity {
        if (!file.isFile) return EvidenceIntegrity.MISSING
        if (expected.isNullOrBlank()) return EvidenceIntegrity.UNHASHED
        val actual = sha256(file) ?: return EvidenceIntegrity.MISSING
        return if (actual.equals(expected.trim(), ignoreCase = true)) EvidenceIntegrity.VERIFIED else EvidenceIntegrity.MODIFIED
    }

    /** "3f9a…c21b" for compact display; the full value is always what gets exported. */
    fun short(hash: String?): String = when {
        hash.isNullOrBlank() -> "—"
        hash.length <= 16 -> hash
        else -> hash.take(8) + "…" + hash.takeLast(8)
    }
}
