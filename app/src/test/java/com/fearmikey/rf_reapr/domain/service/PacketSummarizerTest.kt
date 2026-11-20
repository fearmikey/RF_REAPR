package com.fearmikey.rf_reapr.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PacketSummarizerTest {

    /** IPv4 header (20 bytes) + UDP header (8) + DNS query for [name]. */
    private fun ipv4DnsQuery(name: String): ByteArray {
        val qname = name.split('.').flatMap { label -> listOf(label.length.toByte()) + label.toByteArray().toList() } + 0.toByte()
        val dns = listOf<Byte>(0x12, 0x34, 0x01, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0) + qname + listOf<Byte>(0, 1, 0, 1)
        val udp = listOf<Byte>(0xC3.toByte(), 0x50, 0x00, 0x35, 0, (8 + dns.size).toByte(), 0, 0)
        val ip = mutableListOf<Byte>(0x45, 0, 0, 0, 0, 0, 0, 0, 64, 17, 0, 0, 10, 0, 0, 2, 8, 8, 8, 8)
        return (ip + udp + dns).toByteArray()
    }

    @Test
    fun `parses ipv4 udp dns query`() {
        val packet = ipv4DnsQuery("example.com")
        val info = PacketSummarizer.parse(packet)!!
        assertEquals(4, info.ipVersion)
        assertEquals(TransportProtocol.UDP, info.protocol)
        assertEquals("10.0.0.2", info.source)
        assertEquals("8.8.8.8", info.destination)
        assertEquals(50000, info.sourcePort)
        assertEquals(53, info.destinationPort)
        assertEquals("example.com", info.dnsQuery)
    }

    @Test
    fun `parses ipv6 tcp header`() {
        val packet = ByteArray(60)
        packet[0] = 0x60
        packet[6] = 6 // TCP
        packet[8] = 0xFD.toByte(); packet[23] = 2 // fd00::2
        packet[24] = 0x20; packet[25] = 0x01; packet[26] = 0x0D; packet[27] = 0xB8.toByte(); packet[39] = 1 // 2001:db8::1
        packet[42] = 0x01; packet[43] = 0xBB.toByte() // dst port 443
        val info = PacketSummarizer.parse(packet)!!
        assertEquals(6, info.ipVersion)
        assertEquals(TransportProtocol.TCP, info.protocol)
        assertEquals(443, info.destinationPort)
        assertEquals("2001:db8:0:0:0:0:0:1", info.destination)
    }

    @Test
    fun `rejects truncated or non-ip data`() {
        assertNull(PacketSummarizer.parse(byteArrayOf(0x45, 0, 0)))
        assertNull(PacketSummarizer.parse(byteArrayOf(0x10, 0, 0, 0)))
        assertNull(PacketSummarizer.parse(ByteArray(0)))
    }

    @Test
    fun `aggregator tracks protocols destinations and recent dns names`() {
        val agg = CaptureAggregator(maxDnsNames = 2)
        agg.add(PacketSummarizer.parse(ipv4DnsQuery("a.com")), 60)
        agg.add(PacketSummarizer.parse(ipv4DnsQuery("b.com")), 60)
        agg.add(PacketSummarizer.parse(ipv4DnsQuery("c.com")), 60)
        agg.add(PacketSummarizer.parse(ipv4DnsQuery("b.com")), 60)
        agg.add(null, 10)

        assertEquals(5L, agg.packets)
        assertEquals(250L, agg.bytes)
        assertEquals(4L, agg.protocolCounts()[TransportProtocol.UDP])
        assertEquals("8.8.8.8:53" to 4L, agg.topDestinations().first())
        assertEquals(listOf("b.com", "c.com"), agg.recentDnsQueries())
    }
}
