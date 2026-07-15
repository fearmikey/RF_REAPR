package com.example.rf_reapr.domain.repository

import com.example.rf_reapr.domain.model.MagnetometerData
import kotlinx.coroutines.flow.Flow

interface MagnetometerRepository {
    fun getMagnetometerData(): Flow<MagnetometerData>
    fun startListening()
    fun stopListening()
}
