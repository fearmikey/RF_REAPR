package com.fearmikey.rf_reapr.domain.model

import java.util.Date

data class ReportData(
    val title: String,
    val auditorName: String,
    val executiveSummary: String,
    val timestamp: Date = Date(),
    val networkScans: List<ReportScanSession> = emptyList(),
    val wifiScans: List<EventLog> = emptyList(),
    val bleScans: List<EventLog> = emptyList(),
    val iperfTests: List<EventLog> = emptyList(),
    val snmpResults: List<EventLog> = emptyList(),
    val evidenceProjects: List<ReportEvidenceProject> = emptyList(),
    val complianceFindings: List<ReportComplianceFramework> = emptyList(),
    val eventLogs: List<EventLog> = emptyList()
)

data class ReportScanSession(
    val id: Long,
    val timestamp: Long,
    val networkName: String?,
    val gatewayIp: String?,
    val nodes: List<NetworkNode>
)

data class ReportEvidenceProject(
    val project: EvidenceProject,
    val items: List<Evidence>
)

data class ReportComplianceFramework(
    val framework: ComplianceFramework,
    val controls: List<ComplianceControl>
)
