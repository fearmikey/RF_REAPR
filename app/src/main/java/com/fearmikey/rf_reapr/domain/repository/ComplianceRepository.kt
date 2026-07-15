package com.fearmikey.rf_reapr.domain.repository

import com.fearmikey.rf_reapr.domain.model.ComplianceControl
import com.fearmikey.rf_reapr.domain.model.ComplianceFramework
import com.fearmikey.rf_reapr.domain.model.ComplianceStatus
import kotlinx.coroutines.flow.Flow

interface ComplianceRepository {
    fun getFrameworks(): List<ComplianceFramework>
    fun getControlsForFramework(frameworkId: String): Flow<List<ComplianceControl>>
    suspend fun updateControlStatus(
        controlId: String, 
        frameworkId: String, 
        status: ComplianceStatus, 
        notes: String
    )
}
