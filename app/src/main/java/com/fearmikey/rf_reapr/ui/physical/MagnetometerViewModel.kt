package com.fearmikey.rf_reapr.ui.physical

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.model.MagnetometerData
import com.fearmikey.rf_reapr.domain.repository.MagnetometerRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.sqrt

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

    /** Ambient field vector captured by [calibrate]; deviation is measured from it. */
    private val _baseline = MutableStateFlow<MagnetometerData?>(null)
    val baseline: StateFlow<MagnetometerData?> = _baseline.asStateFlow()

    /** |B - baseline| in µT (or |B| - 50 µT before calibration). */
    private val _deviation = MutableStateFlow(0f)
    val deviation: StateFlow<Float> = _deviation.asStateFlow()

    private val _history = MutableStateFlow<List<Float?>>(emptyList())
    val history: StateFlow<List<Float?>> = _history.asStateFlow()

    private val _isCalibrating = MutableStateFlow(false)
    val isCalibrating: StateFlow<Boolean> = _isCalibrating.asStateFlow()

    private val _soundEnabled = MutableStateFlow(false)
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    val accuracy: StateFlow<Int> = repository.accuracy
    val isAvailable: Boolean get() = repository.isAvailable

    private val hysteresisMargin = 3f
    private var smoothed: MagnetometerData? = null
    private val calibrationSamples = mutableListOf<MagnetometerData>()
    private var lastHistoryAt = 0L

    init {
        viewModelScope.launch {
            repository.getMagnetometerData().collect { raw ->
                // Light low-pass filter: the raw sensor is noisy at 50 Hz.
                val prev = smoothed
                val data = if (prev == null) raw else MagnetometerData(
                    prev.x + 0.3f * (raw.x - prev.x),
                    prev.y + 0.3f * (raw.y - prev.y),
                    prev.z + 0.3f * (raw.z - prev.z)
                )
                smoothed = data
                _magnetometerData.value = data
                if (data.totalStrength > _peakStrength.value) _peakStrength.value = data.totalStrength

                if (_isCalibrating.value) {
                    calibrationSamples += raw
                    if (calibrationSamples.size >= CALIBRATION_SAMPLES) finishCalibration()
                }

                val base = _baseline.value
                val dev = if (base != null) {
                    val dx = data.x - base.x
                    val dy = data.y - base.y
                    val dz = data.z - base.z
                    sqrt(dx * dx + dy * dy + dz * dz)
                } else {
                    // Uncalibrated: compare against a typical Earth field (~50 µT).
                    kotlin.math.abs(data.totalStrength - 50f)
                }
                _deviation.value = dev
                _fieldStatus.value = calculateStatus(dev, _fieldStatus.value)

                val now = System.currentTimeMillis()
                if (now - lastHistoryAt >= 100) {
                    lastHistoryAt = now
                    _history.update { (it + dev).takeLast(150) }
                }
            }
        }
    }

    /**
     * Deviation thresholds (µT above the ambient baseline). Small electronics and wiring
     * usually show 5–20 µT close up; magnets and speakers far more.
     */
    private fun calculateStatus(dev: Float, current: FieldStatus): FieldStatus {
        val up = listOf(8f, 25f, 80f)
        fun level(s: FieldStatus) = s.ordinal
        val target = when {
            dev > up[2] -> FieldStatus.HIGH
            dev > up[1] -> FieldStatus.SIGNIFICANT
            dev > up[0] -> FieldStatus.NORMAL
            else -> FieldStatus.WEAK
        }
        // Hysteresis: only step down once clearly below the threshold.
        if (level(target) < level(current)) {
            val threshold = up[level(current) - 1]
            return if (dev < threshold - hysteresisMargin) target else current
        }
        return target
    }

    fun calibrate() {
        calibrationSamples.clear()
        _isCalibrating.value = true
    }

    private fun finishCalibration() {
        val n = calibrationSamples.size.toFloat()
        _baseline.value = MagnetometerData(
            calibrationSamples.sumOf { it.x.toDouble() }.toFloat() / n,
            calibrationSamples.sumOf { it.y.toDouble() }.toFloat() / n,
            calibrationSamples.sumOf { it.z.toDouble() }.toFloat() / n
        )
        calibrationSamples.clear()
        _isCalibrating.value = false
        _history.value = emptyList()
    }

    fun clearBaseline() {
        _baseline.value = null
    }

    fun setSoundEnabled(enabled: Boolean) {
        _soundEnabled.value = enabled
    }

    fun startScanning() = repository.startListening()

    fun stopScanning() = repository.stopListening()

    fun resetPeak() {
        _peakStrength.value = 0f
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopListening()
    }

    private companion object {
        const val CALIBRATION_SAMPLES = 100 // ~2 s at 50 Hz
    }
}
