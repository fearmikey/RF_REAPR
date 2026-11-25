package com.fearmikey.rf_reapr.domain.repository

import com.fearmikey.rf_reapr.domain.service.GattFinding
import com.fearmikey.rf_reapr.domain.service.GattServiceInfo
import kotlinx.coroutines.flow.Flow

enum class GattPhase { CONNECTING, DISCOVERING, READING, DONE, FAILED }

data class GattInspection(
    val address: String,
    val name: String? = null,
    val phase: GattPhase,
    /** Whether the phone was already paired with the device (findings only apply when it wasn't). */
    val bonded: Boolean = false,
    val services: List<GattServiceInfo> = emptyList(),
    val findings: List<GattFinding> = emptyList(),
    val error: String? = null
)

/** Read-only enumeration of a BLE device's GATT services. */
interface GattInspectorRepository {
    /** Connects, enumerates and disconnects, emitting progress. Cancelling the collection disconnects. */
    fun inspect(address: String): Flow<GattInspection>
}
