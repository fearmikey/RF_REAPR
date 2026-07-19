package com.fearmikey.rf_reapr.domain.repository

import android.net.Uri
import com.fearmikey.rf_reapr.domain.model.*
import kotlinx.coroutines.flow.Flow

interface ReportRepository {
    fun getAvailableScanSessions(): Flow<List<ReportScanSession>>
    fun getAvailableEvidenceProjects(): Flow<List<ReportEvidenceProject>>
    fun getAvailableComplianceFrameworks(): Flow<List<ReportComplianceFramework>>
    fun getAvailableLogs(): Flow<List<EventLog>>
    
    suspend fun getFullEvidenceProject(projectId: String): ReportEvidenceProject
    suspend fun getFullComplianceFindings(frameworkId: String): List<ComplianceControl>

    suspend fun generatePdf(data: ReportData): Result<Uri>
    suspend fun generateDocx(data: ReportData): Result<Uri>
}
