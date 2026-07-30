package com.fearmikey.rf_reapr.ui.sdr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.model.FftData
import com.fearmikey.rf_reapr.domain.model.SdrConfig
import com.fearmikey.rf_reapr.domain.repository.SdrRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SdrViewModel(private val repository: SdrRepository) : ViewModel() {

    val config: StateFlow<SdrConfig> = repository.config as StateFlow<SdrConfig>
    val fftData: StateFlow<FftData> = repository.fftData as StateFlow<FftData>

    fun connect(host: String, port: Int) {
        viewModelScope.launch {
            repository.connect(host, port)
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            repository.disconnect()
        }
    }

    fun setFrequency(frequency: Long) {
        viewModelScope.launch {
            repository.setFrequency(frequency)
        }
    }

    fun setGain(gain: Int) {
        viewModelScope.launch {
            repository.setGain(gain)
        }
    }
}
