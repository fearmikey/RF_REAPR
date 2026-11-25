package com.fearmikey.rf_reapr.data.repository

import com.fearmikey.rf_reapr.data.db.dao.ComplianceDao
import com.fearmikey.rf_reapr.data.db.entity.ComplianceFindingEntity
import com.fearmikey.rf_reapr.domain.model.ComplianceControl
import com.fearmikey.rf_reapr.domain.model.ComplianceFramework
import com.fearmikey.rf_reapr.domain.model.ComplianceStatus
import com.fearmikey.rf_reapr.domain.repository.ComplianceRepository
import com.fearmikey.rf_reapr.domain.service.ComplianceCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ComplianceRepositoryImpl(
    private val complianceDao: ComplianceDao
) : ComplianceRepository {

    override fun getFrameworks(): List<ComplianceFramework> {
        return ComplianceFramework.entries
    }

    override fun getControlsForFramework(frameworkId: String): Flow<List<ComplianceControl>> {
        val staticControls = ComplianceCatalog.controls(frameworkId)
        return complianceDao.getFindingsForFramework(frameworkId).map { findings ->
            staticControls.map { control ->
                val finding = findings.find { it.controlId == control.id }
                val status = try {
                    ComplianceStatus.valueOf(finding?.status ?: "NONE")
                } catch (e: Exception) {
                    ComplianceStatus.NONE
                }
                control.copy(
                    status = status,
                    notes = finding?.notes ?: ""
                )
            }
        }
    }

    override suspend fun updateControlStatus(
        controlId: String,
        frameworkId: String,
        status: ComplianceStatus,
        notes: String
    ) {
        val entity = ComplianceFindingEntity(
            controlId = controlId,
            frameworkId = frameworkId,
            status = status.name,
            notes = notes
        )
        complianceDao.insertFinding(entity)
    }
}
