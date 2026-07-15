package com.fearmikey.rf_reapr.domain.repository

import com.fearmikey.rf_reapr.domain.model.Evidence
import com.fearmikey.rf_reapr.domain.model.EvidenceFolder
import com.fearmikey.rf_reapr.domain.model.EvidenceProject
import kotlinx.coroutines.flow.Flow

interface EvidenceRepository {
    fun getAllProjects(): Flow<List<EvidenceProject>>
    fun getDeletedProjects(): Flow<List<EvidenceProject>>
    suspend fun createProject(project: EvidenceProject)
    suspend fun updateProject(project: EvidenceProject)
    suspend fun softDeleteProject(project: EvidenceProject)
    suspend fun restoreProject(project: EvidenceProject)
    suspend fun purgeProject(project: EvidenceProject)
    suspend fun deleteProject(project: EvidenceProject) // Keeping this for backward compat if needed, but should use softDelete

    fun getFoldersForProject(projectId: String): Flow<List<EvidenceFolder>>
    fun getDeletedFolders(): Flow<List<EvidenceFolder>>
    suspend fun createFolder(folder: EvidenceFolder)
    suspend fun deleteFolder(folder: EvidenceFolder)
    suspend fun softDeleteFolder(folder: EvidenceFolder)
    suspend fun restoreFolder(folder: EvidenceFolder)
    suspend fun purgeFolder(folder: EvidenceFolder)

    fun getAllEvidence(): Flow<List<Evidence>>
    fun getEvidenceForProject(projectId: String): Flow<List<Evidence>>
    fun getEvidenceForFolder(folderId: String): Flow<List<Evidence>>
    fun getEvidenceForControl(controlId: String): Flow<List<Evidence>>
    fun getDeletedEvidence(): Flow<List<Evidence>>
    
    suspend fun saveEvidence(evidence: Evidence)
    suspend fun deleteEvidence(evidence: Evidence)
    suspend fun restoreEvidence(evidence: Evidence)
    suspend fun purgeEvidence(evidence: Evidence)
    suspend fun emptyRecycleBin()
    suspend fun purgeExpiredEvidence(thresholdMillis: Long)

    suspend fun moveEvidence(evidenceId: String, targetProjectId: String?, targetFolderId: String?)
    suspend fun copyEvidence(evidenceId: String, targetProjectId: String?, targetFolderId: String?)
}
