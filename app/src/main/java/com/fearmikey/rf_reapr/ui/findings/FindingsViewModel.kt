package com.fearmikey.rf_reapr.ui.findings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.model.Finding
import com.fearmikey.rf_reapr.domain.model.FindingDraft
import com.fearmikey.rf_reapr.domain.model.FindingStatus
import com.fearmikey.rf_reapr.domain.model.FindingTools
import com.fearmikey.rf_reapr.domain.model.RiskLevel
import com.fearmikey.rf_reapr.domain.model.Evidence
import com.fearmikey.rf_reapr.domain.repository.EngagementRepository
import com.fearmikey.rf_reapr.domain.repository.EvidenceRepository
import com.fearmikey.rf_reapr.domain.service.EvidenceIntegrity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import com.fearmikey.rf_reapr.domain.repository.FindingRepository
import com.fearmikey.rf_reapr.domain.service.FindingsExporter
import com.fearmikey.rf_reapr.ui.components.ExportFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FindingsFilter(
    val query: String = "",
    val severities: Set<RiskLevel> = emptySet(),
    /** Default hides triaged-away findings so the list is the work queue. */
    val statuses: Set<FindingStatus> = setOf(FindingStatus.OPEN)
) {
    fun matches(f: Finding): Boolean =
        (severities.isEmpty() || f.severity in severities) &&
            (statuses.isEmpty() || f.status in statuses) &&
            (query.isBlank() || listOf(f.title, f.target, f.tool, f.detail).any { it.contains(query.trim(), ignoreCase = true) })
}

class FindingsViewModel(
    private val findingRepository: FindingRepository,
    private val engagementRepository: EngagementRepository,
    private val appVersion: String,
    private val evidenceRepository: EvidenceRepository? = null
) : ViewModel() {

    /** Evidence that can be attached: the active project's items, or everything when no project is active. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val candidateEvidence: StateFlow<List<Evidence>> = engagementRepository.activeProjectId
        .flatMapLatest { projectId ->
            when {
                evidenceRepository == null -> flowOf(emptyList())
                projectId != null -> evidenceRepository.getEvidenceForProject(projectId)
                else -> evidenceRepository.getAllEvidence()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun evidenceFor(findingId: Long): Flow<List<Evidence>> =
        evidenceRepository?.getEvidenceForFinding(findingId) ?: flowOf(emptyList())

    fun attachEvidence(evidence: Evidence, finding: Finding) =
        viewModelScope.launch { evidenceRepository?.linkToFinding(evidence.id, finding.id) }

    fun detachEvidence(evidence: Evidence) =
        viewModelScope.launch { evidenceRepository?.linkToFinding(evidence.id, null) }

    suspend fun verifyIntegrity(evidence: Evidence): EvidenceIntegrity =
        evidenceRepository?.verifyIntegrity(evidence) ?: EvidenceIntegrity.UNHASHED

    /** Unfiltered findings of the active project (the detail sheet reads from this). */
    val all: StateFlow<List<Finding>> = findingRepository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _filter = MutableStateFlow(FindingsFilter())
    val filter: StateFlow<FindingsFilter> = _filter.asStateFlow()

    val findings: StateFlow<List<Finding>> = combine(all, _filter) { list, f ->
        FindingsExporter.sortForReport(list.filter(f::matches))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalCount: StateFlow<Int> = all.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val projectName: StateFlow<String?> = engagementRepository.observeActiveProject().map { it?.name }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setQuery(q: String) = _filter.update { it.copy(query = q) }
    fun toggleSeverity(s: RiskLevel) = _filter.update { it.copy(severities = it.severities.toggle(s)) }
    fun toggleStatus(s: FindingStatus) = _filter.update { it.copy(statuses = it.statuses.toggle(s)) }

    fun setStatus(finding: Finding, status: FindingStatus) =
        viewModelScope.launch { findingRepository.setStatus(finding.id, status) }

    fun setNotes(finding: Finding, notes: String) =
        viewModelScope.launch { findingRepository.setNotes(finding.id, notes) }

    fun delete(finding: Finding) = viewModelScope.launch { findingRepository.delete(finding.id) }

    /** Deletes every finding of the active project (or the no-project bucket). */
    fun clearAll() = viewModelScope.launch {
        findingRepository.deleteAll(engagementRepository.activeProjectId.value)
    }

    fun addManual(title: String, target: String, severity: RiskLevel, detail: String, remediation: String) {
        if (title.isBlank() || target.isBlank()) return
        viewModelScope.launch {
            findingRepository.record(listOf(FindingDraft(FindingTools.MANUAL, target, severity, title, detail, remediation)))
        }
    }

    suspend fun buildExport(format: ExportFormat): String {
        return format.render(engagementRepository.buildReport(engagementRepository.activeProjectId.value, appVersion))
    }

    private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item
}
