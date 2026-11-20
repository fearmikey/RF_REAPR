package com.fearmikey.rf_reapr.domain.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class CaptureMode { STANDARD_VPN, ROOT_TCPDUMP }

data class CaptureState(
    val isRunning: Boolean = false,
    val mode: CaptureMode? = null,
    val startedAt: Long? = null,
    val filePath: String? = null,
    /** Packet-level stats are only available in VPN mode (tcpdump writes straight to file). */
    val packets: Long = 0,
    val bytes: Long = 0,
    val fileSizeBytes: Long? = null,
    val protocolCounts: Map<TransportProtocol, Long> = emptyMap(),
    val topDestinations: List<Pair<String, Long>> = emptyList(),
    val dnsQueries: List<String> = emptyList(),
    val error: String? = null
)

/**
 * Process-wide capture state. Written by the capture service / root runner and observed by
 * the UI, so the screen always reflects what is really running (including after the
 * ViewModel is recreated or the capture failed to start).
 */
object PacketCaptureSession {
    private val _state = MutableStateFlow(CaptureState())
    val state: StateFlow<CaptureState> = _state.asStateFlow()

    fun started(mode: CaptureMode, filePath: String?) {
        _state.value = CaptureState(isRunning = true, mode = mode, startedAt = System.currentTimeMillis(), filePath = filePath)
    }

    fun publish(aggregator: CaptureAggregator) {
        _state.update {
            it.copy(
                packets = aggregator.packets,
                bytes = aggregator.bytes,
                protocolCounts = aggregator.protocolCounts(),
                topDestinations = aggregator.topDestinations(),
                dnsQueries = aggregator.recentDnsQueries()
            )
        }
    }

    fun updateFileSize(bytes: Long) {
        _state.update { it.copy(fileSizeBytes = bytes) }
    }

    /** Marks the session stopped, keeping the final stats on screen. */
    fun stopped(finalFileSize: Long? = null) {
        _state.update { it.copy(isRunning = false, fileSizeBytes = finalFileSize ?: it.fileSizeBytes) }
    }

    fun failed(message: String) {
        _state.update { it.copy(isRunning = false, error = message) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
