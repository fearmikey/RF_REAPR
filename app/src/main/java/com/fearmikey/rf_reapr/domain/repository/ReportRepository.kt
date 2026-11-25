package com.fearmikey.rf_reapr.domain.repository

import android.net.Uri
import com.fearmikey.rf_reapr.domain.model.*
import kotlinx.coroutines.flow.Flow

interface ReportRepository {
    fun getAvailableScanSessions(): Flow<List<ReportScanSession>>
    fun getAvailableEvidenceProjects(): Flow<List<ReportEvidenceProject>>
    fun getAvailableComplianceFrameworks(): Flow<List<ReportComplianceFramework>>
    fun getAvailableLogs(): Flow<List<EventLog>>

    /** Findings of [projectId] (null = recorded with no active project). */
    fun observeFindings(projectId: String?): Flow<List<Finding>>

    suspend fun getFullEvidenceProject(projectId: String): ReportEvidenceProject
    suspend fun getFullComplianceFindings(frameworkId: String): List<ComplianceControl>

    /** Adds mapped controls and linked evidence (with integrity re-checked) to [findings]. */
    suspend fun buildReportFindings(findings: List<Finding>): List<ReportFinding>

    suspend fun generatePdf(data: ReportData): Result<Uri>
    suspend fun generateDocx(data: ReportData): Result<Uri>
}
