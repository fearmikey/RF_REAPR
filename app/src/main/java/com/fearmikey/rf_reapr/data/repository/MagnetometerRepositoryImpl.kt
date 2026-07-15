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
import kotlinx.coroutines.flow.asSharedFlow

class MagnetometerRepositoryImpl(context: Context) : MagnetometerRepository, SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val _dataFlow = MutableSharedFlow<MagnetometerData>(replay = 1)

    override fun getMagnetometerData(): Flow<MagnetometerData> = _dataFlow.asSharedFlow()

    override fun startListening() {
        magnetometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) {
            val data = MagnetometerData(
                x = event.values[0],
                y = event.values[1],
                z = event.values[2]
            )
            _dataFlow.tryEmit(data)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for this implementation
    }
}
