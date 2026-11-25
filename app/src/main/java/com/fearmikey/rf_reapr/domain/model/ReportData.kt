package com.fearmikey.rf_reapr.domain.model

import com.fearmikey.rf_reapr.domain.service.EvidenceIntegrity
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
    val eventLogs: List<EventLog> = emptyList(),
    /** Engagement the report is scoped to, when one was chosen in the builder. */
    val projectName: String? = null,
    val scope: String = "",
    /** Findings section (already filtered to the chosen project and statuses). */
    val findings: List<ReportFinding> = emptyList()
)

/** A finding with what a report shows next to it. */
data class ReportFinding(
    val finding: Finding,
    /** Compliance control ids this finding is evidence against (see ComplianceMapper). */
    val controlIds: List<String> = emptyList(),
    val evidence: List<ReportEvidenceItem> = emptyList()
)

/** An evidence item plus the result of re-checking its capture hash at report time. */
data class ReportEvidenceItem(
    val evidence: Evidence,
    val integrity: EvidenceIntegrity
)

data class ReportScanSession(
    val id: Long,
    val timestamp: Long,
    val networkName: String?,
    val gatewayIp: String?,
    val nodes: List<NetworkNode>,
    val projectId: String? = null
)

data class ReportEvidenceProject(
    val project: EvidenceProject,
    val items: List<Evidence>,
    /** Integrity per evidence id; empty until the full project is loaded for a report. */
    val integrity: Map<String, EvidenceIntegrity> = emptyMap()
)

data class ReportComplianceFramework(
    val framework: ComplianceFramework,
    val controls: List<ComplianceControl>
)
