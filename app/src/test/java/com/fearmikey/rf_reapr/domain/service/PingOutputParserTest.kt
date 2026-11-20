package com.fearmikey.rf_reapr.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PingOutputParserTest {

    @Test
    fun `parses ipv4 hostname and ipv6 reply lines`() {
        val v4 = PingOutputParser.parseReply("64 bytes from 8.8.8.8: icmp_seq=1 ttl=117 time=12.3 ms")!!
        assertEquals(1, v4.seq)
        assertEquals(117, v4.ttl)
        assertEquals(12.3f, v4.timeMs, 0.001f)
        assertEquals("8.8.8.8", v4.from)

        val named = PingOutputParser.parseReply("64 bytes from dns.google (8.8.4.4): icmp_seq=7 ttl=117 time=9.87 ms")!!
        assertEquals("8.8.4.4", named.from)
        assertEquals(7, named.seq)

        val v6 = PingOutputParser.parseReply("64 bytes from 2001:4860:4860::8888: icmp_seq=2 ttl=117 time=0.98 ms")!!
        assertEquals("2001:4860:4860::8888", v6.from)
        assertEquals(0.98f, v6.timeMs, 0.001f)

        assertNull(PingOutputParser.parseReply("PING 8.8.8.8 (8.8.8.8) 56(84) bytes of data."))
    }

    @Test
    fun `parses error and summary lines`() {
        assertEquals(3, PingOutputParser.parseErrorSeq("From 192.168.1.1 icmp_seq=3 Destination Host Unreachable"))
        assertEquals(4, PingOutputParser.parseErrorSeq("From 10.0.0.1 icmp_seq=4 Time to live exceeded"))
        assertNull(PingOutputParser.parseErrorSeq("64 bytes from 8.8.8.8: icmp_seq=1 ttl=117 time=12.3 ms"))
        assertEquals(4 to 3, PingOutputParser.parseSummary("4 packets transmitted, 3 received, 25% packet loss, time 3004ms"))
        assertEquals(10 to 8, PingOutputParser.parseSummary("10 packets transmitted, 8 received, +2 errors, 20% packet loss, time 9012ms"))
    }

    @Test
    fun `computes stats with inferred loss and jitter`() {
        val replies = listOf(
            PingReply(1, 10f), PingReply(2, 20f), PingReply(4, 14f) // seq 3 lost
        )
        val stats = PingOutputParser.computeStats(replies)
        assertEquals(4, stats.transmitted)
        assertEquals(3, stats.received)
        assertEquals(25f, stats.lossPercent, 0.01f)
        assertEquals(10f, stats.minMs!!, 0.001f)
        assertEquals(20f, stats.maxMs!!, 0.001f)
        assertEquals(14.667f, stats.avgMs!!, 0.01f)
        assertEquals(8f, stats.jitterMs!!, 0.001f) // (|20-10| + |14-20|) / 2
        assertEquals(PingQuality.POOR, stats.quality)
    }

    @Test
    fun `summary line overrides inferred counts`() {
        val stats = PingOutputParser.computeStats(listOf(PingReply(1, 5f)), summary = 5 to 1)
        assertEquals(5, stats.transmitted)
        assertEquals(80f, stats.lossPercent, 0.01f)
    }

    @Test
    fun `quality grades`() {
        assertEquals(PingQuality.EXCELLENT, PingOutputParser.computeStats(listOf(PingReply(1, 5f), PingReply(2, 6f))).quality)
        assertEquals(PingQuality.UNREACHABLE, PingOutputParser.computeStats(emptyList(), summary = 4 to 0).quality)
    }

    @Test
    fun `rtt series marks gaps`() {
        val series = PingOutputParser.rttSeries(listOf(PingReply(1, 10f), PingReply(3, 30f)), upToSeq = 3)
        assertEquals(listOf(10f, null, 30f), series)
        assertEquals(60, PingOutputParser.rttSeries(emptyList(), upToSeq = 100).size)
    }
}
