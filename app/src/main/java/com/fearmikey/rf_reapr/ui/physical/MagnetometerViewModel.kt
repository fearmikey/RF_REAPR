package com.fearmikey.rf_reapr.ui.physical

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.model.MagnetometerData
import com.fearmikey.rf_reapr.domain.repository.MagnetometerRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class FieldStatus {
    WEAK, NORMAL, SIGNIFICANT, HIGH
}

class MagnetometerViewModel(
    private val repository: MagnetometerRepository
) : ViewModel() {

    private val _magnetometerData = MutableStateFlow<MagnetometerData?>(null)
    val magnetometerData: StateFlow<MagnetometerData?> = _magnetometerData.asStateFlow()

    private val _peakStrength = MutableStateFlow(0f)
    val peakStrength: StateFlow<Float> = _peakStrength.asStateFlow()

    private val _fieldStatus = MutableStateFlow(FieldStatus.WEAK)
    val fieldStatus: StateFlow<FieldStatus> = _fieldStatus.asStateFlow()

    private val hysteresisMargin = 5f

    init {
        viewModelScope.launch {
            repository.getMagnetometerData().collect { data ->
                _magnetometerData.value = data
                
                // Peak tracking
                if (data.totalStrength > _peakStrength.value) {
                    _peakStrength.value = data.totalStrength
                }
                
                // Hysteresis calculation
                _fieldStatus.value = calculateStatus(data.totalStrength, _fieldStatus.value)
            }
        }
    }

    private fun calculateStatus(strength: Float, current: FieldStatus): FieldStatus {
        return when (current) {
            FieldStatus.WEAK -> {
                if (strength > 40f + hysteresisMargin) FieldStatus.NORMAL else FieldStatus.WEAK
            }
            FieldStatus.NORMAL -> {
                when {
                    strength > 80f + hysteresisMargin -> FieldStatus.SIGNIFICANT
                    strength < 40f - hysteresisMargin -> FieldStatus.WEAK
                    else -> FieldStatus.NORMAL
                }
            }
            FieldStatus.SIGNIFICANT -> {
                when {
                    strength > 150f + hysteresisMargin -> FieldStatus.HIGH
                    strength < 80f - hysteresisMargin -> FieldStatus.NORMAL
                    else -> FieldStatus.SIGNIFICANT
                }
            }
            FieldStatus.HIGH -> {
                if (strength < 150f - hysteresisMargin) FieldStatus.SIGNIFICANT else FieldStatus.HIGH
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
