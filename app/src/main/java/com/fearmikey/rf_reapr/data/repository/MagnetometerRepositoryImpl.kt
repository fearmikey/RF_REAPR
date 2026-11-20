package com.fearmikey.rf_reapr.data.repository

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.fearmikey.rf_reapr.domain.model.MagnetometerData
import com.fearmikey.rf_reapr.domain.repository.MagnetometerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class MagnetometerRepositoryImpl(context: Context) : MagnetometerRepository, SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val _dataFlow = MutableSharedFlow<MagnetometerData>(replay = 1, extraBufferCapacity = 8)
    private val _accuracy = MutableStateFlow(SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM)
    override val accuracy: StateFlow<Int> = _accuracy.asStateFlow()

    override val isAvailable: Boolean get() = magnetometer != null

    override fun getMagnetometerData(): Flow<MagnetometerData> = _dataFlow.asSharedFlow()

    override fun startListening() {
        magnetometer?.let {
            // GAME rate (~50 Hz) so sweeping the phone over a wall reacts immediately.
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) {
            _dataFlow.tryEmit(MagnetometerData(x = event.values[0], y = event.values[1], z = event.values[2]))
            _accuracy.value = event.accuracy
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) _accuracy.value = accuracy
    }
}
