package com.fearmikey.rf_reapr.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fearmikey.rf_reapr.domain.model.WifiAccessPoint
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReportGraphRendererTest {

    @Test
    fun testRenderWifiSpectrum_CreatesBitmapSuccessfully() {
        // Arrange
        val aps = listOf(
            WifiAccessPoint(
                bssid = "00:11:22:33:44:55",
                ssid = "Test_WiFi_1",
                frequency = 2412,
                signalLevel = -50,
                capabilities = "[WPA2-PSK-CCMP]",
                bandwidth = 20,
                timestamp = System.currentTimeMillis()
            ),
            WifiAccessPoint(
                bssid = "AA:BB:CC:DD:EE:FF",
                ssid = "Test_WiFi_2",
                frequency = 2437,
                signalLevel = -70,
                capabilities = "[WPA3-SAE-CCMP]",
                bandwidth = 40,
                timestamp = System.currentTimeMillis()
            )
        )
        val minFreq = 2400f
        val maxFreq = 2500f
        val title = "2.4 GHz Spectrum"

        // Act
        val bitmap = ReportGraphRenderer.renderWifiSpectrum(aps, minFreq, maxFreq, title)

        // Assert
        assertNotNull(bitmap)
        assertTrue(bitmap.width > 0)
        assertTrue(bitmap.height > 0)
        // Check that it's the expected size
        assertTrue(bitmap.width == 800)
        assertTrue(bitmap.height == 400)
    }
}
