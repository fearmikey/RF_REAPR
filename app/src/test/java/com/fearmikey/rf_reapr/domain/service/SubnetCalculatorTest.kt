package com.fearmikey.rf_reapr.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubnetCalculatorTest {

    private fun calc(ip: String, prefix: Int) = SubnetCalculator.calculate(SubnetCalculator.parseIpv4(ip)!!, prefix)
    private fun f(v: Long) = SubnetCalculator.format(v)

    @Test
    fun `calculates a standard slash 24`() {
        val s = calc("192.168.1.100", 24)
        assertEquals("192.168.1.0", f(s.network))
        assertEquals("192.168.1.255", f(s.broadcast))
        assertEquals("255.255.255.0", f(s.netmask))
        assertEquals("0.0.0.255", f(s.wildcard))
        assertEquals("192.168.1.1", f(s.firstHost))
        assertEquals("192.168.1.254", f(s.lastHost))
        assertEquals(254L, s.usableHosts)
        assertEquals(256L, s.totalAddresses)
        assertEquals('C', s.ipClass)
        assertEquals(Ipv4AddressType.PRIVATE, s.addressType)
        assertEquals("192.168.1.0/24", s.cidr)
    }

    @Test
    fun `handles edge prefixes`() {
        assertEquals(1L, calc("10.0.0.1", 32).usableHosts)
        val p2p = calc("10.0.0.1", 31)
        assertEquals(2L, p2p.usableHosts)
        assertEquals("10.0.0.0", f(p2p.firstHost))
        assertEquals("10.0.0.1", f(p2p.lastHost))
        val all = calc("8.8.8.8", 0)
        assertEquals(4294967296L, all.totalAddresses)
        assertEquals("0.0.0.0", f(all.netmask))
        assertEquals("255.255.255.255", f(all.broadcast))
    }

    @Test
    fun `parses masks prefixes and inline cidr`() {
        assertEquals(24, SubnetCalculator.parsePrefix("255.255.255.0"))
        assertEquals(20, SubnetCalculator.parsePrefix("/20"))
        assertNull(SubnetCalculator.parsePrefix("255.0.255.0")) // non-contiguous
        assertNull(SubnetCalculator.parsePrefix("33"))
        assertEquals(SubnetCalculator.parseIpv4("10.0.0.5")!! to 8, SubnetCalculator.parseInput("10.0.0.5/8", "24"))
        assertEquals(SubnetCalculator.parseIpv4("10.0.0.5")!! to 24, SubnetCalculator.parseInput("10.0.0.5", "24"))
        assertNull(SubnetCalculator.parseIpv4("256.1.1.1"))
        assertNull(SubnetCalculator.parseIpv4("google.com")) // never triggers a DNS lookup
    }

    @Test
    fun `splits and checks containment`() {
        val s = calc("10.0.0.0", 24)
        val halves = SubnetCalculator.split(s, 25)
        assertEquals(listOf("10.0.0.0/25", "10.0.0.128/25"), halves.map { it.cidr })
        assertEquals(256L, SubnetCalculator.subnetCount(calc("10.0.0.0", 16), 24))
        assertEquals(256, SubnetCalculator.split(calc("10.0.0.0", 8), 24).size) // capped
        assertTrue(s.contains(SubnetCalculator.parseIpv4("10.0.0.200")!!))
        assertFalse(s.contains(SubnetCalculator.parseIpv4("10.0.1.1")!!))
    }

    @Test
    fun `classifies and formats`() {
        assertEquals(Ipv4AddressType.CGNAT, SubnetCalculator.classify(SubnetCalculator.parseIpv4("100.70.1.1")!!))
        assertEquals(Ipv4AddressType.PUBLIC, SubnetCalculator.classify(SubnetCalculator.parseIpv4("8.8.8.8")!!))
        assertEquals(Ipv4AddressType.MULTICAST, SubnetCalculator.classify(SubnetCalculator.parseIpv4("224.0.0.251")!!))
        assertEquals("11000000.10101000.00000001.00000001", SubnetCalculator.toBinary(SubnetCalculator.parseIpv4("192.168.1.1")!!))
        assertEquals("0xC0A80101", SubnetCalculator.toHex(SubnetCalculator.parseIpv4("192.168.1.1")!!))
    }
}
