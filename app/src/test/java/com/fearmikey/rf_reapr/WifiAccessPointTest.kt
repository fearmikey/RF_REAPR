package com.fearmikey.rf_reapr

import com.fearmikey.rf_reapr.domain.model.WifiAccessPoint
import org.junit.Test
import org.junit.Assert.assertEquals

class WifiAccessPointTest {

    @Test
    fun testGetChannel_2GHz() {
        assertEquals(1, createAp(2412).getChannel())
        assertEquals(6, createAp(2437).getChannel())
        assertEquals(11, createAp(2462).getChannel())
        assertEquals(14, createAp(2484).getChannel())
    }

    @Test
    fun testGetChannel_5GHz() {
        assertEquals(36, createAp(5180).getChannel())
        assertEquals(44, createAp(5220).getChannel())
        assertEquals(149, createAp(5745).getChannel())
        assertEquals(165, createAp(5825).getChannel())
    }

    @Test
    fun testGetChannel_6GHz() {
        assertEquals(1, createAp(5945).getChannel())
        assertEquals(233, createAp(7105).getChannel())
    }

    @Test
    fun testGetChannel_Invalid() {
        assertEquals(-1, createAp(0).getChannel())
        assertEquals(-1, createAp(1000).getChannel())
    }

    private fun createAp(frequency: Int) = WifiAccessPoint(
        ssid = "Test",
        bssid = "00:00:00:00:00:00",
        signalLevel = -50,
        frequency = frequency,
        bandwidth = 20,
        capabilities = "",
        isRogueSuspect = false
    )
}
