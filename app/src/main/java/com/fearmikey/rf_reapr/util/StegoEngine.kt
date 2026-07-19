package com.fearmikey.rf_reapr.util

import android.graphics.Bitmap
import android.graphics.Color
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object StegoEngine {
    private const val MAGIC_BYTES = "REAPR"
    private const val HEADER_VERSION = 1.toByte()
    private const val ITERATIONS = 10000
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH = 128

    fun encode(bitmap: Bitmap, message: String, passphrase: CharArray): Bitmap {
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
        
        val key = deriveKey(passphrase, salt)
        val encryptedData = encrypt(message.toByteArray(), key, iv)
        
        val header = ByteBuffer.allocate(MAGIC_BYTES.length + 1 + 4 + SALT_LENGTH + IV_LENGTH)
        header.put(MAGIC_BYTES.toByteArray())
        header.put(HEADER_VERSION)
        header.putInt(encryptedData.size)
        header.put(salt)
        header.put(iv)
        
        val fullPayload = ByteBuffer.allocate(header.capacity() + encryptedData.size)
        fullPayload.put(header.array())
        fullPayload.put(encryptedData)
        val payloadBytes = fullPayload.array()

        val outputBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val width = outputBitmap.width
        val height = outputBitmap.height
        
        if (payloadBytes.size * 8 > width * height * 3) {
            throw IllegalArgumentException("Message too large for this image")
        }

        var byteIdx = 0
        var bitIdx = 0
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (byteIdx >= payloadBytes.size) break
                
                var pixel = outputBitmap.getPixel(x, y)
                var r = Color.red(pixel)
                var g = Color.green(pixel)
                var b = Color.blue(pixel)
                val a = Color.alpha(pixel)

                // Embed in R
                if (byteIdx < payloadBytes.size) {
                    val bit = (payloadBytes[byteIdx].toInt() shr (7 - bitIdx)) and 1
                    r = (r and 0xFE) or bit
                    bitIdx++
                    if (bitIdx == 8) { bitIdx = 0; byteIdx++ }
                }

                // Embed in G
                if (byteIdx < payloadBytes.size) {
                    val bit = (payloadBytes[byteIdx].toInt() shr (7 - bitIdx)) and 1
                    g = (g and 0xFE) or bit
                    bitIdx++
                    if (bitIdx == 8) { bitIdx = 0; byteIdx++ }
                }

                // Embed in B
                if (byteIdx < payloadBytes.size) {
                    val bit = (payloadBytes[byteIdx].toInt() shr (7 - bitIdx)) and 1
                    b = (b and 0xFE) or bit
                    bitIdx++
                    if (bitIdx == 8) { bitIdx = 0; byteIdx++ }
                }

                outputBitmap.setPixel(x, y, Color.argb(a, r, g, b))
            }
            if (byteIdx >= payloadBytes.size) break
        }
        
        return outputBitmap
    }

    fun decode(bitmap: Bitmap, passphrase: CharArray): Result<String> {
        val width = bitmap.width
        val height = bitmap.height
        
        // Read header first (Fixed size)
        val headerSize = MAGIC_BYTES.length + 1 + 4 + SALT_LENGTH + IV_LENGTH
        val headerBytes = readBytes(bitmap, headerSize) ?: return Result.failure(Exception("Failed to read header"))
        
        val headerBuffer = ByteBuffer.wrap(headerBytes)
        val magic = ByteArray(MAGIC_BYTES.length).also { headerBuffer.get(it) }
        if (String(magic) != MAGIC_BYTES) {
            return Result.failure(Exception("Not a stego image or corrupted"))
        }
        
        val version = headerBuffer.get()
        if (version != HEADER_VERSION) {
            return Result.failure(Exception("Unsupported stego version"))
        }
        
        val dataSize = headerBuffer.getInt()
        val salt = ByteArray(SALT_LENGTH).also { headerBuffer.get(it) }
        val iv = ByteArray(IV_LENGTH).also { headerBuffer.get(it) }
        
        val totalBytesToRead = headerSize + dataSize
        val allBytes = readBytes(bitmap, totalBytesToRead) ?: return Result.failure(Exception("Failed to read payload"))
        val encryptedData = allBytes.sliceArray(headerSize until allBytes.size)
        
        return try {
            val key = deriveKey(passphrase, salt)
            val decryptedData = decrypt(encryptedData, key, iv)
            Result.success(String(decryptedData))
        } catch (e: Exception) {
            Result.failure(Exception("Decryption failed. Wrong passphrase?"))
        }
    }

    private fun readBytes(bitmap: Bitmap, count: Int): ByteArray? {
        val bytes = ByteArray(count)
        var byteIdx = 0
        var bitIdx = 0
        
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (byteIdx >= count) return bytes
                
                val pixel = bitmap.getPixel(x, y)
                val channels = listOf(Color.red(pixel), Color.green(pixel), Color.blue(pixel))
                
                for (channelValue in channels) {
                    if (byteIdx >= count) return bytes
                    
                    val bit = channelValue and 1
                    bytes[byteIdx] = (bytes[byteIdx].toInt() or (bit shl (7 - bitIdx))).toByte()
                    
                    bitIdx++
                    if (bitIdx == 8) { bitIdx = 0; byteIdx++ }
                }
            }
        }
        return if (byteIdx >= count) bytes else null
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, KEY_LENGTH)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }

    private fun encrypt(data: ByteArray, key: SecretKeySpec, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)
        return cipher.doFinal(data)
    }

    private fun decrypt(data: ByteArray, key: SecretKeySpec, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        return cipher.doFinal(data)
    }
}
