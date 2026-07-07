package com.example.rf_reapr.domain.service

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceIdentificationServiceTest {

    @Test
    fun `getManufacturer returns Apple for Apple OUI`() {
        val mac = "00:03:93:12:34:56"
        val manufacturer = DeviceIdentificationService.getManufacturer(mac)
        assertEquals("Apple", manufacturer)
    }

    @Test
    fun `getManufacturer returns Ubiquiti for Ubiquiti OUI`() {
        val mac = "24:A4:3C:AA:BB:CC"
        val manufacturer = DeviceIdentificationService.getManufacturer(mac)
        assertEquals("Ubiquiti", manufacturer)
    }

    @Test
    fun `getManufacturer returns Raspberry Pi for Raspberry Pi OUI`() {
        val mac = "B8:27:EB:11:22:33"
        val manufacturer = DeviceIdentificationService.getManufacturer(mac)
        assertEquals("Raspberry Pi", manufacturer)
    }

    @Test
    fun `getManufacturer returns Cisco for Cisco OUI`() {
        val mac = "00:01:42:00:11:22"
        val manufacturer = DeviceIdentificationService.getManufacturer(mac)
        assertEquals("Cisco", manufacturer)
    }

    @Test
    fun `getManufacturer returns null for unknown OUI`() {
        val mac = "FF:FF:FF:00:11:22"
        val manufacturer = DeviceIdentificationService.getManufacturer(mac)
        assertEquals(null, manufacturer)
    }

    @Test
    fun `getManufacturer handles various MAC formats`() {
        assertEquals("Apple", DeviceIdentificationService.getManufacturer("000393123456"))
        assertEquals("Apple", DeviceIdentificationService.getManufacturer("00-03-93-12-34-56"))
        assertEquals("Apple", DeviceIdentificationService.getManufacturer("00:03:93:12:34:56"))
    }
}
