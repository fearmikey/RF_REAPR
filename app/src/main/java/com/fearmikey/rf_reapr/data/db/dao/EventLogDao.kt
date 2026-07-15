package com.fearmikey.rf_reapr.data.db.dao

import androidx.room.*
import com.fearmikey.rf_reapr.data.db.entity.EventLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: EventLogEntity)

    @Query("SELECT * FROM event_logs WHERE type = :type ORDER BY timestamp DESC")
    fun getLogsByType(type: String): Flow<List<EventLogEntity>>

    @Query("SELECT * FROM event_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<EventLogEntity>>

    @Query("DELETE FROM event_logs WHERE id = :id")
    suspend fun deleteLog(id: Long)

    @Query("DELETE FROM event_logs WHERE type = :type")
    suspend fun clearLogsByType(type: String)
}
