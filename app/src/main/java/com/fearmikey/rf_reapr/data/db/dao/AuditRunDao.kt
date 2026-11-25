package com.fearmikey.rf_reapr.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.fearmikey.rf_reapr.data.db.entity.AuditRunEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditRunDao {
    @Insert
    suspend fun insert(run: AuditRunEntity): Long

    @Query("SELECT * FROM audit_runs WHERE projectId = :projectId ORDER BY finishedAt DESC")
    fun observeForProject(projectId: String): Flow<List<AuditRunEntity>>

    @Query("SELECT * FROM audit_runs WHERE projectId = :projectId ORDER BY finishedAt DESC")
    suspend fun getForProject(projectId: String): List<AuditRunEntity>

    @Query("SELECT * FROM audit_runs WHERE id = :id")
    suspend fun getById(id: Long): AuditRunEntity?

    @Query("DELETE FROM audit_runs WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM audit_runs WHERE projectId = :projectId")
    suspend fun deleteForProject(projectId: String)
}
