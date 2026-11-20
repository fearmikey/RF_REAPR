package com.fearmikey.rf_reapr.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.random.Random

class BackupCryptoTest {

    // Low iteration count keeps the tests fast; the format stores the count.
    private fun encrypt(plain: ByteArray, password: String = "correct horse"): ByteArray {
        val out = ByteArrayOutputStream()
        BackupCrypto.encryptingStream(out, password.toCharArray(), iterations = 1_000).use { it.write(plain) }
        return out.toByteArray()
    }

    private fun decrypt(sealed: ByteArray, password: String = "correct horse"): ByteArray =
        BackupCrypto.decryptingStream(ByteArrayInputStream(sealed), password.toCharArray()).use { it.readBytes() }

    @Test
    fun roundTripsEmptySmallAndMultiChunkData() {
        listOf(0, 1, 1000, BackupCrypto.CHUNK_SIZE, BackupCrypto.CHUNK_SIZE * 2 + 17).forEach { size ->
            val plain = Random(size).nextBytes(size)
            assertArrayEquals("size $size", plain, decrypt(encrypt(plain)))
        }
    }

    @Test
    fun outputIsNotPlaintextAndIsRandomized() {
        val plain = ByteArray(4096) { 'A'.code.toByte() }
        val a = encrypt(plain)
        val b = encrypt(plain)
        assertEquals(false, String(a, Charsets.ISO_8859_1).contains("AAAAAAAA"))
        assertEquals(false, a.contentEquals(b))
    }

    @Test(expected = BackupCrypto.WrongPasswordException::class)
    fun wrongPasswordIsRejected() {
        decrypt(encrypt("secret evidence".toByteArray()), password = "wrong password")
    }

    @Test
    fun truncationIsDetected() {
        val sealed = encrypt(Random(1).nextBytes(BackupCrypto.CHUNK_SIZE * 2 + 5))
        // Drop the final chunk entirely: the remaining last chunk was sealed as non-final.
        val finalChunkLen = 4 + 5 + 16
        expectFailure { decrypt(sealed.copyOf(sealed.size - finalChunkLen)) }
        expectFailure { decrypt(sealed.copyOf(sealed.size - 1)) }
    }

    @Test
    fun tamperingIsDetected() {
        val sealed = encrypt("audit data".toByteArray())
        sealed[sealed.size - 3] = (sealed[sealed.size - 3].toInt() xor 0x01).toByte()
        expectFailure { decrypt(sealed) }
    }

    @Test(expected = BackupCrypto.NotABackupException::class)
    fun randomFileIsNotABackup() {
        decrypt("PK\u0003\u0004 just a zip".toByteArray())
    }

    @Test
    fun safeResolveBlocksZipSlip() {
        val root = File(System.getProperty("java.io.tmpdir"), "rfreapr-slip").apply { mkdirs() }
        BackupManager.safeResolve(root, "files/evidence/a.jpg")
        listOf("../evil", "files/../../evil", "/etc/passwd".removePrefix("/").let { "../../../../$it" }).forEach { name ->
            expectFailure { BackupManager.safeResolve(root, name) }
        }
    }

    private fun expectFailure(block: () -> Unit) {
        try {
            block()
            fail("expected an IOException")
        } catch (_: IOException) {
        }
    }
}
