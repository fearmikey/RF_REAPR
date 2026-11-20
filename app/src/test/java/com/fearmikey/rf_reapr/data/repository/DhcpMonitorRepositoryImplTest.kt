package com.fearmikey.rf_reapr.data.repository

import com.fearmikey.rf_reapr.domain.model.NetworkEventType
import com.fearmikey.rf_reapr.domain.model.NetworkNode
import com.fearmikey.rf_reapr.domain.repository.DhcpMonitorRepository.MonitoringStatus
import com.fearmikey.rf_reapr.domain.repository.LocalNetworkInfo
import com.fearmikey.rf_reapr.domain.repository.NetworkDiscoveryRepository
import com.fearmikey.rf_reapr.domain.scanner.NetworkScanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DhcpMonitorRepositoryImplTest {

    /** Returns one scripted sweep result per discoverDevices() call (repeating the last). */
    private class FakeDiscovery(private val sweeps: List<List<NetworkNode>>) : NetworkDiscoveryRepository {
        private var index = 0
        override fun discoverDevices(): Flow<NetworkScanner.ScanResult<NetworkNode>> {
            val sweep = sweeps[minOf(index++, sweeps.lastIndex)]
            return flowOf(NetworkScanner.ScanResult.Progress(1f, sweep), NetworkScanner.ScanResult.Finished(sweep))
        }
        override suspend fun getLocalNetworkInfo() = LocalNetworkInfo("192.168.1.2", "192.168.1.1", "255.255.255.0", 24)
        override fun startScan() = discoverDevices()
        override fun stopScan() = Unit
    }

    private fun node(ip: String, mac: String? = null) = NetworkNode(id = ip, ipAddress = ip, macAddress = mac)

    /** Runs [sweepCount] sweeps (each ends with a Waiting status) and returns every status emitted. */
    private fun runSweeps(repo: DhcpMonitorRepositoryImpl, sweepCount: Int): List<MonitoringStatus> = runBlocking {
        var waits = 0
        repo.startMonitoring(intervalMs = 1L)
            .transformWhile { status ->
                emit(status)
                !(status is MonitoringStatus.Waiting && ++waits == sweepCount)
            }
            .toList()
    }

    @Test
    fun `first sweep is the baseline and nothing is flagged new`() {
        val repo = DhcpMonitorRepositoryImpl(FakeDiscovery(listOf(listOf(node("192.168.1.1"), node("192.168.1.10")))))
        val statuses = runSweeps(repo, 1)

        assertTrue(statuses.none { it is MonitoringStatus.DeviceDetected })
        assertEquals(2, repo.activeDevices.value.size)
        assertTrue(repo.activeDevices.value.none { it.isNew })
        assertEquals(NetworkEventType.BASELINE, repo.events.value.single().type)
        assertEquals(1, repo.stats.value.sweepCount)
    }

    @Test
    fun `devices joining after the baseline are flagged and alerted`() {
        val repo = DhcpMonitorRepositoryImpl(
            FakeDiscovery(
                listOf(
                    listOf(node("192.168.1.1")),
                    listOf(node("192.168.1.1"), node("192.168.1.50", "B8:27:EB:00:00:01"))
                )
            )
        )
        val statuses = runSweeps(repo, 2)

        val detected = statuses.filterIsInstance<MonitoringStatus.DeviceDetected>().single()
        assertEquals("192.168.1.50", detected.device.ipAddress)
        val newDevice = repo.activeDevices.value.single { it.ipAddress == "192.168.1.50" }
        assertTrue(newDevice.isNew)
        assertEquals("Raspberry Pi", newDevice.manufacturer)
        assertEquals(NetworkEventType.JOINED, repo.events.value.first().type)

        repo.acknowledgeNewDevices()
        assertTrue(repo.activeDevices.value.none { it.isNew })
    }

    @Test
    fun `devices go offline only after consecutive misses and are reported on return`() {
        val both = listOf(node("192.168.1.1"), node("192.168.1.10"))
        val gatewayOnly = listOf(node("192.168.1.1"))
        val repo = DhcpMonitorRepositoryImpl(FakeDiscovery(listOf(both, gatewayOnly, gatewayOnly, both)))

        runSweeps(repo, 2)
        assertTrue("one miss is tolerated", repo.activeDevices.value.single { it.ipAddress == "192.168.1.10" }.isOnline)

        runSweeps(repo, 1)
        assertFalse(repo.activeDevices.value.single { it.ipAddress == "192.168.1.10" }.isOnline)
        assertEquals(NetworkEventType.LEFT, repo.events.value.first().type)

        runSweeps(repo, 1)
        assertTrue(repo.activeDevices.value.single { it.ipAddress == "192.168.1.10" }.isOnline)
        assertEquals(NetworkEventType.RETURNED, repo.events.value.first().type)
    }

    @Test
    fun `mac change on a known ip is flagged`() {
        val repo = DhcpMonitorRepositoryImpl(
            FakeDiscovery(
                listOf(
                    listOf(node("192.168.1.10", "AA:BB:CC:00:00:01")),
                    listOf(node("192.168.1.10", "AA:BB:CC:00:00:02"))
                )
            )
        )
        val statuses = runSweeps(repo, 2)

        assertTrue(statuses.any { it is MonitoringStatus.DeviceDetected && it.reason.startsWith("MAC changed") })
        assertEquals(NetworkEventType.MAC_CHANGED, repo.events.value.first().type)
        assertTrue(repo.activeDevices.value.single().isNew)
    }

    @Test
    fun `randomized macs are identified`() {
        val repo = DhcpMonitorRepositoryImpl(FakeDiscovery(listOf(listOf(node("192.168.1.10", "DA:A1:19:00:00:01")))))
        runSweeps(repo, 1)
        assertTrue(repo.activeDevices.value.single().isRandomizedMac)
    }

    @Test
    fun `reset baseline clears state`() {
        val repo = DhcpMonitorRepositoryImpl(FakeDiscovery(listOf(listOf(node("192.168.1.1")))))
        runSweeps(repo, 1)
        repo.resetBaseline()
        assertTrue(repo.activeDevices.value.isEmpty())
        assertTrue(repo.events.value.isEmpty())
        assertEquals(0, repo.stats.value.sweepCount)
    }
}
