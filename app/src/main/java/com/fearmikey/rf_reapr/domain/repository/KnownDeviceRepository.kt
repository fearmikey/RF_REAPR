package com.fearmikey.rf_reapr.domain.repository

import com.fearmikey.rf_reapr.domain.service.KnownDevice
import com.fearmikey.rf_reapr.domain.service.ObservedDevice
import kotlinx.coroutines.flow.Flow

/** Per-project list of devices that are expected on the network (matched by MAC). */
interface KnownDeviceRepository {
    /** Known devices of whichever project is currently active. */
    fun observeActive(): Flow<List<KnownDevice>>

    suspend fun getActive(): List<KnownDevice>

    /** Adds devices that have a usable MAC and aren't listed yet. Returns how many were added. */
    suspend fun add(devices: List<ObservedDevice>): Int

    suspend fun update(id: Long, label: String, notes: String?)
    suspend fun remove(id: Long)

    /** Removes [mac] from the active project's list (no-op if absent). */
    suspend fun removeByMac(mac: String)
}
