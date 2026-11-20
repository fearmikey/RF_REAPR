package com.fearmikey.rf_reapr.domain.service

import com.fearmikey.rf_reapr.iperf.IperfConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IperfResultParserTest {

    private val tcpJson = """
        {
          "start": {
            "connected": [{"socket": 5, "local_host": "192.168.1.5", "local_port": 40000, "remote_host": "192.168.1.10", "remote_port": 5201}],
            "connecting_to": {"host": "192.168.1.10", "port": 5201},
            "test_start": {"protocol": "TCP", "num_streams": 2, "duration": 10, "reverse": 1}
          },
          "intervals": [
            {"streams": [], "sum": {"start": 0, "end": 1.0, "bits_per_second": 9.4e8, "retransmits": 0}},
            {"streams": [], "sum": {"start": 1.0, "end": 2.0, "bits_per_second": 9.1e8, "retransmits": 3}}
          ],
          "end": {
            "sum_sent": {"bytes": 1175000000, "bits_per_second": 940000000.5, "retransmits": 3},
            "sum_received": {"bytes": 1170000000, "bits_per_second": 936000000.0},
            "cpu_utilization_percent": {"host_total": 12.5, "remote_total": 30.1}
          }
        }
    """.trimIndent()

    @Test
    fun `parses tcp results`() {
        val s = IperfResultParser.parse(tcpJson)!!
        assertEquals("TCP", s.protocol)
        assertEquals("client", s.role)
        assertEquals("192.168.1.10", s.remoteHost)
        assertEquals(5201, s.remotePort)
        assertEquals(2, s.numStreams)
        assertTrue(s.reverse)
        assertEquals(936000000.0, s.headlineBps!!, 1.0)
        assertEquals(3, s.retransmits)
        assertEquals(2, s.intervals.size)
        assertEquals(9.1e8, s.intervals[1].bitsPerSecond, 1.0)
        assertEquals(12.5, s.cpuLocalPercent!!, 0.01)
        assertFalse(s.isUdp)
        assertNull(s.jitterMs)
    }

    @Test
    fun `parses udp results and trailing native error text`() {
        val udp = """
            {"start": {"test_start": {"protocol": "UDP", "num_streams": 1, "duration": 5, "reverse": 0}},
             "intervals": [],
             "end": {"sum": {"bytes": 62500000, "bits_per_second": 100000000, "jitter_ms": 0.123, "lost_packets": 12, "packets": 43000, "lost_percent": 0.028}}}
            iperf_run_client failed: something
        """.trimIndent()
        val s = IperfResultParser.parse(udp)!!
        assertTrue(s.isUdp)
        assertEquals(1e8, s.sentBps!!, 1.0)
        assertEquals(0.123, s.jitterMs!!, 1e-6)
        assertEquals(12L, s.lostPackets)
        assertNull(s.retransmits)
    }

    @Test
    fun `surfaces iperf errors and handles non-json output`() {
        val s = IperfResultParser.parse("""{"start": {}, "intervals": [], "end": {}, "error": "unable to connect to server: Connection refused"}""")!!
        assertEquals("unable to connect to server: Connection refused", s.error)
        assertNull(s.headlineBps)
        assertNull(IperfResultParser.parse("Error executing iPerf: library not loaded"))
    }

    @Test
    fun `formats rates and sizes`() {
        assertEquals("1.25 Gbit/s", IperfResultParser.formatBitrate(1.25e9))
        assertEquals("94.2 Mbit/s", IperfResultParser.formatBitrate(9.42e7))
        assertEquals("1.5 MB", IperfResultParser.formatBytes(1_572_864))
    }

    @Test
    fun `builds iperf arguments`() {
        val client = IperfConfig(serverHost = "10.0.0.2", durationSeconds = 30, parallelStreams = 4, reverse = true, udp = true, udpBitrateMbps = 500, port = 5202).toArgs()
        assertEquals(
            listOf("-c", "10.0.0.2", "-t", "30", "-P", "4", "-R", "-u", "-b", "500M", "--connect-timeout", "5000", "-p", "5202", "-J"),
            client
        )
        assertEquals(listOf("-s", "-1", "-B", "192.168.1.5", "-J"), IperfConfig(isServerMode = true, bindInterfaceIp = "192.168.1.5").toArgs())
    }
}
