package com.fearmikey.rf_reapr.domain.repository

import com.fearmikey.rf_reapr.domain.model.FftData
import com.fearmikey.rf_reapr.domain.model.SdrConfig
import kotlinx.coroutines.flow.StateFlow

interface SdrRepository {
    val fftData: StateFlow<FftData>
    val config: StateFlow<SdrConfig>
    val error: StateFlow<String?>
    
    suspend fun connect(host: String, port: Int)
    suspend fun disconnect()
    suspend fun setFrequency(frequency: Long)
    suspend fun setSampleRate(sampleRate: Int)
    suspend fun setGain(gain: Int)
}
