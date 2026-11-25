package com.fearmikey.rf_reapr.data.repository

import com.fearmikey.rf_reapr.data.db.dao.AuditRunDao
import com.fearmikey.rf_reapr.data.db.dao.EventLogDao
import com.fearmikey.rf_reapr.domain.model.AuditRun
import com.fearmikey.rf_reapr.domain.model.EventLog
import com.fearmikey.rf_reapr.domain.model.Evidence
import com.fearmikey.rf_reapr.domain.service.AuditRunCodec
import com.fearmikey.rf_reapr.domain.service.TimelineBuilder
import com.fearmikey.rf_reapr.domain.service.TimelineEvent
import com.fearmikey.rf_reapr.data.db.dao.EvidenceDao
import com.fearmikey.rf_reapr.data.db.dao.ScanSessionDao
import com.fearmikey.rf_reapr.data.db.entity.EvidenceProjectEntity
import com.fearmikey.rf_reapr.data.db.entity.NetworkNodeEntity
import com.fearmikey.rf_reapr.data.db.entity.ScanSessionWithNodes
import com.fearmikey.rf_reapr.data.local.ActiveProjectStore
import com.fearmikey.rf_reapr.domain.model.EvidenceProject
import com.fearmikey.rf_reapr.domain.model.FindingStatus
import com.fearmikey.rf_reapr.domain.model.NetworkNode
import com.fearmikey.rf_reapr.domain.model.ReportScanSession
import com.fearmikey.rf_reapr.domain.repository.EngagementRepository
import com.fearmikey.rf_reapr.domain.repository.EngagementStats
import com.fearmikey.rf_reapr.domain.repository.FindingRepository
import com.fearmikey.rf_reapr.domain.service.EngagementReport
import com.fearmikey.rf_reapr.domain.service.EvidenceHasher
import com.fearmikey.rf_reapr.domain.service.ExportEvidence
import com.fearmikey.rf_reapr.domain.service.ExportHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.Date
import java.util.UUID

class EngagementRepositoryImpl(
    private val evidenceDao: EvidenceDao,
    private val scanSessionDao: ScanSessionDao,
    private val eventLogDao: EventLogDao,
    private val findingRepository: FindingRepository,
    private val activeProjectStore: ActiveProjectStore,
    private val auditRunDao: AuditRunDao
) : EngagementRepository {

    override fun observeTimeline(projectId: String?): Flow<List<TimelineEvent>> {
        val logs = if (projectId == null) eventLogDao.getAllLogs() else eventLogDao.getLogsForProject(projectId)
        val evidence = if (projectId == null) flowOf(emptyList()) else evidenceDao.getEvidenceForProject(projectId)
        return combine(
            logs,
            findingRepository.observe(projectId),
            evidence,
            observeScanSessions(projectId),
            auditRunDao.observeForProject(projectId.orEmpty())
        ) { logList, findings, evidenceList, scans, runs ->
            TimelineBuilder.build(
                logs = logList.map { EventLog(it.id, it.type, it.timestamp, it.summary, "", it.projectId) },
                findings = findings,
                evidence = evidenceList.map { e ->
                    Evidence(
                        id = e.id, filePath = e.filePath, timestamp = Date(e.timestamp), latitude = e.latitude,
                        longitude = e.longitude, notes = e.notes, sha256 = e.sha256, findingId = e.findingId
                    )
                },
                scans = scans,
                audits = runs.map { r ->
                    AuditRun(
                        id = r.id, projectId = r.projectId.ifEmpty { null }, startedAt = r.startedAt, finishedAt = r.finishedAt,
                        networkLabel = r.networkLabel, grade = r.grade, score = r.score, completed = r.completed,
                        checks = AuditRunCodec.decodeChecks(r.checks), hostCount = r.hostCount,
                        findings = AuditRunCodec.decode(r.findingsJson), reportMarkdown = ""
                    )
                }
            )
        }
    }

    override val activeProjectId: StateFlow<String?> get() = activeProjectStore.activeProjectId

    override fun observeProjects(): Flow<List<EvidenceProject>> =
        evidenceDao.getAllProjects().map { list -> list.map { it.toDomain() } }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeActiveProject(): Flow<EvidenceProject?> =
        activeProjectStore.activeProjectId.flatMapLatest { id ->
            if (id == null) flowOf(null)
            else evidenceDao.observeProject(id).map { it?.takeUnless { p -> p.isDeleted }?.toDomain() }
        }

    override fun setActiveProject(projectId: String?) = activeProjectStore.setActive(projectId)

    override suspend fun createProject(name: String, description: String, scope: String): EvidenceProject {
        val entity = EvidenceProjectEntity(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            createdAt = System.currentTimeMillis(),
            description = description.trim(),
            scope = scope.trim()
        )
        evidenceDao.insertProject(entity)
        return entity.toDomain()
    }

    override suspend fun updateScope(projectId: String, scope: String) =
        evidenceDao.updateProjectScope(projectId, scope.trim())

    override fun observeStats(projectId: String?): Flow<EngagementStats> {
        val logs = if (projectId == null) eventLogDao.getAllLogs() else eventLogDao.getLogsForProject(projectId)
        val evidence = if (projectId == null) flowOf(0) else evidenceDao.countEvidenceForProject(projectId)
        return combine(
            findingRepository.observe(projectId),
            observeScanSessions(projectId),
            logs,
            evidence
        ) { findings, scans, logList, evidenceCount ->
            val open = findings.filter { it.status == FindingStatus.OPEN }
            EngagementStats(
                openBySeverity = open.groupingBy { it.severity }.eachCount(),
                totalFindings = findings.size,
                scans = scans.size,
                logs = logList.size,
                evidence = evidenceCount
            )
        }
    }

    override fun observeScanSessions(projectId: String?): Flow<List<ReportScanSession>> {
        val source = if (projectId == null) scanSessionDao.getSessionsWithNodes()
        else scanSessionDao.getSessionsWithNodesForProject(projectId)
        return source.map { list -> list.map { it.toReportSession() } }
    }

    override suspend fun buildReport(projectId: String?, appVersion: String): EngagementReport {
        val project = projectId?.let { evidenceDao.getProjectById(it) }
        val sessions = observeScanSessions(projectId).first()
        // Latest known state of every host seen in this project's scans.
        val hosts = sessions.flatMap { it.nodes }
            .associateBy { it.ipAddress }
            .values
            .sortedWith(compareBy { ipSortKey(it.ipAddress) })
            .map { node ->
                ExportHost(
                    ip = node.ipAddress,
                    mac = node.macAddress,
                    hostname = node.hostname,
                    deviceType = node.deviceType.name,
                    risk = node.riskLevel,
                    openPorts = node.openPorts.map { it.port }.sorted(),
                    lastSeen = null
                )
            }
        val logs = if (projectId == null) eventLogDao.getAllLogs().first() else eventLogDao.getLogsForProject(projectId).first()
        val evidence = if (projectId == null) 0 else evidenceDao.countEvidenceForProject(projectId).first()
        val findings = findingRepository.getForProject(projectId)
        val findingIds = findings.map { it.id }.toSet()
        // Re-check each linked item's capture hash so the export states its integrity as of now.
        val evidenceByFinding = withContext(Dispatchers.IO) {
            evidenceDao.getLinkedEvidence()
                .filter { it.findingId in findingIds }
                .groupBy({ it.findingId!! }) { e ->
                    val file = File(e.filePath)
                    ExportEvidence(file.name, e.timestamp, e.sha256, EvidenceHasher.verify(file, e.sha256), e.notes)
                }
        }
        return EngagementReport(
            projectName = project?.name ?: "Unassigned work",
            description = project?.description.orEmpty(),
            scope = project?.scope.orEmpty(),
            generatedAt = System.currentTimeMillis(),
            findings = findings,
            hosts = hosts,
            scanCount = sessions.size,
            logCount = logs.size,
            evidenceCount = evidence,
            appVersion = appVersion,
            evidenceByFinding = evidenceByFinding
        )
    }

    private fun ipSortKey(ip: String): Long =
        ip.split('.').takeIf { it.size == 4 }?.mapNotNull { it.toLongOrNull() }?.takeIf { it.size == 4 }
            ?.fold(0L) { acc, o -> (acc shl 8) or o } ?: Long.MAX_VALUE

    private fun ScanSessionWithNodes.toReportSession() = ReportScanSession(
        id = session.id,
        timestamp = session.timestamp,
        networkName = session.networkName,
        gatewayIp = session.gatewayIp,
        nodes = nodes.map { it.toDomain() },
        projectId = session.projectId
    )

    private fun NetworkNodeEntity.toDomain() = NetworkNode(
        id = ipAddress,
        ipAddress = ipAddress,
        macAddress = macAddress,
        hostname = hostname,
        riskLevel = riskLevel,
        openPorts = openPorts,
        deviceType = deviceType,
        parentId = parentId,
        snmpData = snmpData
    )

    private fun EvidenceProjectEntity.toDomain() = EvidenceProject(
        id = id,
        name = name,
        createdAt = Date(createdAt),
        description = description,
        showGps = showGps,
        showDate = showDate,
        showTimestamp = showTimestamp,
        isDeleted = isDeleted,
        deletedAt = deletedAt?.let { Date(it) },
        scope = scope
    )
}
