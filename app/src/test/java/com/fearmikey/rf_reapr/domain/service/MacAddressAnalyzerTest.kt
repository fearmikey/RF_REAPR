package com.fearmikey.rf_reapr.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MacAddressAnalyzerTest {

    @Test
    fun `parses all common notations to the same address`() {
        val expected = "B827EB123456"
        listOf("B8:27:EB:12:34:56", "b8-27-eb-12-34-56", "b827.eb12.3456", "B827EB123456", " b8:27:eb:12:34:56 ")
            .forEach { assertEquals(it, expected, MacAddressAnalyzer.parse(it)?.normalized) }
    }

    @Test
    fun `rejects invalid input`() {
        assertNull(MacAddressAnalyzer.parse("B8:27:EB:12:34"))
        assertNull(MacAddressAnalyzer.parse("G8:27:EB:12:34:56"))
        assertNull(MacAddressAnalyzer.parse("B8:27:EB:12:34:56:78"))
        assertNull(MacAddressAnalyzer.parse(""))
    }

    @Test
    fun `formats address in each notation`() {
        val mac = MacAddressAnalyzer.parse("b827eb123456")!!
        assertEquals("B8:27:EB:12:34:56", mac.colonFormat)
        assertEquals("B8-27-EB-12-34-56", mac.dashFormat)
        assertEquals("b827.eb12.3456", mac.ciscoFormat)
        assertEquals("B827EB", mac.oui)
        assertEquals("B8:27:EB", mac.ouiColonFormat)
    }

    @Test
    fun `decodes multicast and locally administered bits`() {
        val vendor = MacAddressAnalyzer.parse("B8:27:EB:00:00:01")!!
        assertFalse(vendor.isMulticast)
        assertFalse(vendor.isLocallyAdministered)
        assertFalse(vendor.isRandomized)

        // 0xDA = 1101 1010 -> U/L bit set, I/G clear: a typical randomized phone MAC.
        val randomized = MacAddressAnalyzer.parse("DA:A1:19:00:00:01")!!
        assertTrue(randomized.isLocallyAdministered)
        assertTrue(randomized.isRandomized)

        val multicast = MacAddressAnalyzer.parse("01:00:5E:00:00:FB")!!
        assertTrue(multicast.isMulticast)
        assertFalse(multicast.isRandomized)

        assertTrue(MacAddressAnalyzer.parse("FF:FF:FF:FF:FF:FF")!!.isBroadcast)
    }

    @Test
    fun `ouiOf accepts prefixes and full addresses`() {
        assertEquals("B827EB", MacAddressAnalyzer.ouiOf("B8:27:EB"))
        assertEquals("B827EB", MacAddressAnalyzer.ouiOf("b827.eb12.3456"))
        assertNull(MacAddressAnalyzer.ouiOf("B8:27"))
    }

    @Test
    fun `extracts distinct addresses from free text`() {
        val arp = """
            IP address       HW type     Flags       HW address            Mask     Device
            192.168.1.1      0x1         0x2         aa:bb:cc:dd:ee:ff     *        wlan0
            192.168.1.20     0x1         0x2         B8-27-EB-12-34-56     *        wlan0
            switch# show mac  b827.eb12.3456  dynamic   Gi0/1
            duplicate AA:BB:CC:DD:EE:FF and a hash 0123456789abcdef0123 that isn't a MAC
        """.trimIndent()
        val found = MacAddressAnalyzer.extractAll(arp).map { it.normalized }
        assertEquals(listOf("AABBCCDDEEFF", "B827EB123456"), found)
    }
}
