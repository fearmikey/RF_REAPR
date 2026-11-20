package com.fearmikey.rf_reapr.data.backup

import java.io.DataInputStream
import java.io.EOFException
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-based, streaming authenticated encryption for backup files (JDK crypto only,
 * no third-party dependencies).
 *
 * File layout:
 * ```
 * header:  "RFREAPRBK" | version(1) | pbkdf2Iterations(int) | salt(16) | noncePrefix(8)
 * chunks:  length(int) | AES-256-GCM ciphertext+tag     (repeated)
 * ```
 * Each chunk (≤ [CHUNK_SIZE] plaintext bytes) is sealed with nonce = noncePrefix || index and
 * AAD = header || index || isFinal, so chunks can't be reordered, dropped, or the file
 * truncated without failing authentication. Memory use stays at ~1 chunk regardless of
 * backup size (Android's GCM buffers the whole message on decrypt otherwise).
 */
object BackupCrypto {

    private val MAGIC = "RFREAPRBK".toByteArray(Charsets.US_ASCII)
    private const val VERSION: Byte = 1
    const val CHUNK_SIZE = 1 shl 20
    const val DEFAULT_ITERATIONS = 310_000
    private const val SALT_LEN = 16
    private const val NONCE_PREFIX_LEN = 8
    private const val TAG_BITS = 128
    private const val MAX_ITERATIONS = 10_000_000

    class WrongPasswordException : IOException("Wrong password or corrupted backup")
    class NotABackupException : IOException("Not an RF-REAPR backup file")

    /** Wraps [out]; everything written is encrypted. [close] MUST be called to write the final chunk. */
    fun encryptingStream(out: OutputStream, password: CharArray, iterations: Int = DEFAULT_ITERATIONS): OutputStream {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LEN).also(random::nextBytes)
        val noncePrefix = ByteArray(NONCE_PREFIX_LEN).also(random::nextBytes)
        val header = ByteBuffer.allocate(MAGIC.size + 1 + 4 + SALT_LEN + NONCE_PREFIX_LEN)
            .put(MAGIC).put(VERSION).putInt(iterations).put(salt).put(noncePrefix).array()
        out.write(header)
        return EncryptingOutputStream(out, deriveKey(password, salt, iterations), header, noncePrefix)
    }

    /** Wraps [input]; reads return decrypted bytes. Throws [WrongPasswordException] on bad password/tamper. */
    fun decryptingStream(input: InputStream, password: CharArray): InputStream {
        val data = DataInputStream(input)
        val magic = ByteArray(MAGIC.size)
        try {
            data.readFully(magic)
        } catch (_: EOFException) {
            throw NotABackupException()
        }
        if (!magic.contentEquals(MAGIC)) throw NotABackupException()
        val version = data.readByte()
        if (version != VERSION) throw IOException("Unsupported backup version $version")
        val iterations = data.readInt()
        if (iterations !in 1..MAX_ITERATIONS) throw NotABackupException()
        val salt = ByteArray(SALT_LEN).also(data::readFully)
        val noncePrefix = ByteArray(NONCE_PREFIX_LEN).also(data::readFully)
        val header = ByteBuffer.allocate(MAGIC.size + 1 + 4 + SALT_LEN + NONCE_PREFIX_LEN)
            .put(MAGIC).put(version).putInt(iterations).put(salt).put(noncePrefix).array()
        return DecryptingInputStream(data, deriveKey(password, salt, iterations), header, noncePrefix)
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun nonce(prefix: ByteArray, index: Int): ByteArray =
        ByteBuffer.allocate(NONCE_PREFIX_LEN + 4).put(prefix).putInt(index).array()

    private fun aad(header: ByteArray, index: Int, isFinal: Boolean): ByteArray =
        ByteBuffer.allocate(header.size + 5).put(header).putInt(index).put(if (isFinal) 1 else 0).array()

    private class EncryptingOutputStream(
        out: OutputStream,
        private val key: SecretKeySpec,
        private val header: ByteArray,
        private val noncePrefix: ByteArray
    ) : FilterOutputStream(out) {
        private val buffer = ByteArray(CHUNK_SIZE)
        private var size = 0
        private var index = 0
        private var closed = false

        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

        override fun write(b: ByteArray, off: Int, len: Int) {
            check(!closed) { "stream closed" }
            var offset = off
            var remaining = len
            while (remaining > 0) {
                // Only seal a full buffer once we know more data follows, so the last chunk
                // is always the one written by close() with isFinal = true.
                if (size == CHUNK_SIZE) sealChunk(isFinal = false)
                val n = minOf(remaining, CHUNK_SIZE - size)
                System.arraycopy(b, offset, buffer, size, n)
                size += n
                offset += n
                remaining -= n
            }
        }

        private fun sealChunk(isFinal: Boolean) {
            if (index == Int.MAX_VALUE) throw IOException("Backup too large")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce(noncePrefix, index)))
            cipher.updateAAD(aad(header, index, isFinal))
            val sealed = cipher.doFinal(buffer, 0, size)
            out.write(ByteBuffer.allocate(4).putInt(sealed.size).array())
            out.write(sealed)
            index++
            size = 0
        }

        override fun flush() = out.flush()

        override fun close() {
            if (closed) return
            closed = true
            try {
                sealChunk(isFinal = true)
                out.flush()
            } finally {
                buffer.fill(0)
                out.close()
            }
        }
    }

    private class DecryptingInputStream(
        private val data: DataInputStream,
        private val key: SecretKeySpec,
        private val header: ByteArray,
        private val noncePrefix: ByteArray
    ) : InputStream() {
        private var plain = ByteArray(0)
        private var pos = 0
        private var index = 0
        private var finished = false
        /** Length prefix of the next chunk, read ahead so we know whether the current one is final. */
        private var nextLength: Int? = readLength() ?: throw NotABackupException()

        private fun readLength(): Int? = try {
            data.readInt()
        } catch (_: EOFException) {
            null
        }

        private fun fill(): Boolean {
            while (pos >= plain.size) {
                if (finished) return false
                val length = nextLength ?: return false
                if (length !in 16..(CHUNK_SIZE + 16)) throw WrongPasswordException()
                val sealed = ByteArray(length)
                try {
                    data.readFully(sealed)
                } catch (_: EOFException) {
                    throw WrongPasswordException()
                }
                nextLength = readLength()
                val isFinal = nextLength == null
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce(noncePrefix, index)))
                cipher.updateAAD(aad(header, index, isFinal))
                plain = try {
                    cipher.doFinal(sealed)
                } catch (_: AEADBadTagException) {
                    throw WrongPasswordException()
                }
                pos = 0
                index++
                if (isFinal) finished = true
            }
            return true
        }

        override fun read(): Int = if (!fill()) -1 else plain[pos++].toInt() and 0xFF

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (!fill()) return -1
            val n = minOf(len, plain.size - pos)
            System.arraycopy(plain, pos, b, off, n)
            pos += n
            return n
        }

        override fun close() = data.close()
    }
}
