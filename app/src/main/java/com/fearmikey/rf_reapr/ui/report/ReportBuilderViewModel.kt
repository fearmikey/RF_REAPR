package com.fearmikey.rf_reapr.ui.report

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.model.*
import com.fearmikey.rf_reapr.domain.repository.ReportRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class ReportBuilderViewModel(
    private val repository: ReportRepository,
    /** Project the builder starts scoped to (the active engagement), or null for all data. */
    initialProjectId: String? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportBuilderUiState(projectFilterId = initialProjectId))
    val uiState: StateFlow<ReportBuilderUiState> = _uiState.asStateFlow()

    private val projectFilter = MutableStateFlow(initialProjectId)

    private val externalReconTypes = listOf("SUBDOMAIN", "CLOUD", "WEBSITE", "TLS", "SHODAN", "HIBP", "WEB_SUBDOMAIN", "WEB_CLOUD", "WEB_TLS")
    private val ownSectionTypes = listOf("WIFI", "IPERF", "SNMP", "BLE") + externalReconTypes

    init {
        viewModelScope.launch {
            combine(
                repository.getAvailableScanSessions(),
                repository.getAvailableEvidenceProjects(),
                repository.getAvailableComplianceFrameworks(),
                repository.getAvailableLogs(),
                projectFilter
            ) { allScans, projects, compliance, allLogs, projectId ->
                // A project that was deleted while it was the filter falls back to "all data".
                val filterId = projectId?.takeIf { id -> projects.any { it.project.id == id } }
                if (filterId != projectId) projectFilter.value = filterId
                val scans = allScans.filter { filterId == null || it.projectId == filterId }
                val logs = allLogs.filter { filterId == null || it.projectId == filterId }
                _uiState.update { state ->
                    state.copy(
                        projectFilterId = filterId,
                        availableScans = scans,
                        availableProjects = projects,
                        availableCompliance = compliance,
                        availableLogs = logs.filter { it.type !in ownSectionTypes },
                        availableWifiScans = logs.filter { it.type == "WIFI" },
                        availableBleScans = logs.filter { it.type == "BLE" },
                        availableIperfTests = logs.filter { it.type == "IPERF" },
                        availableSnmpResults = logs.filter { it.type == "SNMP" },
                        availableExternalRecon = logs.filter { it.type in externalReconTypes }
                    ).dropUnavailableSelections()
                }
            }.collect()
        }
        viewModelScope.launch {
            projectFilter.flatMapLatest { repository.observeFindings(it) }.collect { findings ->
                _uiState.update { it.copy(availableFindings = findings) }
            }
        }
    }

    /** Selections made under another project filter must not leak into the report. */
    private fun ReportBuilderUiState.dropUnavailableSelections(): ReportBuilderUiState = copy(
        selectedScanIds = selectedScanIds intersect availableScans.map { it.id }.toSet(),
        selectedLogIds = selectedLogIds intersect availableLogs.map { it.id }.toSet(),
        selectedWifiScanIds = selectedWifiScanIds intersect availableWifiScans.map { it.id }.toSet(),
        selectedBleScanIds = selectedBleScanIds intersect availableBleScans.map { it.id }.toSet(),
        selectedIperfTestIds = selectedIperfTestIds intersect availableIperfTests.map { it.id }.toSet(),
        selectedSnmpLogIds = selectedSnmpLogIds intersect availableSnmpResults.map { it.id }.toSet(),
        selectedExternalReconIds = selectedExternalReconIds intersect availableExternalRecon.map { it.id }.toSet()
    )

    /** Scope the report to one engagement (null = everything on the device). */
    fun setProjectFilter(projectId: String?) {
        projectFilter.value = projectId
        _uiState.update { state ->
            state.copy(
                projectFilterId = projectId,
                // Picking an engagement implies its evidence gallery belongs in the report.
                selectedProjectIds = if (projectId != null) setOf(projectId) else emptySet()
            )
        }
    }

    fun setIncludeFindings(value: Boolean) = _uiState.update { it.copy(includeFindings = value) }

    fun setIncludeResolvedFindings(value: Boolean) = _uiState.update { it.copy(includeResolvedFindings = value) }

    fun autoSelectAndGenerate(format: ReportFormat = ReportFormat.PDF, startTime: Long? = null) {
        viewModelScope.launch {
            // Wait for data to load if needed (Room flows take a moment to emit after insertion)
            kotlinx.coroutines.delay(2.seconds)
            
            _uiState.update { state ->
                state.copy(
                    selectedScanIds = state.availableScans.asSequence()
                        .filter { (startTime == null) || (it.timestamp >= startTime) }
                        .map { it.id }.toSet(),
                    selectedLogIds = state.availableLogs.asSequence()
                        .filter { (startTime == null) || (it.timestamp >= startTime) }
                        .map { it.id }.toSet(),
                    selectedWifiScanIds = state.availableWifiScans.asSequence()
                        .filter { (startTime == null) || (it.timestamp >= startTime) }
                        .map { it.id }.toSet(),
                    selectedBleScanIds = state.availableBleScans.asSequence()
                        .filter { (startTime == null) || (it.timestamp >= startTime) }
                        .map { it.id }.toSet(),
                    selectedIperfTestIds = state.availableIperfTests.asSequence()
                        .filter { (startTime == null) || (it.timestamp >= startTime) }
                        .map { it.id }.toSet(),
                    selectedSnmpLogIds = state.availableSnmpResults.asSequence()
                        .filter { (startTime == null) || (it.timestamp >= startTime) }
                        .map { it.id }.toSet(),
                    selectedExternalReconIds = state.availableExternalRecon.asSequence()
                        .filter { (startTime == null) || (it.timestamp >= startTime) }
                        .map { it.id }.toSet(),
                    title = "Automated Recon Report",
                    executiveSummary = "This report was automatically generated following an OSINT Recon workflow."
                )
            }
            generateReport(format)
        }
    }

    fun updateTitle(title: String) {
        _uiState.update { it.copy(title = title) }
    }

    fun updateAuditorName(name: String) {
        _uiState.update { it.copy(auditorName = name) }
    }

    fun updateSummary(summary: String) {
        _uiState.update { it.copy(executiveSummary = summary) }
    }

    private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item

    fun toggleScan(scanId: Long) = _uiState.update { it.copy(selectedScanIds = it.selectedScanIds.toggle(scanId)) }

    fun toggleProject(projectId: String) = _uiState.update { it.copy(selectedProjectIds = it.selectedProjectIds.toggle(projectId)) }

    fun toggleCompliance(frameworkId: String) =
        _uiState.update { it.copy(selectedComplianceIds = it.selectedComplianceIds.toggle(frameworkId)) }

    fun toggleLog(logId: Long) = _uiState.update { it.copy(selectedLogIds = it.selectedLogIds.toggle(logId)) }

    fun toggleWifiScan(logId: Long) = _uiState.update { it.copy(selectedWifiScanIds = it.selectedWifiScanIds.toggle(logId)) }

    fun toggleBleScan(logId: Long) = _uiState.update { it.copy(selectedBleScanIds = it.selectedBleScanIds.toggle(logId)) }

    fun toggleIperfTest(logId: Long) = _uiState.update { it.copy(selectedIperfTestIds = it.selectedIperfTestIds.toggle(logId)) }

    fun toggleSnmpResult(logId: Long) = _uiState.update { it.copy(selectedSnmpLogIds = it.selectedSnmpLogIds.toggle(logId)) }

    fun toggleExternalRecon(logId: Long) =
        _uiState.update { it.copy(selectedExternalReconIds = it.selectedExternalReconIds.toggle(logId)) }

    fun generateReport(format: ReportFormat) {
        val state = _uiState.value
        if (state.isGenerating) return

        _uiState.update { it.copy(isGenerating = true, error = null, generatedUri = null) }

        viewModelScope.launch {
            val result = runCatching { buildReportData(state) }.fold(
                onSuccess = { reportData ->
                    when (format) {
                        ReportFormat.PDF -> repository.generatePdf(reportData)
                        ReportFormat.DOCX -> repository.generateDocx(reportData)
                    }
                },
                onFailure = { Result.failure(it) }
            )

            _uiState.update { 
                it.copy(
                    isGenerating = false,
                    generatedUri = result.getOrNull(),
                    error = result.exceptionOrNull()?.let { e -> e.message ?: "Report generation failed" }
                )
            }
        }
    }

    private suspend fun buildReportData(state: ReportBuilderUiState): ReportData {
        val selectedScans = state.availableScans.filter { it.id in state.selectedScanIds }

        // Fetch full evidence projects with items
        val selectedProjects = state.selectedProjectIds.map { projectId ->
            repository.getFullEvidenceProject(projectId)
        }

        // Fetch full compliance findings (static controls + results)
        val selectedCompliance = state.selectedComplianceIds.mapNotNull { frameworkId ->
            val framework = ComplianceFramework.entries.find { it.name == frameworkId } ?: return@mapNotNull null
            ReportComplianceFramework(framework, repository.getFullComplianceFindings(frameworkId))
        }

        val findings = if (state.includeFindings) repository.buildReportFindings(state.reportFindings) else emptyList()
        val project = state.availableProjects.firstOrNull { it.project.id == state.projectFilterId }?.project

        return ReportData(
            title = state.title,
            auditorName = state.auditorName,
            executiveSummary = state.executiveSummary,
            networkScans = selectedScans,
            wifiScans = state.availableWifiScans.filter { it.id in state.selectedWifiScanIds },
            bleScans = state.availableBleScans.filter { it.id in state.selectedBleScanIds },
            iperfTests = state.availableIperfTests.filter { it.id in state.selectedIperfTestIds },
            snmpResults = state.availableSnmpResults.filter { it.id in state.selectedSnmpLogIds },
            evidenceProjects = selectedProjects,
            complianceFindings = selectedCompliance,
            eventLogs = state.availableLogs.filter { it.id in state.selectedLogIds } +
                        state.availableExternalRecon.filter { it.id in state.selectedExternalReconIds },
            projectName = project?.name,
            scope = project?.scope.orEmpty(),
            findings = findings
        )
    }

    fun clearResult() {
        _uiState.update { it.copy(generatedUri = null, error = null) }
    }
}

data class ReportBuilderUiState(
    val title: String = "Security Audit Report",
    val auditorName: String = "Lead Auditor",
    val executiveSummary: String = "",
    /** Engagement the lists below are filtered to; null = all data on the device. */
    val projectFilterId: String? = null,
    val includeFindings: Boolean = true,
    /** Also list findings triaged as Fixed / False positive. */
    val includeResolvedFindings: Boolean = false,
    val availableFindings: List<Finding> = emptyList(),
    val availableScans: List<ReportScanSession> = emptyList(),
    val availableProjects: List<ReportEvidenceProject> = emptyList(),
    val availableCompliance: List<ReportComplianceFramework> = emptyList(),
    val availableLogs: List<EventLog> = emptyList(),
    val availableWifiScans: List<EventLog> = emptyList(),
    val availableBleScans: List<EventLog> = emptyList(),
    val availableIperfTests: List<EventLog> = emptyList(),
    val availableSnmpResults: List<EventLog> = emptyList(),
    val availableExternalRecon: List<EventLog> = emptyList(),
    val selectedScanIds: Set<Long> = emptySet(),
    val selectedProjectIds: Set<String> = emptySet(),
    val selectedComplianceIds: Set<String> = emptySet(),
    val selectedLogIds: Set<Long> = emptySet(),
    val selectedWifiScanIds: Set<Long> = emptySet(),
    val selectedBleScanIds: Set<Long> = emptySet(),
    val selectedIperfTestIds: Set<Long> = emptySet(),
    val selectedSnmpLogIds: Set<Long> = emptySet(),
    val selectedExternalReconIds: Set<Long> = emptySet(),
    val isGenerating: Boolean = false,
    val generatedUri: Uri? = null,
    val error: String? = null
) {
    /** The findings that will be written, given the triage toggle. */
    val reportFindings: List<Finding>
        get() = availableFindings.filter {
            includeResolvedFindings || it.status == FindingStatus.OPEN || it.status == FindingStatus.ACCEPTED
        }

    val projectFilterName: String?
        get() = availableProjects.firstOrNull { it.project.id == projectFilterId }?.project?.name
}

enum class ReportFormat { PDF, DOCX }
