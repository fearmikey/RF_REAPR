package com.example.rf_reapr.ui.physical

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rf_reapr.domain.model.MagnetometerData
import com.example.rf_reapr.domain.repository.MagnetometerRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MagnetometerViewModel(
    private val repository: MagnetometerRepository
) : ViewModel() {

    private val _magnetometerData = MutableStateFlow<MagnetometerData?>(null)
    val magnetometerData: StateFlow<MagnetometerData?> = _magnetometerData.asStateFlow()

    private val _peakStrength = MutableStateFlow(0f)
    val peakStrength: StateFlow<Float> = _peakStrength.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getMagnetometerData().collect { data ->
                _magnetometerData.value = data
                if (data.totalStrength > _peakStrength.value) {
                    _peakStrength.value = data.totalStrength
                }
            }
        }
    }

    fun startScanning() {
        repository.startListening()
    }

    fun stopScanning() {
        repository.stopListening()
    }

    fun resetPeak() {
        _peakStrength.value = 0f
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopListening()
    }
}
