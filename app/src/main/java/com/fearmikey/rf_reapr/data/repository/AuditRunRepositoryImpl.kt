package com.fearmikey.rf_reapr.data.repository

import com.fearmikey.rf_reapr.data.db.dao.AuditRunDao
import com.fearmikey.rf_reapr.data.db.entity.AuditRunEntity
import com.fearmikey.rf_reapr.data.local.ActiveProjectStore
import com.fearmikey.rf_reapr.domain.model.AuditRun
import com.fearmikey.rf_reapr.domain.model.AuditRunState
import com.fearmikey.rf_reapr.domain.model.AuditStepStatus
import com.fearmikey.rf_reapr.domain.repository.AuditRunRepository
import com.fearmikey.rf_reapr.domain.service.AuditReportBuilder
import com.fearmikey.rf_reapr.domain.service.AuditRunCodec
import com.fearmikey.rf_reapr.domain.service.AuditScoring
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

class AuditRunRepositoryImpl(
    private val dao: AuditRunDao,
    private val activeProjectStore: ActiveProjectStore,
    private val appVersion: String
) : AuditRunRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeActive(): Flow<List<AuditRun>> =
        activeProjectStore.activeProjectId.flatMapLatest { projectId ->
            dao.observeForProject(projectId.orEmpty()).map { list -> list.map { it.toDomain() } }
        }

    override suspend fun save(state: AuditRunState): AuditRun? {
        val startedAt = state.startedAt ?: return null
        val finished = state.steps.filter { it.status == AuditStepStatus.DONE }
        if (finished.isEmpty()) return null
        val score = AuditScoring.score(state.findings)
        val entity = AuditRunEntity(
            projectId = activeProjectStore.activeProjectId.value.orEmpty(),
            startedAt = startedAt,
            finishedAt = state.finishedAt ?: System.currentTimeMillis(),
            networkLabel = state.networkLabel,
            grade = score.grade,
            score = score.score,
            completed = state.steps.none { it.status == AuditStepStatus.FAILED || it.status == AuditStepStatus.CANCELLED },
            checks = AuditRunCodec.encodeChecks(finished.map { it.check }),
            hostCount = state.hosts.size,
            findingsJson = AuditRunCodec.encode(AuditRunCodec.snapshot(state.findings)),
            reportMarkdown = AuditReportBuilder.markdown(state, appVersion)
        )
        return entity.copy(id = dao.insert(entity)).toDomain()
    }

    override suspend fun delete(id: Long) = dao.delete(id)

    private fun AuditRunEntity.toDomain() = AuditRun(
        id = id,
        projectId = projectId.ifEmpty { null },
        startedAt = startedAt,
        finishedAt = finishedAt,
        networkLabel = networkLabel,
        grade = grade,
        score = score,
        completed = completed,
        checks = AuditRunCodec.decodeChecks(checks),
        hostCount = hostCount,
        findings = AuditRunCodec.decode(findingsJson),
        reportMarkdown = reportMarkdown
    )
}
