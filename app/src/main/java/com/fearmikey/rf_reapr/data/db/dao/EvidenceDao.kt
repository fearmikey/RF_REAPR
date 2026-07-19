package com.fearmikey.rf_reapr.data.db.dao

import androidx.room.*
import com.fearmikey.rf_reapr.data.db.entity.EvidenceEntity
import com.fearmikey.rf_reapr.data.db.entity.EvidenceFolderEntity
import com.fearmikey.rf_reapr.data.db.entity.EvidenceProjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EvidenceDao {
    @Query("SELECT * FROM evidence_projects WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun getAllProjects(): Flow<List<EvidenceProjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: EvidenceProjectEntity)

    @Delete
    suspend fun deleteProjectPermanently(project: EvidenceProjectEntity)

    @Query("UPDATE evidence_projects SET isDeleted = 1, deletedAt = :deletedAt WHERE id = :projectId")
    suspend fun softDeleteProject(projectId: String, deletedAt: Long)

    @Query("UPDATE evidence_projects SET isDeleted = 0, deletedAt = null WHERE id = :projectId")
    suspend fun restoreProject(projectId: String)

    @Query("SELECT * FROM evidence_projects WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun getDeletedProjects(): Flow<List<EvidenceProjectEntity>>

    @Query("SELECT * FROM evidence_folders WHERE isDeleted = 0 AND projectId = :projectId AND (parentFolderId = :parentFolderId OR (parentFolderId IS NULL AND :parentFolderId IS NULL)) ORDER BY createdAt DESC")
    fun getFolders(projectId: String, parentFolderId: String?): Flow<List<EvidenceFolderEntity>>

    @Query("SELECT * FROM evidence_folders WHERE isDeleted = 0 AND projectId = :projectId ORDER BY createdAt DESC")
    fun getFoldersForProject(projectId: String): Flow<List<EvidenceFolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: EvidenceFolderEntity)

    @Delete
    suspend fun deleteFolderPermanently(folder: EvidenceFolderEntity)

    @Query("UPDATE evidence_folders SET isDeleted = 1, deletedAt = :deletedAt WHERE id = :folderId")
    suspend fun softDeleteFolder(folderId: String, deletedAt: Long)

    @Query("UPDATE evidence_folders SET isDeleted = 1, deletedAt = :deletedAt WHERE projectId = :projectId")
    suspend fun softDeleteFoldersInProject(projectId: String, deletedAt: Long)

    @Query("UPDATE evidence_folders SET isDeleted = 0, deletedAt = null WHERE id = :folderId")
    suspend fun restoreFolder(folderId: String)

    @Query("UPDATE evidence_folders SET isDeleted = 0, deletedAt = null WHERE projectId = :projectId")
    suspend fun restoreFoldersInProject(projectId: String)

    @Query("SELECT * FROM evidence_folders WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun getDeletedFolders(): Flow<List<EvidenceFolderEntity>>

    @Query("SELECT * FROM evidence WHERE isDeleted = 0 ORDER BY timestamp DESC")
    fun getAllEvidence(): Flow<List<EvidenceEntity>>

    @Query("SELECT * FROM evidence WHERE isDeleted = 0 AND controlId = :controlId")
    fun getEvidenceForControl(controlId: String): Flow<List<EvidenceEntity>>

    @Query("SELECT * FROM evidence WHERE isDeleted = 0 AND projectId = :projectId ORDER BY timestamp DESC")
    fun getEvidenceForProject(projectId: String): Flow<List<EvidenceEntity>>

    @Query("SELECT * FROM evidence WHERE isDeleted = 0 AND folderId = :folderId ORDER BY timestamp DESC")
    fun getEvidenceForFolder(folderId: String): Flow<List<EvidenceEntity>>

    @Query("SELECT * FROM evidence WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun getDeletedEvidence(): Flow<List<EvidenceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvidence(evidence: EvidenceEntity)

    @Delete
    suspend fun deleteEvidencePermanently(evidence: EvidenceEntity)

    @Query("UPDATE evidence SET isDeleted = 1, deletedAt = :deletedAt WHERE id = :evidenceId")
    suspend fun softDeleteEvidence(evidenceId: String, deletedAt: Long)

    @Query("UPDATE evidence SET isDeleted = 1, deletedAt = :deletedAt WHERE projectId = :projectId")
    suspend fun softDeleteEvidenceInProject(projectId: String, deletedAt: Long)

    @Query("UPDATE evidence SET isDeleted = 1, deletedAt = :deletedAt WHERE folderId = :folderId")
    suspend fun softDeleteEvidenceInFolder(folderId: String, deletedAt: Long)

    @Query("UPDATE evidence SET isDeleted = 0, deletedAt = null WHERE id = :evidenceId")
    suspend fun restoreEvidence(evidenceId: String)

    @Query("UPDATE evidence SET isDeleted = 0, deletedAt = null WHERE projectId = :projectId")
    suspend fun restoreEvidenceInProject(projectId: String)

    @Query("UPDATE evidence SET isDeleted = 0, deletedAt = null WHERE folderId = :folderId")
    suspend fun restoreEvidenceInFolder(folderId: String)

    @Query("SELECT * FROM evidence WHERE id = :id")
    suspend fun getEvidenceById(id: String): EvidenceEntity?

    @Query("UPDATE evidence SET projectId = :targetProjectId, folderId = :targetFolderId WHERE id = :evidenceId")
    suspend fun updateEvidenceLocation(evidenceId: String, targetProjectId: String?, targetFolderId: String?)

    @Query("UPDATE evidence SET folderId = :targetFolderId WHERE folderId = :sourceFolderId")
    suspend fun updateEvidenceFolder(sourceFolderId: String, targetFolderId: String?)

    @Query("UPDATE evidence_folders SET projectId = :targetProjectId, parentFolderId = :targetParentFolderId WHERE id = :folderId")
    suspend fun updateFolderLocation(folderId: String, targetProjectId: String, targetParentFolderId: String?)

    @Query("UPDATE evidence_folders SET projectId = :targetProjectId WHERE id = :folderId")
    suspend fun updateFolderProject(folderId: String, targetProjectId: String)

    @Query("UPDATE evidence SET projectId = :targetProjectId WHERE folderId = :folderId")
    suspend fun updateEvidenceInFolderProject(folderId: String, targetProjectId: String)

    @Query("SELECT * FROM evidence_folders WHERE id = :folderId")
    suspend fun getFolderById(folderId: String): EvidenceFolderEntity?

    @Query("SELECT * FROM evidence_projects WHERE id = :projectId")
    suspend fun getProjectById(projectId: String): EvidenceProjectEntity?

    @Query("SELECT * FROM evidence WHERE folderId = :folderId")
    suspend fun getEvidenceInFolderSync(folderId: String): List<EvidenceEntity>

    @Query("SELECT * FROM evidence WHERE isDeleted = 1 AND deletedAt <= :threshold")
    suspend fun getEvidenceDeletedBefore(threshold: Long): List<EvidenceEntity>
    
    @Query("SELECT * FROM evidence_folders WHERE isDeleted = 1 AND deletedAt <= :threshold")
    suspend fun getFoldersDeletedBefore(threshold: Long): List<EvidenceFolderEntity>
    
    @Query("SELECT * FROM evidence_projects WHERE isDeleted = 1 AND deletedAt <= :threshold")
    suspend fun getProjectsDeletedBefore(threshold: Long): List<EvidenceProjectEntity>
}
