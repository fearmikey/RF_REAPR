package com.fearmikey.rf_reapr.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.fearmikey.rf_reapr.data.db.entity.FindingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FindingDao {
    @Query("SELECT * FROM findings WHERE projectId = :projectId ORDER BY lastSeen DESC")
    fun observeForProject(projectId: String): Flow<List<FindingEntity>>

    @Query("SELECT * FROM findings ORDER BY lastSeen DESC")
    fun observeAll(): Flow<List<FindingEntity>>

    @Query("SELECT * FROM findings WHERE projectId = :projectId ORDER BY lastSeen DESC")
    suspend fun getForProject(projectId: String): List<FindingEntity>

    @Query("SELECT * FROM findings WHERE projectId = :projectId AND fingerprint IN (:fingerprints)")
    suspend fun getByFingerprints(projectId: String, fingerprints: List<String>): List<FindingEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(findings: List<FindingEntity>)

    @Update
    suspend fun updateAll(findings: List<FindingEntity>)

    @Transaction
    suspend fun upsert(inserts: List<FindingEntity>, updates: List<FindingEntity>) {
        if (inserts.isNotEmpty()) insertAll(inserts)
        if (updates.isNotEmpty()) updateAll(updates)
    }

    @Query("UPDATE findings SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("UPDATE findings SET notes = :notes WHERE id = :id")
    suspend fun updateNotes(id: Long, notes: String?)

    @Query("DELETE FROM findings WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM findings WHERE projectId = :projectId")
    suspend fun deleteForProject(projectId: String)

    @Query("SELECT COUNT(*) FROM findings WHERE projectId = :projectId")
    fun countForProject(projectId: String): Flow<Int>
}
