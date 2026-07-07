package com.example.rf_reapr.data.db.dao

import androidx.room.*
import com.example.rf_reapr.data.db.entity.ScanSessionEntity
import com.example.rf_reapr.data.db.entity.ScanSessionWithNodes
import com.example.rf_reapr.data.db.entity.SessionNodeCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ScanSessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessionNodeCrossRef(crossRef: SessionNodeCrossRef)

    @Transaction
    @Query("SELECT * FROM scan_sessions ORDER BY timestamp DESC")
    fun getSessionsWithNodes(): Flow<List<ScanSessionWithNodes>>

    @Transaction
    @Query("SELECT * FROM scan_sessions WHERE id = :sessionId")
    suspend fun getSessionWithNodes(sessionId: Long): ScanSessionWithNodes?

    @Transaction
    suspend fun saveSessionWithNodes(session: ScanSessionEntity, ipAddresses: List<String>) {
        val sessionId = insertSession(session)
        ipAddresses.forEach { ip ->
            insertSessionNodeCrossRef(SessionNodeCrossRef(sessionId, ip))
        }
    }

    @Query("DELETE FROM scan_sessions")
    suspend fun deleteAllSessions()

    @Query("DELETE FROM session_node_cross_ref")
    suspend fun deleteAllCrossRefs()
}
