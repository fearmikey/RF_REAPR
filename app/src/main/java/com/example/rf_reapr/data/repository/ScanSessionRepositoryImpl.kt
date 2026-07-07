package com.example.rf_reapr.data.repository

import com.example.rf_reapr.data.db.dao.NetworkDao
import com.example.rf_reapr.data.db.dao.ScanSessionDao
import com.example.rf_reapr.data.db.entity.NetworkNodeEntity
import com.example.rf_reapr.data.db.entity.ScanSessionEntity
import com.example.rf_reapr.domain.model.NetworkNode
import com.example.rf_reapr.domain.repository.ScanSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ScanSessionRepositoryImpl(
    private val networkDao: NetworkDao,
    private val scanSessionDao: ScanSessionDao
) : ScanSessionRepository {

    override fun getAllPersistedNodes(): Flow<List<NetworkNode>> {
        return networkDao.getAllNodes().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun saveDiscoveredNodes(nodes: List<NetworkNode>, networkName: String?, gatewayIp: String?) {
        val entities = nodes.map { newNode ->
            val existing = networkDao.getNodeByIp(newNode.ipAddress)
            if (existing != null) {
                // Preserve ports and risk level if we already have them
                existing.copy(
                    hostname = newNode.hostname ?: existing.hostname,
                    macAddress = newNode.macAddress ?: existing.macAddress,
                    lastSeen = System.currentTimeMillis()
                )
            } else {
                newNode.toEntity()
            }
        }
        networkDao.insertNodes(entities)
        
        val session = ScanSessionEntity(
            networkName = networkName,
            gatewayIp = gatewayIp
        )
        scanSessionDao.saveSessionWithNodes(session, nodes.map { it.ipAddress })
    }

    override suspend fun updateNodeDetails(node: NetworkNode) {
        networkDao.insertNode(node.toEntity())
    }

    override suspend fun getNodeByIp(ip: String): NetworkNode? {
        return networkDao.getNodeByIp(ip)?.toDomain()
    }

    override suspend fun clearAllData() {
        networkDao.deleteAllNodes()
        scanSessionDao.deleteAllSessions()
        scanSessionDao.deleteAllCrossRefs()
    }

    private fun NetworkNodeEntity.toDomain(): NetworkNode {
        return NetworkNode(
            id = ipAddress,
            ipAddress = ipAddress,
            macAddress = macAddress,
            hostname = hostname,
            riskLevel = riskLevel,
            openPorts = openPorts,
            deviceType = deviceType,
            parentId = parentId
        )
    }

    private fun NetworkNode.toEntity(): NetworkNodeEntity {
        return NetworkNodeEntity(
            ipAddress = ipAddress,
            macAddress = macAddress,
            hostname = hostname,
            riskLevel = riskLevel,
            openPorts = openPorts,
            deviceType = deviceType,
            parentId = parentId
        )
    }
}
