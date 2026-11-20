package com.fearmikey.rf_reapr.domain.repository

import com.fearmikey.rf_reapr.domain.model.MagnetometerData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface MagnetometerRepository {
    fun getMagnetometerData(): Flow<MagnetometerData>
    fun startListening()
    fun stopListening()

    /** False if the phone has no magnetic field sensor. */
    val isAvailable: Boolean get() = true

    /** SensorManager.SENSOR_STATUS_* (0 = unreliable ... 3 = high). */
    val accuracy: StateFlow<Int>
}
