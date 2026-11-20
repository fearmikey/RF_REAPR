package com.fearmikey.rf_reapr.domain.service

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WolPacketBuilderTest {

    private val mac = MacAddressAnalyzer.parse("00:1A:2B:3C:4D:5E")!!
    private val macBytes = byteArrayOf(0x00, 0x1A, 0x2B, 0x3C, 0x4D, 0x5E)

    @Test
    fun `builds standard magic packet`() {
        val packet = WolPacketBuilder.build(mac)
        assertEquals(102, packet.size)
        assertTrue(packet.take(6).all { it == 0xFF.toByte() })
        for (rep in 0 until 16) {
            assertArrayEquals(macBytes, packet.copyOfRange(6 + rep * 6, 12 + rep * 6))
        }
    }

    @Test
    fun `appends secureon password`() {
        val password = WolPacketBuilder.parseSecureOn("AA:BB:CC:DD:EE:FF").getOrThrow()!!
        val packet = WolPacketBuilder.build(mac, password)
        assertEquals(108, packet.size)
        assertArrayEquals(password, packet.copyOfRange(102, 108))
    }

    @Test
    fun `parses secureon formats`() {
        assertNull(WolPacketBuilder.parseSecureOn("  ").getOrThrow())
        assertArrayEquals(byteArrayOf(192.toByte(), 168.toByte(), 1, 1), WolPacketBuilder.parseSecureOn("192.168.1.1").getOrThrow())
        assertEquals(4, WolPacketBuilder.parseSecureOn("deadbeef").getOrThrow()!!.size)
        assertTrue(WolPacketBuilder.parseSecureOn("abc").isFailure)
        assertTrue(WolPacketBuilder.parseSecureOn("zz:zz").isFailure)
    }
}
