package com.fearmikey.rf_reapr.domain.repository

import com.fearmikey.rf_reapr.domain.model.EvidenceProject
import com.fearmikey.rf_reapr.domain.model.ReportScanSession
import com.fearmikey.rf_reapr.domain.service.EngagementReport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class EngagementStats(
    val openBySeverity: Map<com.fearmikey.rf_reapr.domain.model.RiskLevel, Int> = emptyMap(),
    val totalFindings: Int = 0,
    val scans: Int = 0,
    val logs: Int = 0,
    val evidence: Int = 0
)

/**
 * Projects as engagements: choosing the active one, editing its scope, and summarizing
 * everything recorded under it.
 */
interface EngagementRepository {
    val activeProjectId: StateFlow<String?>

    fun observeProjects(): Flow<List<EvidenceProject>>
    fun observeActiveProject(): Flow<EvidenceProject?>
    fun setActiveProject(projectId: String?)

    suspend fun createProject(name: String, description: String, scope: String): EvidenceProject
    suspend fun updateScope(projectId: String, scope: String)

    fun observeStats(projectId: String?): Flow<EngagementStats>

    /** Scan sessions of [projectId]; null = all sessions on the device. */
    fun observeScanSessions(projectId: String?): Flow<List<ReportScanSession>>

    suspend fun buildReport(projectId: String?, appVersion: String): EngagementReport
}
