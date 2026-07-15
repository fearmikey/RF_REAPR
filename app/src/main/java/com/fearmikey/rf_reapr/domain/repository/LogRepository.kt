package com.fearmikey.rf_reapr.domain.repository

import com.fearmikey.rf_reapr.data.db.entity.EventLogEntity
import kotlinx.coroutines.flow.Flow

interface LogRepository {
    fun getLogs(type: String): Flow<List<EventLogEntity>>
    fun getAllLogs(): Flow<List<EventLogEntity>>
    suspend fun saveLog(type: String, summary: String, detailJson: String)
    suspend fun deleteLog(id: Long)
    suspend fun clearLogs(type: String)
}
