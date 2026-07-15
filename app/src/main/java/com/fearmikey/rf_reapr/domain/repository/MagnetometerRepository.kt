package com.fearmikey.rf_reapr.domain.repository

import com.fearmikey.rf_reapr.domain.model.MagnetometerData
import kotlinx.coroutines.flow.Flow

interface MagnetometerRepository {
    fun getMagnetometerData(): Flow<MagnetometerData>
    fun startListening()
    fun stopListening()
}
