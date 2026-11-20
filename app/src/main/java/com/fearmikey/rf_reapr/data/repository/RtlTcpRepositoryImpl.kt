package com.fearmikey.rf_reapr.data.repository

import com.fearmikey.rf_reapr.domain.model.FftData
import com.fearmikey.rf_reapr.domain.model.SdrConfig
import com.fearmikey.rf_reapr.domain.repository.SdrRepository
import com.fearmikey.rf_reapr.domain.service.ActiveTaskMonitor
import com.fearmikey.rf_reapr.domain.service.SdrDsp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jtransforms.fft.FloatFFT_1D
import java.io.DataInputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

class RtlTcpRepositoryImpl : SdrRepository {

    private val _config = MutableStateFlow(SdrConfig())
    override val config: StateFlow<SdrConfig> = _config.asStateFlow()

    private val _fftData = MutableStateFlow(FftData(FloatArray(0), 0, 0))
    override val fftData: StateFlow<FftData> = _fftData.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error.asStateFlow()

    @Volatile private var socket: Socket? = null
    @Volatile private var outputStream: OutputStream? = null
    private val writeLock = Mutex()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var dataJob: Job? = null

    private val fftSize = 2048
    private val fft = FloatFFT_1D(fftSize.toLong())
    private val window = SdrDsp.hannWindow(fftSize)
    private val windowSum = window.sum()

    override suspend fun connect(host: String, port: Int) {
        if (_config.value.isConnected || _config.value.isConnecting) return
        _error.value = null
        _config.update { it.copy(isConnecting = true) }
        withContext(Dispatchers.IO) {
            try {
                val s = Socket()
                s.connect(InetSocketAddress(host, port), 3000)
                s.soTimeout = 5000
                s.receiveBufferSize = 256 * 1024
                socket = s
                outputStream = s.getOutputStream()
                val input = DataInputStream(s.getInputStream().buffered(64 * 1024))

                val header = ByteArray(12)
                input.readFully(header)
                val info = SdrDsp.parseDongleInfo(header)
                if (info == null) {
                    throw IllegalStateException("Not an rtl_tcp server (bad header)")
                }
                _config.update { it.copy(isConnected = true, isConnecting = false, tunerName = info.tunerName) }

                val c = _config.value
                setSampleRate(c.sampleRate)
                setFrequency(c.frequency)
                setGain(c.gain)
                if (c.ppm != 0) setPpm(c.ppm)

                ActiveTaskMonitor.addTask(TASK)
                startDataCollection(input)
            } catch (e: Exception) {
                _error.value = "Connection failed: ${e.localizedMessage ?: e.javaClass.simpleName}"
                closeSocket()
            }
        }
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            dataJob?.cancel()
            closeSocket()
        }
    }

    private fun closeSocket() {
        runCatching { socket?.close() }
        socket = null
        outputStream = null
        ActiveTaskMonitor.removeTask(TASK)
        _config.update { it.copy(isConnected = false, isConnecting = false) }
    }

    /**
     * Reads IQ continuously, averages FFT power over ~1/15 s worth of frames and publishes
     * about 15 spectra per second (the old code ran and published 1000+ FFTs per second).
     */
    private fun startDataCollection(input: DataInputStream) {
        dataJob = scope.launch {
            val iq = ByteArray(fftSize * 2)
            val complex = FloatArray(fftSize * 2)
            val power = FloatArray(fftSize)
            var frames = 0
            var lastPublish = System.currentTimeMillis()
            var sequence = 0L
            try {
                while (isActive) {
                    input.readFully(iq)
                    for (i in 0 until fftSize) {
                        val w = window[i]
                        complex[2 * i] = ((iq[2 * i].toInt() and 0xFF) - 127.5f) * w
                        complex[2 * i + 1] = ((iq[2 * i + 1].toInt() and 0xFF) - 127.5f) * w
                    }
                    fft.complexForward(complex)
                    for (i in 0 until fftSize) {
                        val re = complex[2 * i]
                        val im = complex[2 * i + 1]
                        power[i] += re * re + im * im
                    }
                    frames++
                    val now = System.currentTimeMillis()
                    if (now - lastPublish >= FRAME_INTERVAL_MS) {
                        val db = SdrDsp.powerToDbfsShifted(power, frames, windowSum)
                        val c = _config.value
                        _fftData.value = FftData(
                            magnitudes = db,
                            centerFrequency = c.frequency,
                            bandwidth = c.sampleRate,
                            sequence = sequence++,
                            noiseFloor = SdrDsp.noiseFloor(db),
                            peak = SdrDsp.findPeak(db, c.frequency, c.sampleRate)
                        )
                        power.fill(0f)
                        frames = 0
                        lastPublish = now
                    }
                }
            } catch (e: Exception) {
                if (isActive) _error.value = "Stream ended: ${e.localizedMessage ?: e.javaClass.simpleName}"
            } finally {
                closeSocket()
            }
        }
    }

    override suspend fun setFrequency(frequency: Long) {
        val hz = frequency.coerceIn(MIN_FREQ, MAX_FREQ)
        sendCommand(0x01, hz.toInt())
        _config.update { it.copy(frequency = hz) }
    }

    override suspend fun setSampleRate(sampleRate: Int) {
        sendCommand(0x02, sampleRate)
        _config.update { it.copy(sampleRate = sampleRate) }
    }

    override suspend fun setGain(gain: Int) {
        // 0x03: gain mode (0 = auto, 1 = manual); 0x04: gain in tenths of a dB.
        sendCommand(0x03, if (gain == 0) 0 else 1)
        if (gain != 0) sendCommand(0x04, SdrDsp.gainCommandValue(gain))
        _config.update { it.copy(gain = gain) }
    }

    override suspend fun setPpm(ppm: Int) {
        sendCommand(0x05, ppm)
        _config.update { it.copy(ppm = ppm) }
    }

    override suspend fun setBiasTee(enabled: Boolean) {
        sendCommand(0x0E, if (enabled) 1 else 0)
        _config.update { it.copy(biasTee = enabled) }
    }

    /** Commands are written in order under a lock (they used to race in separate coroutines). */
    private suspend fun sendCommand(command: Byte, value: Int) {
        val bytes = ByteBuffer.allocate(5).order(ByteOrder.BIG_ENDIAN).put(command).putInt(value).array()
        withContext(Dispatchers.IO) {
            writeLock.withLock {
                try {
                    outputStream?.apply {
                        write(bytes)
                        flush()
                    }
                } catch (e: Exception) {
                    _error.value = "Command failed: ${e.localizedMessage}"
                }
            }
        }
    }

    private companion object {
        const val TASK = "SDR stream"
        const val FRAME_INTERVAL_MS = 66L
        // Below 24 MHz only works with direct-sampling dongles (RTL-SDR Blog v3/v4).
        const val MIN_FREQ = 500_000L
        const val MAX_FREQ = 1_766_000_000L
    }
}
