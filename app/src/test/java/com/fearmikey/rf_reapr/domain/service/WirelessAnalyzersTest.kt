package com.fearmikey.rf_reapr.domain.service

import com.fearmikey.rf_reapr.domain.model.RiskLevel
import com.fearmikey.rf_reapr.domain.model.WifiAccessPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WirelessAnalyzersTest {

    // ---- BLE ----
    @Test
    fun `classifies airtag and ibeacon`() {
        val findMy = byteArrayOf(0x12, 0x19) + ByteArray(20)
        assertEquals(BleCategory.APPLE_FIND_MY, BleAdvertisementAnalyzer.classify(mapOf(0x004C to findMy), emptyList()).category)

        val ibeaconData = byteArrayOf(0x02, 0x15) + ByteArray(16) + byteArrayOf(0x00, 0x01, 0x00, 0x02, 0xC5.toByte())
        val c = BleAdvertisementAnalyzer.classify(mapOf(0x004C to ibeaconData), emptyList())
        assertEquals(BleCategory.IBEACON, c.category)
        assertTrue(c.detail!!.contains("major 1"))
    }

    @Test
    fun `classifies tile and google find my`() {
        assertEquals(BleCategory.TILE, BleAdvertisementAnalyzer.classify(emptyMap(), listOf("0000feed-0000-1000-8000-00805f9b34fb")).category)
        val fmdn = mapOf("0000feaa-0000-1000-8000-00805f9b34fb" to byteArrayOf(0x40, 0x00))
        assertEquals(BleCategory.GOOGLE_FIND_MY, BleAdvertisementAnalyzer.classify(emptyMap(), emptyList(), fmdn.keys.toList(), fmdn).category)
    }

    @Test
    fun `distance uses advertised tx power`() {
        // advertisedTxPower -59 → ref -100; at rssi -100 distance ~1m floor
        assertTrue(BleAdvertisementAnalyzer.estimateDistance(-59) < 1.5)
        assertTrue(BleAdvertisementAnalyzer.estimateDistance(-90) > BleAdvertisementAnalyzer.estimateDistance(-50))
    }

    // ---- Wi-Fi ----
    private fun ap(ssid: String, bssid: String, caps: String, freq: Int = 2412) =
        WifiAccessPoint(ssid, bssid, -50, freq, 20, caps)

    @Test
    fun `parses wifi security families`() {
        assertEquals(WifiSecurityFamily.OPEN, WifiSecurityAnalyzer.analyze("[ESS]").family)
        assertEquals(WifiSecurityFamily.WEP, WifiSecurityAnalyzer.analyze("[WEP][ESS]").family)
        assertEquals(WifiSecurityFamily.WPA2, WifiSecurityAnalyzer.analyze("[WPA2-PSK-CCMP][ESS]").family)
        assertEquals(WifiSecurityFamily.WPA3, WifiSecurityAnalyzer.analyze("[RSN-SAE-CCMP][ESS]").family)
        assertTrue(WifiSecurityAnalyzer.analyze("[WPA2-PSK-CCMP][WPS][ESS]").wps)
        assertEquals(RiskLevel.HIGH, WifiSecurityAnalyzer.analyze("[ESS]").risk)
    }

    @Test
    fun `detects evil twin open clone`() {
        val aps = listOf(
            ap("CorpWiFi", "00:11:22:33:44:55", "[WPA2-PSK-CCMP][ESS]"),
            ap("CorpWiFi", "66:77:88:99:AA:BB", "[ESS]")
        )
        val rogues = WifiSecurityAnalyzer.findRogues(aps)
        assertNotNull(rogues["66:77:88:99:AA:BB"])
        assertNull(rogues["00:11:22:33:44:55"])
    }

    @Test
    fun `recommends least congested 2_4 channel`() {
        // All neighbours on channel 1 → recommend 6 or 11.
        val aps = (1..3).map { ap("net$it", "00:00:00:00:00:0$it", "[WPA2][ESS]", 2412) }
        val advice = WifiSecurityAnalyzer.recommendChannel(aps, WifiBand.GHZ_2_4)
        assertNotNull(advice)
        assertTrue(advice!!.bestChannel == 6 || advice.bestChannel == 11)
    }

    @Test
    fun `wifi channel and band math`() {
        assertEquals(WifiBand.GHZ_2_4, WifiSecurityAnalyzer.band(2412))
        assertEquals(WifiBand.GHZ_5, WifiSecurityAnalyzer.band(5180))
        assertEquals(WifiBand.GHZ_6, WifiSecurityAnalyzer.band(5955))
        assertEquals(1, WifiSecurityAnalyzer.channel(2412))
        assertEquals(36, WifiSecurityAnalyzer.channel(5180))
    }

    // ---- SDR ----
    @Test
    fun `parses rtl_tcp header and nudges spectrum math`() {
        val header = byteArrayOf('R'.code.toByte(), 'T'.code.toByte(), 'L'.code.toByte(), '0'.code.toByte(),
            0, 0, 0, 5, 0, 0, 0, 29)
        val info = SdrDsp.parseDongleInfo(header)!!
        assertEquals(5, info.tunerType)
        assertTrue(info.tunerName.contains("R820T"))
        assertEquals(100_000_000L + 256_000, SdrDsp.binToFrequency(768, 1024, 100_000_000L, 1_024_000))
        assertEquals(50, SdrDsp.gainCommandValue(5))
    }

    // ---- USB ----
    @Test
    fun `flags badusb keyboard plus storage`() {
        val device = UsbDeviceInfo(
            "usb/001", 0x1234, 0x5678, "Acme", "Flash Drive", 0x00,
            listOf(UsbInterfaceInfo(0x03, 1, 1, 1), UsbInterfaceInfo(0x08, 6, 0x50, 2))
        )
        val findings = UsbAuditAnalyzer.findings(device)
        assertEquals(RiskLevel.HIGH, findings.first().severity)
        assertTrue(findings.any { it.title.contains("composite") || it.title.contains("keyboard") })
    }

    @Test
    fun `identifies known attack board`() {
        val digispark = UsbDeviceInfo("usb/002", 0x16D0, 0x0753, null, "Digispark", 0x00, listOf(UsbInterfaceInfo(0x03, 0, 0, 1)))
        assertEquals(RiskLevel.HIGH, UsbAuditAnalyzer.findings(digispark).first().severity)
        assertEquals("HID keyboard", UsbAuditAnalyzer.className(0x03, 1, 1))
    }

    // ---- Cellular ----
    @Test
    fun `lte band and downlink frequency`() {
        assertEquals("B3", CellAnalyzer.bandLabel(CellTech.LTE, 1575))
        val mhz = CellAnalyzer.downlinkMhz(CellTech.LTE, 1575)!!
        assertTrue(mhz in 1842.0..1843.0)
        assertNull(CellAnalyzer.valid(Int.MAX_VALUE))
        assertEquals(42, CellAnalyzer.valid(42))
    }

    @Test
    fun `flags 2g downgrade`() {
        fun cell(tech: CellTech, registered: Boolean, dbm: Int, tac: Int = 100) =
            CellSummary(tech, registered, "310", "260", 123, tac, 1, 100, dbm, 3)
        val cells = listOf(cell(CellTech.GSM, true, -70), cell(CellTech.LTE, false, -90))
        val findings = CellAnalyzer.findings(cells)
        assertEquals(RiskLevel.HIGH, findings.first().severity)
        assertTrue(findings.first().title.contains("2G"))
    }

    // ---- DuckyScript ----
    @Test
    fun `validates ducky script`() {
        val good = "REM test\nDEFAULTDELAY 100\nGUI r\nDELAY 500\nSTRING notepad\nENTER"
        val r = DuckyScriptValidator.validate(good)
        assertTrue(r.isValid)
        assertEquals(0, r.errors)
        assertTrue(r.commandCount >= 4)

        val bad = DuckyScriptValidator.validate("DELAY abc\nSTRIG hello\nGUI r")
        assertTrue(bad.errors >= 2)
        assertTrue(bad.issues.any { it.message.contains("number") })
    }

    // ---- NFC ----
    @Test
    fun `decodes ndef text and uri`() {
        val textPayload = byteArrayOf(0x02) + "en".toByteArray() + "Hi".toByteArray()
        assertTrue(NfcAnalyzer.decodeText(textPayload).startsWith("Hi"))
        val uriPayload = byteArrayOf(0x04) + "example.com".toByteArray() // 0x04 = https://
        assertEquals("https://example.com", NfcAnalyzer.decodeUri(uriPayload))
    }

    @Test
    fun `identifies mifare classic and flags it`() {
        val facts = NfcTagFacts(uid = byteArrayOf(0x04, 0x11, 0x22, 0x33), techList = listOf("android.nfc.tech.MifareClassic", "android.nfc.tech.NfcA"), sak = 0x08, classicSize = 1024)
        val chip = NfcAnalyzer.identifyChip(facts)
        assertTrue(chip.contains("Classic"))
        val findings = NfcAnalyzer.findings(facts, chip, emptyList())
        assertEquals(RiskLevel.HIGH, findings.first().severity)
    }
}
