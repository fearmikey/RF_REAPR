package com.fearmikey.rf_reapr.data.repository

import com.fearmikey.rf_reapr.data.db.dao.KnownDeviceDao
import com.fearmikey.rf_reapr.data.db.entity.KnownDeviceEntity
import com.fearmikey.rf_reapr.data.local.ActiveProjectStore
import com.fearmikey.rf_reapr.domain.repository.KnownDeviceRepository
import com.fearmikey.rf_reapr.domain.service.KnownDevice
import com.fearmikey.rf_reapr.domain.service.KnownDeviceMatcher
import com.fearmikey.rf_reapr.domain.service.ObservedDevice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

class KnownDeviceRepositoryImpl(
    private val dao: KnownDeviceDao,
    private val activeProjectStore: ActiveProjectStore
) : KnownDeviceRepository {

    private val projectKey: String get() = activeProjectStore.activeProjectId.value.orEmpty()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeActive(): Flow<List<KnownDevice>> =
        activeProjectStore.activeProjectId.flatMapLatest { projectId ->
            dao.observeForProject(projectId.orEmpty()).map { list -> list.map { it.toDomain() } }
        }

    override suspend fun getActive(): List<KnownDevice> = dao.getForProject(projectKey).map { it.toDomain() }

    override suspend fun add(devices: List<ObservedDevice>): Int {
        val project = projectKey
        val now = System.currentTimeMillis()
        val entities = devices.mapNotNull { device ->
            val mac = KnownDeviceMatcher.normalize(device.mac) ?: return@mapNotNull null
            KnownDeviceEntity(
                projectId = project,
                mac = mac,
                label = KnownDeviceMatcher.defaultLabel(device),
                lastIp = device.ip,
                addedAt = now
            )
        }.distinctBy { it.mac }
        if (entities.isEmpty()) return 0
        // IGNORE conflict strategy returns -1 for rows that already existed.
        return dao.insertAll(entities).count { it != -1L }
    }

    override suspend fun update(id: Long, label: String, notes: String?) =
        dao.update(id, label.trim().ifBlank { "Device" }, notes?.trim()?.ifBlank { null })

    override suspend fun remove(id: Long) = dao.delete(id)

    override suspend fun removeByMac(mac: String) {
        KnownDeviceMatcher.normalize(mac)?.let { dao.deleteByMac(projectKey, it) }
    }

    private fun KnownDeviceEntity.toDomain() = KnownDevice(
        id = id,
        projectId = projectId.ifEmpty { null },
        mac = mac,
        label = label,
        lastIp = lastIp,
        notes = notes,
        addedAt = addedAt
    )
}
