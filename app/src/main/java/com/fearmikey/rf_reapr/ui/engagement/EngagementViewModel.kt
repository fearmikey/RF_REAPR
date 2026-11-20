package com.fearmikey.rf_reapr.ui.engagement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.model.EvidenceProject
import com.fearmikey.rf_reapr.domain.model.ReportScanSession
import com.fearmikey.rf_reapr.domain.repository.EngagementRepository
import com.fearmikey.rf_reapr.domain.repository.EngagementStats
import com.fearmikey.rf_reapr.domain.service.FindingsExporter
import com.fearmikey.rf_reapr.domain.service.ScanDiff
import com.fearmikey.rf_reapr.domain.service.ScopeMatcher
import com.fearmikey.rf_reapr.ui.components.ExportFormat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Host details shown in the scan comparison. */
data class DiffHost(val ip: String, val hostname: String?, val mac: String?)

data class ScanComparison(
    val baseline: ReportScanSession,
    val latest: ReportScanSession,
    val added: List<DiffHost>,
    val removed: List<DiffHost>,
    val unchangedCount: Int
)

@OptIn(ExperimentalCoroutinesApi::class)
class EngagementViewModel(
    private val repository: EngagementRepository,
    private val appVersion: String
) : ViewModel() {

    val projects: StateFlow<List<EvidenceProject>> = repository.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeProject: StateFlow<EvidenceProject?> = repository.observeActiveProject()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val stats: StateFlow<EngagementStats> = repository.activeProjectId
        .flatMapLatest { repository.observeStats(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EngagementStats())

    val sessions: StateFlow<List<ReportScanSession>> = repository.activeProjectId
        .flatMapLatest { repository.observeScanSessions(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** User-picked (baselineId, latestId); null = default to the two most recent sessions. */
    private val _diffSelection = MutableStateFlow<Pair<Long, Long>?>(null)

    val comparison: StateFlow<ScanComparison?> = combine(sessions, _diffSelection) { list, selection ->
        if (list.size < 2) return@combine null
        val byId = list.associateBy { it.id }
        val latest = selection?.second?.let(byId::get) ?: list[0]
        val baseline = selection?.first?.let(byId::get) ?: list[1]
        val diff = ScanDiff.compare(baseline.nodes.map { it.ipAddress }, latest.nodes.map { it.ipAddress })
        val nodeInfo = (baseline.nodes + latest.nodes).associateBy { it.ipAddress }
        fun host(ip: String) = nodeInfo[ip].let { DiffHost(ip, it?.hostname, it?.macAddress) }
        ScanComparison(baseline, latest, diff.added.map(::host), diff.removed.map(::host), diff.unchanged.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _scopeDraft = MutableStateFlow<String?>(null)
    /** Scope text being edited; null = not edited (show the saved scope). */
    val scopeDraft: StateFlow<String?> = _scopeDraft

    val scopeInfo: StateFlow<ScopeMatcher> = combine(_scopeDraft, activeProject) { draft, project ->
        ScopeMatcher.parse(draft ?: project?.scope)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScopeMatcher.EMPTY)

    private val _messages = MutableStateFlow<String?>(null)
    val messages: StateFlow<String?> = _messages

    fun setActive(projectId: String?) {
        _scopeDraft.value = null
        _diffSelection.value = null
        repository.setActiveProject(projectId)
    }

    fun createProject(name: String, description: String, scope: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val project = repository.createProject(name, description, scope)
            setActive(project.id)
            _messages.value = "Project \"${project.name}\" created and set active"
        }
    }

    fun editScope(text: String) = _scopeDraft.update { text }

    fun saveScope() {
        val project = activeProject.value ?: return
        val text = _scopeDraft.value ?: return
        viewModelScope.launch {
            repository.updateScope(project.id, text)
            _scopeDraft.value = null
            val parsed = ScopeMatcher.parse(text)
            _messages.value = if (parsed.invalidLines.isEmpty()) "Scope saved (${parsed.ruleCount} entries)"
            else "Scope saved; ${parsed.invalidLines.size} line(s) couldn't be parsed and are ignored"
        }
    }

    fun discardScopeEdit() {
        _scopeDraft.value = null
    }

    fun selectComparison(baselineId: Long, latestId: Long) {
        _diffSelection.value = baselineId to latestId
    }

    suspend fun buildExport(format: ExportFormat): String {
        val report = repository.buildReport(repository.activeProjectId.value, appVersion)
        return when (format) {
            ExportFormat.MARKDOWN -> FindingsExporter.toMarkdown(report)
            ExportFormat.JSON -> FindingsExporter.toJson(report)
            ExportFormat.CSV -> FindingsExporter.toCsv(report.findings)
            ExportFormat.HOSTS_CSV -> FindingsExporter.hostsToCsv(report.hosts)
        }
    }

    fun showMessage(text: String) {
        _messages.value = text
    }

    fun consumeMessage() {
        _messages.value = null
    }

    val activeProjectName: StateFlow<String> = activeProject.map { it?.name ?: "No project" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "No project")
}
