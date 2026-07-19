package com.fearmikey.rf_reapr.data.repository

import com.fearmikey.rf_reapr.data.db.dao.HidScriptDao
import com.fearmikey.rf_reapr.data.db.entity.HidScriptEntity
import com.fearmikey.rf_reapr.domain.model.HidPayload
import com.fearmikey.rf_reapr.domain.repository.HidRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalHidRepository(private val hidScriptDao: HidScriptDao) : HidRepository {
    override fun getAllScripts(): Flow<List<HidPayload>> {
        return hidScriptDao.getAllScripts().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getScriptById(id: Long): HidPayload? {
        return hidScriptDao.getScriptById(id)?.toDomain()
    }

    override suspend fun saveScript(payload: HidPayload): Long {
        return hidScriptDao.insertScript(HidScriptEntity.fromDomain(payload))
    }

    override suspend fun deleteScript(payload: HidPayload) {
        hidScriptDao.deleteScript(HidScriptEntity.fromDomain(payload))
    }
}
