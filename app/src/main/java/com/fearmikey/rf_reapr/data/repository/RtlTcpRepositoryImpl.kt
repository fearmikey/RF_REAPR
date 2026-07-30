package com.fearmikey.rf_reapr.data.repository

import com.fearmikey.rf_reapr.domain.model.FftData
import com.fearmikey.rf_reapr.domain.model.SdrConfig
import com.fearmikey.rf_reapr.domain.repository.SdrRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jtransforms.fft.FloatFFT_1D
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.sqrt

class RtlTcpRepositoryImpl : SdrRepository {

    private val _config = MutableStateFlow(SdrConfig())
    override val config: StateFlow<SdrConfig> = _config.asStateFlow()

    private val _fftData = MutableStateFlow(FftData(FloatArray(0), 0, 0))
    override val fftData: StateFlow<FftData> = _fftData.asStateFlow()

    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var dataJob: Job? = null

    private val fftSize = 1024
    private val fft = FloatFFT_1D(fftSize.toLong())
    private val iqBuffer = ByteArray(fftSize * 2)
    private val complexBuffer = FloatArray(fftSize * 2)

    override suspend fun connect(host: String, port: Int) {
        withContext(Dispatchers.IO) {
            try {
                socket = Socket(host, port)
                outputStream = socket?.getOutputStream()
                inputStream = socket?.getInputStream()
                _config.value = _config.value.copy(isConnected = true)
                
                // Set initial parameters
                setFrequency(_config.value.frequency)
                setSampleRate(_config.value.sampleRate)
                setGain(_config.value.gain)

                startDataCollection()
            } catch (e: Exception) {
                e.printStackTrace()
                disconnect()
            }
        }
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            dataJob?.cancel()
            socket?.close()
            socket = null
            outputStream = null
            inputStream = null
            _config.value = _config.value.copy(isConnected = false)
        }
    }

    private fun startDataCollection() {
        dataJob = scope.launch {
            while (isActive && _config.value.isConnected) {
                try {
                    val read = readFully(iqBuffer)
                    if (read == iqBuffer.size) {
                        processIqData(iqBuffer)
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        disconnect()
                    }
                    break
                }
            }
        }
    }

    private fun readFully(buffer: ByteArray): Int {
        var totalRead = 0
        while (totalRead < buffer.size) {
            val read = inputStream?.read(buffer, totalRead, buffer.size - totalRead) ?: -1
            if (read == -1) return -1
            totalRead += read
        }
        return totalRead
    }

    private fun processIqData(data: ByteArray) {
        // Convert to complex float (interleaved I, Q)
        for (i in 0 until fftSize) {
            complexBuffer[2 * i] = (data[2 * i].toInt() and 0xFF).toFloat() - 127.5f
            complexBuffer[2 * i + 1] = (data[2 * i + 1].toInt() and 0xFF).toFloat() - 127.5f
        }

        // Apply FFT
        fft.complexForward(complexBuffer)

        // Calculate magnitude in dB
        val magnitudes = FloatArray(fftSize)
        for (i in 0 until fftSize) {
            val re = complexBuffer[2 * i]
            val im = complexBuffer[2 * i + 1]
            val mag = sqrt(re * re + im * im)
            // Normalize and convert to dB
            magnitudes[i] = (20 * log10(mag + 1e-6f))
        }

        // Shift FFT so center frequency is in the middle
        val shiftedMagnitudes = FloatArray(fftSize)
        val halfSize = fftSize / 2
        System.arraycopy(magnitudes, halfSize, shiftedMagnitudes, 0, halfSize)
        System.arraycopy(magnitudes, 0, shiftedMagnitudes, halfSize, halfSize)

        _fftData.value = FftData(
            magnitudes = shiftedMagnitudes,
            centerFrequency = _config.value.frequency,
            bandwidth = _config.value.sampleRate
        )
    }

    override suspend fun setFrequency(frequency: Long) {
        sendCommand(0x01, (frequency).toInt()) // Frequency in Hz
        _config.value = _config.value.copy(frequency = frequency)
    }

    override suspend fun setSampleRate(sampleRate: Int) {
        sendCommand(0x02, sampleRate)
        _config.value = _config.value.copy(sampleRate = sampleRate)
    }

    override suspend fun setGain(gain: Int) {
        // Gain mode: 0 = auto, 1 = manual
        sendCommand(0x03, if (gain == 0) 0 else 1)
        if (gain != 0) {
            sendCommand(0x04, gain)
        }
        _config.value = _config.value.copy(gain = gain)
    }

    private fun sendCommand(command: Byte, value: Int) {
        val buffer = ByteBuffer.allocate(5)
        buffer.order(ByteOrder.BIG_ENDIAN)
        buffer.put(command)
        buffer.putInt(value)
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    outputStream?.write(buffer.array())
                    outputStream?.flush()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}
