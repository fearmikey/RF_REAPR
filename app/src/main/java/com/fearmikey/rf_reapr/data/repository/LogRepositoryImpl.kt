package com.fearmikey.rf_reapr.data.repository

import com.fearmikey.rf_reapr.data.db.dao.EventLogDao
import com.fearmikey.rf_reapr.data.db.entity.EventLogEntity
import com.fearmikey.rf_reapr.domain.repository.LogRepository
import kotlinx.coroutines.flow.Flow

class LogRepositoryImpl(
    private val eventLogDao: EventLogDao
) : LogRepository {

    override fun getLogs(type: String): Flow<List<EventLogEntity>> {
        return eventLogDao.getLogsByType(type)
    }

    override fun getAllLogs(): Flow<List<EventLogEntity>> {
        return eventLogDao.getAllLogs()
    }

    override suspend fun saveLog(type: String, summary: String, detailJson: String) {
        eventLogDao.insertLog(
            EventLogEntity(
                type = type,
                summary = summary,
                detailJson = detailJson
            )
        )
    }

    override suspend fun deleteLog(id: Long) {
        eventLogDao.deleteLog(id)
    }

    override suspend fun clearLogs(type: String) {
        eventLogDao.clearLogsByType(type)
    }
}
