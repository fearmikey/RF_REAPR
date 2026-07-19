package com.fearmikey.rf_reapr.ui.report

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.model.*
import com.fearmikey.rf_reapr.domain.repository.ReportRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ReportBuilderViewModel(
    private val repository: ReportRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportBuilderUiState())
    val uiState: StateFlow<ReportBuilderUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.getAvailableScanSessions(),
                repository.getAvailableEvidenceProjects(),
                repository.getAvailableComplianceFrameworks(),
                repository.getAvailableLogs()
            ) { scans, projects, compliance, logs ->
                _uiState.update { state ->
                    state.copy(
                        availableScans = scans,
                        availableProjects = projects,
                        availableCompliance = compliance,
                        availableLogs = logs.filter { it.type != "WIFI" },
                        availableWifiScans = logs.filter { it.type == "WIFI" }
                    )
                }
            }.collect()
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

    fun toggleScan(scanId: Long) {
        _uiState.update { state ->
            val newSelected = if (scanId in state.selectedScanIds) {
                state.selectedScanIds - scanId
            } else {
                state.selectedScanIds + scanId
            }
            state.copy(selectedScanIds = newSelected)
        }
    }

    fun toggleProject(projectId: String) {
        _uiState.update { state ->
            val newSelected = if (projectId in state.selectedProjectIds) {
                state.selectedProjectIds - projectId
            } else {
                state.selectedProjectIds + projectId
            }
            state.copy(selectedProjectIds = newSelected)
        }
    }

    fun toggleCompliance(frameworkId: String) {
        _uiState.update { state ->
            val newSelected = if (frameworkId in state.selectedComplianceIds) {
                state.selectedComplianceIds - frameworkId
            } else {
                state.selectedComplianceIds + frameworkId
            }
            state.copy(selectedComplianceIds = newSelected)
        }
    }

    fun toggleLog(logId: Long) {
        _uiState.update { state ->
            val newSelected = if (logId in state.selectedLogIds) {
                state.selectedLogIds - logId
            } else {
                state.selectedLogIds + logId
            }
            state.copy(selectedLogIds = newSelected)
        }
    }

    fun toggleWifiScan(logId: Long) {
        _uiState.update { state ->
            val newSelected = if (logId in state.selectedWifiScanIds) {
                state.selectedWifiScanIds - logId
            } else {
                state.selectedWifiScanIds + logId
            }
            state.copy(selectedWifiScanIds = newSelected)
        }
    }

    fun generateReport(format: ReportFormat) {
        val state = _uiState.value
        if (state.isGenerating) return

        _uiState.update { it.copy(isGenerating = true, error = null, generatedUri = null) }

        viewModelScope.launch {
            val selectedScans = state.availableScans.filter { it.id in state.selectedScanIds }
            
            // Fetch full evidence projects with items
            val selectedProjects = state.selectedProjectIds.map { projectId ->
                repository.getFullEvidenceProject(projectId)
            }
            
            // Fetch full compliance findings (static controls + results)
            val selectedCompliance = state.selectedComplianceIds.map { frameworkId ->
                val framework = ComplianceFramework.entries.find { it.name == frameworkId }!!
                val controls = repository.getFullComplianceFindings(frameworkId)
                ReportComplianceFramework(framework, controls)
            }

            val reportData = ReportData(
                title = state.title,
                auditorName = state.auditorName,
                executiveSummary = state.executiveSummary,
                networkScans = selectedScans,
                wifiScans = state.availableWifiScans.filter { it.id in state.selectedWifiScanIds },
                evidenceProjects = selectedProjects,
                complianceFindings = selectedCompliance,
                eventLogs = state.availableLogs.filter { it.id in state.selectedLogIds }
            )

            val result = when (format) {
                ReportFormat.PDF -> repository.generatePdf(reportData)
                ReportFormat.DOCX -> repository.generateDocx(reportData)
            }

            _uiState.update { 
                it.copy(
                    isGenerating = false,
                    generatedUri = result.getOrNull(),
                    error = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun clearResult() {
        _uiState.update { it.copy(generatedUri = null, error = null) }
    }
}

data class ReportBuilderUiState(
    val title: String = "Security Audit Report",
    val auditorName: String = "Lead Auditor",
    val executiveSummary: String = "",
    val availableScans: List<ReportScanSession> = emptyList(),
    val availableProjects: List<ReportEvidenceProject> = emptyList(),
    val availableCompliance: List<ReportComplianceFramework> = emptyList(),
    val availableLogs: List<EventLog> = emptyList(),
    val availableWifiScans: List<EventLog> = emptyList(),
    val selectedScanIds: Set<Long> = emptySet(),
    val selectedProjectIds: Set<String> = emptySet(),
    val selectedComplianceIds: Set<String> = emptySet(),
    val selectedLogIds: Set<Long> = emptySet(),
    val selectedWifiScanIds: Set<Long> = emptySet(),
    val isGenerating: Boolean = false,
    val generatedUri: Uri? = null,
    val error: String? = null
)

enum class ReportFormat { PDF, DOCX }
