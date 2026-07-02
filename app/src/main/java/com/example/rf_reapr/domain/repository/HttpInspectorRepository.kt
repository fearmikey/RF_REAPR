package com.example.rf_reapr.domain.repository

import com.example.rf_reapr.domain.model.HttpSecurityResult
import kotlinx.coroutines.flow.Flow

interface HttpInspectorRepository {
    fun inspectUrl(url: String): Flow<InspectorResult>
    
    sealed class InspectorResult {
        object Loading : InspectorResult()
        data class Success(val result: HttpSecurityResult) : InspectorResult()
        data class Error(val message: String) : InspectorResult()
    }
}
