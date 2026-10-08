package com.fearmikey.rf_reapr.util

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StegoEngineTest {

    @Test
    fun testEncodeAndDecode_Success() {
        // Arrange
        // Create a fake image (e.g., 100x100) filled with white color
        val originalBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        originalBitmap.eraseColor(Color.WHITE)

        val secretMessage = "This is a top secret RF-REAPR message!"
        val passphrase = "SuperSecurePassword123".toCharArray()

        // Act
        val encodedBitmap = StegoEngine.encode(originalBitmap, secretMessage, passphrase)
        val result = StegoEngine.decode(encodedBitmap, passphrase)

        // Assert
        assertTrue("Decoding should be successful", result.isSuccess)
        assertEquals(secretMessage, result.getOrNull())
    }

    @Test
    fun testDecode_WrongPassphrase_Fails() {
        // Arrange
        val originalBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        originalBitmap.eraseColor(Color.WHITE)

        val secretMessage = "Top Secret"
        val correctPassphrase = "correct_password".toCharArray()
        val wrongPassphrase = "wrong_password".toCharArray()

        // Act
        val encodedBitmap = StegoEngine.encode(originalBitmap, secretMessage, correctPassphrase)
        val result = StegoEngine.decode(encodedBitmap, wrongPassphrase)

        // Assert
        assertTrue("Decoding with wrong passphrase should fail", result.isFailure)
    }

    @Test
    fun testDecode_NotStegoImage_Fails() {
        // Arrange
        val normalBitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        normalBitmap.eraseColor(Color.BLUE)

        val passphrase = "password".toCharArray()

        // Act
        val result = StegoEngine.decode(normalBitmap, passphrase)

        // Assert
        assertTrue("Decoding a non-stego image should fail", result.isFailure)
    }
}
