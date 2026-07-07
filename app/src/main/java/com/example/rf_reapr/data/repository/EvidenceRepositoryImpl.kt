package com.example.rf_reapr.data.repository

import com.example.rf_reapr.data.db.dao.EvidenceDao
import com.example.rf_reapr.data.db.entity.EvidenceEntity
import com.example.rf_reapr.data.db.entity.EvidenceFolderEntity
import com.example.rf_reapr.data.db.entity.EvidenceProjectEntity
import com.example.rf_reapr.domain.model.Evidence
import com.example.rf_reapr.domain.model.EvidenceFolder
import com.example.rf_reapr.domain.model.EvidenceProject
import com.example.rf_reapr.domain.repository.EvidenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.Date
import java.util.UUID

class EvidenceRepositoryImpl(
    private val evidenceDao: EvidenceDao,
    private val context: android.content.Context
) : EvidenceRepository {

    override fun getAllProjects(): Flow<List<EvidenceProject>> {
        return evidenceDao.getAllProjects().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getDeletedProjects(): Flow<List<EvidenceProject>> {
        return evidenceDao.getDeletedProjects().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun createProject(project: EvidenceProject) {
        evidenceDao.insertProject(project.toEntity())
    }

    override suspend fun updateProject(project: EvidenceProject) {
        evidenceDao.insertProject(project.toEntity())
    }

    override suspend fun softDeleteProject(project: EvidenceProject) {
        val now = System.currentTimeMillis()
        evidenceDao.softDeleteProject(project.id, now)
        evidenceDao.softDeleteFoldersInProject(project.id, now)
        evidenceDao.softDeleteEvidenceInProject(project.id, now)
    }

    override suspend fun restoreProject(project: EvidenceProject) {
        evidenceDao.restoreProject(project.id)
        evidenceDao.restoreFoldersInProject(project.id)
        evidenceDao.restoreEvidenceInProject(project.id)
    }

    override suspend fun purgeProject(project: EvidenceProject) {
        // Find all evidence in this project to delete files
        val projectEvidence = evidenceDao.getEvidenceForProject(project.id).first()
        projectEvidence.forEach { entity ->
            val file = File(entity.filePath)
            if (file.exists()) file.delete()
            evidenceDao.deleteEvidencePermanently(entity)
        }
        
        // Find all folders to delete
        val folders = evidenceDao.getFoldersForProject(project.id).first()
        folders.forEach { evidenceDao.deleteFolderPermanently(it) }
        
        evidenceDao.deleteProjectPermanently(project.toEntity())
    }

    override suspend fun deleteProject(project: EvidenceProject) {
        softDeleteProject(project)
    }

    override fun getFoldersForProject(projectId: String): Flow<List<EvidenceFolder>> {
        return evidenceDao.getFoldersForProject(projectId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getDeletedFolders(): Flow<List<EvidenceFolder>> {
        return evidenceDao.getDeletedFolders().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun createFolder(folder: EvidenceFolder) {
        evidenceDao.insertFolder(folder.toEntity())
    }

    override suspend fun softDeleteFolder(folder: EvidenceFolder) {
        val now = System.currentTimeMillis()
        evidenceDao.softDeleteFolder(folder.id, now)
        evidenceDao.softDeleteEvidenceInFolder(folder.id, now)
    }

    override suspend fun restoreFolder(folder: EvidenceFolder) {
        evidenceDao.restoreFolder(folder.id)
        evidenceDao.restoreEvidenceInFolder(folder.id)
    }

    override suspend fun purgeFolder(folder: EvidenceFolder) {
        val folderEvidence = evidenceDao.getEvidenceForFolder(folder.id).first()
        folderEvidence.forEach { entity ->
            val file = File(entity.filePath)
            if (file.exists()) file.delete()
            evidenceDao.deleteEvidencePermanently(entity)
        }
        evidenceDao.deleteFolderPermanently(folder.toEntity())
    }

    override suspend fun deleteFolder(folder: EvidenceFolder) {
        softDeleteFolder(folder)
    }

    override fun getAllEvidence(): Flow<List<Evidence>> {
        return evidenceDao.getAllEvidence().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getEvidenceForProject(projectId: String): Flow<List<Evidence>> {
        return evidenceDao.getEvidenceForProject(projectId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getEvidenceForFolder(folderId: String): Flow<List<Evidence>> {
        return evidenceDao.getEvidenceForFolder(folderId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getEvidenceForControl(controlId: String): Flow<List<Evidence>> {
        return evidenceDao.getEvidenceForControl(controlId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getDeletedEvidence(): Flow<List<Evidence>> {
        return evidenceDao.getDeletedEvidence().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun saveEvidence(evidence: Evidence) {
        evidenceDao.insertEvidence(evidence.toEntity())
    }

    override suspend fun deleteEvidence(evidence: Evidence) {
        evidenceDao.softDeleteEvidence(evidence.id, System.currentTimeMillis())
    }

    override suspend fun restoreEvidence(evidence: Evidence) {
        evidenceDao.restoreEvidence(evidence.id)
    }

    override suspend fun purgeEvidence(evidence: Evidence) {
        val file = File(evidence.filePath)
        if (file.exists()) {
            file.delete()
        }
        evidenceDao.deleteEvidencePermanently(evidence.toEntity())
    }

    override suspend fun emptyRecycleBin() {
        // Empty projects
        val deletedProjects = evidenceDao.getDeletedProjects().first()
        deletedProjects.forEach { purgeProject(it.toDomain()) }
        
        // Empty folders
        val deletedFolders = evidenceDao.getDeletedFolders().first()
        deletedFolders.forEach { purgeFolder(it.toDomain()) }

        // Empty individual evidence
        val deletedEvidence = evidenceDao.getDeletedEvidence().first()
        deletedEvidence.forEach { purgeEvidence(it.toDomain()) }
    }

    override suspend fun purgeExpiredEvidence(thresholdMillis: Long) {
        val expiredProjects = evidenceDao.getProjectsDeletedBefore(thresholdMillis)
        expiredProjects.forEach { purgeProject(it.toDomain()) }
        
        val expiredFolders = evidenceDao.getFoldersDeletedBefore(thresholdMillis)
        expiredFolders.forEach { purgeFolder(it.toDomain()) }
        
        val expiredEvidence = evidenceDao.getEvidenceDeletedBefore(thresholdMillis)
        expiredEvidence.forEach { purgeEvidence(it.toDomain()) }
    }

    override suspend fun moveEvidence(evidenceId: String, targetProjectId: String?, targetFolderId: String?) {
        evidenceDao.updateEvidenceLocation(evidenceId, targetProjectId, targetFolderId)
    }

    override suspend fun copyEvidence(evidenceId: String, targetProjectId: String?, targetFolderId: String?) {
        val original = evidenceDao.getEvidenceById(evidenceId) ?: return
        val originalFile = File(original.filePath)
        if (!originalFile.exists()) return
        
        val newFileName = "evidence_copy_${System.currentTimeMillis()}.jpg"
        val newFile = File(context.filesDir, newFileName)
        originalFile.copyTo(newFile)
        
        val newEntity = original.copy(
            id = UUID.randomUUID().toString(),
            filePath = newFile.absolutePath,
            projectId = targetProjectId,
            folderId = targetFolderId,
            timestamp = System.currentTimeMillis(),
            isDeleted = false,
            deletedAt = null
        )
        evidenceDao.insertEvidence(newEntity)
    }

    private fun EvidenceProjectEntity.toDomain(): EvidenceProject {
        return EvidenceProject(
            id = id,
            name = name,
            createdAt = Date(createdAt),
            description = description,
            showGps = showGps,
            showDate = showDate,
            showTimestamp = showTimestamp,
            isDeleted = isDeleted,
            deletedAt = deletedAt?.let { Date(it) }
        )
    }

    private fun EvidenceProject.toEntity(): EvidenceProjectEntity {
        return EvidenceProjectEntity(
            id = id,
            name = name,
            createdAt = createdAt.time,
            description = description,
            showGps = showGps,
            showDate = showDate,
            showTimestamp = showTimestamp,
            isDeleted = isDeleted,
            deletedAt = deletedAt?.time
        )
    }

    private fun EvidenceFolderEntity.toDomain(): EvidenceFolder {
        return EvidenceFolder(
            id = id,
            projectId = projectId,
            name = name,
            createdAt = Date(createdAt),
            isDeleted = isDeleted,
            deletedAt = deletedAt?.let { Date(it) }
        )
    }

    private fun EvidenceFolder.toEntity(): EvidenceFolderEntity {
        return EvidenceFolderEntity(
            id = id,
            projectId = projectId,
            name = name,
            createdAt = createdAt.time,
            isDeleted = isDeleted,
            deletedAt = deletedAt?.time
        )
    }

    private fun EvidenceEntity.toDomain(): Evidence {
        return Evidence(
            id = id,
            filePath = filePath,
            timestamp = Date(timestamp),
            latitude = latitude,
            longitude = longitude,
            controlId = controlId,
            projectId = projectId,
            folderId = folderId,
            notes = notes,
            isDeleted = isDeleted,
            deletedAt = deletedAt?.let { Date(it) }
        )
    }

    private fun Evidence.toEntity(): EvidenceEntity {
        return EvidenceEntity(
            id = id,
            filePath = filePath,
            timestamp = timestamp.time,
            latitude = latitude,
            longitude = longitude,
            controlId = controlId,
            projectId = projectId,
            folderId = folderId,
            notes = notes,
            isDeleted = isDeleted,
            deletedAt = deletedAt?.time
        )
    }
}
