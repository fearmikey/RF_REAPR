package com.fearmikey.rf_reapr.data.repository

import com.fearmikey.rf_reapr.data.db.dao.FindingDao
import com.fearmikey.rf_reapr.data.db.entity.FindingEntity
import com.fearmikey.rf_reapr.data.local.ActiveProjectStore
import com.fearmikey.rf_reapr.domain.model.Finding
import com.fearmikey.rf_reapr.domain.model.FindingDraft
import com.fearmikey.rf_reapr.domain.model.FindingStatus
import com.fearmikey.rf_reapr.domain.model.RiskLevel
import com.fearmikey.rf_reapr.domain.repository.FindingRepository
import com.fearmikey.rf_reapr.domain.service.FindingMerger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FindingRepositoryImpl(
    private val dao: FindingDao,
    private val activeProjectStore: ActiveProjectStore
) : FindingRepository {

    /** Serializes read-merge-write so concurrent tools can't race on the unique index. */
    private val writeLock = Mutex()

    override fun observe(projectId: String?): Flow<List<Finding>> =
        dao.observeForProject(projectId.orEmpty()).map { list -> list.map { it.toDomain() } }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeActive(): Flow<List<Finding>> =
        activeProjectStore.activeProjectId.flatMapLatest { observe(it) }

    override suspend fun getForProject(projectId: String?): List<Finding> =
        dao.getForProject(projectId.orEmpty()).map { it.toDomain() }

    override suspend fun record(drafts: List<FindingDraft>): FindingMerger.Plan {
        if (drafts.isEmpty()) return FindingMerger.Plan(emptyList(), emptyList(), emptyList())
        val projectId = activeProjectStore.activeProjectId.value
        return writeLock.withLock {
            val fingerprints = drafts.map { FindingMerger.fingerprint(it) }.distinct()
            val existing = fingerprints.chunked(500).flatMap { dao.getByFingerprints(projectId.orEmpty(), it) }
                .associate { it.fingerprint to it.toDomain() }
            val plan = FindingMerger.plan(projectId, drafts, existing, System.currentTimeMillis())
            if (!plan.isEmpty) dao.upsert(plan.inserts.map { it.toEntity() }, plan.updates.map { it.toEntity() })
            plan
        }
    }

    override suspend fun setStatus(id: Long, status: FindingStatus) = dao.updateStatus(id, status.name)

    override suspend fun setNotes(id: Long, notes: String?) = dao.updateNotes(id, notes?.ifBlank { null })

    override suspend fun delete(id: Long) = dao.delete(id)

    private fun FindingEntity.toDomain() = Finding(
        id = id,
        projectId = projectId.ifEmpty { null },
        fingerprint = fingerprint,
        tool = tool,
        target = target,
        severity = RiskLevel.entries.firstOrNull { it.name == severity } ?: RiskLevel.LOW,
        title = title,
        detail = detail,
        remediation = remediation,
        status = FindingStatus.parse(status),
        firstSeen = firstSeen,
        lastSeen = lastSeen,
        occurrences = occurrences,
        notes = notes
    )

    private fun Finding.toEntity() = FindingEntity(
        id = id,
        projectId = projectId.orEmpty(),
        fingerprint = fingerprint,
        tool = tool,
        target = target,
        severity = severity.name,
        title = title,
        detail = detail,
        remediation = remediation,
        status = status.name,
        firstSeen = firstSeen,
        lastSeen = lastSeen,
        occurrences = occurrences,
        notes = notes
    )
}
