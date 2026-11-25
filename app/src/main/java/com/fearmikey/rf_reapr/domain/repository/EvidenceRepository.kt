package com.fearmikey.rf_reapr.domain.repository

import com.fearmikey.rf_reapr.domain.model.Evidence
import com.fearmikey.rf_reapr.domain.model.EvidenceFolder
import com.fearmikey.rf_reapr.domain.model.EvidenceProject
import com.fearmikey.rf_reapr.domain.service.EvidenceIntegrity
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
    fun getFolders(projectId: String, parentFolderId: String?): Flow<List<EvidenceFolder>>
    suspend fun getFolderById(folderId: String): EvidenceFolder?
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

    suspend fun moveFolder(folderId: String, targetProjectId: String?, targetFolderId: String?)
    suspend fun copyFolder(folderId: String, targetProjectId: String?, targetFolderId: String?)

    /** Evidence attached to a finding (see [linkToFinding]). */
    fun getEvidenceForFinding(findingId: Long): Flow<List<Evidence>>

    /** Attach an item to a finding, or detach it with a null [findingId]. */
    suspend fun linkToFinding(evidenceId: String, findingId: Long?)

    /** Re-hashes the file and compares it with the SHA-256 recorded at capture. */
    suspend fun verifyIntegrity(evidence: Evidence): EvidenceIntegrity

    /** Hashes items captured before hashes were recorded. Returns how many were hashed. */
    suspend fun backfillHashes(): Int

    suspend fun hideData(evidence: Evidence, message: String, passphrase: CharArray)
    suspend fun revealData(evidence: Evidence, passphrase: CharArray): Result<String>
}
