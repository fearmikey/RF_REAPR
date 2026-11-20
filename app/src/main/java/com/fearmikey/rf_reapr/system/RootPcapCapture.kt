package com.fearmikey.rf_reapr.system

import android.content.Context
import android.util.Log
import com.fearmikey.rf_reapr.domain.service.ActiveTaskMonitor
import com.fearmikey.rf_reapr.domain.service.CaptureMode
import com.fearmikey.rf_reapr.domain.service.PacketCaptureSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.DataOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Runs `tcpdump` as root. Process-wide singleton (see [get]) so a running capture survives
 * the capture screen's ViewModel being cleared, and can always be stopped afterwards.
 */
class RootPcapCapture private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tcpdumpProcess: Process? = null
    private var monitorJob: Job? = null
    private var outputFile: File? = null

    init {
        scope.launch {
            ActiveTaskMonitor.stopAllSignal.collect { stopCapture() }
        }
    }

    fun isRootAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            process.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }

    fun isTcpdumpAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "which tcpdump"))
            process.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Starts tcpdump on all interfaces. [bpfFilter] must already be validated with
     * [isValidFilter] since it is passed through a root shell.
     */
    @Synchronized
    fun startCapture(bpfFilter: String? = null): String? {
        if (tcpdumpProcess != null) return outputFile?.absolutePath
        if (!isRootAvailable() || !isTcpdumpAvailable()) {
            Log.e(TAG, "Root or tcpdump not available")
            return null
        }
        val filter = bpfFilter?.trim().orEmpty()
        if (!isValidFilter(filter)) return null

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val pcapDir = File(appContext.filesDir, "pcaps")
        if (!pcapDir.exists()) pcapDir.mkdirs()
        val file = File(pcapDir, "root_capture_$timestamp.pcap")
        outputFile = file

        try {
            // -i any = listen on all interfaces, -U = packet-buffered writes
            val filterArg = if (filter.isNotEmpty()) " '$filter'" else ""
            val command = "tcpdump -i any -U -w '${file.absolutePath}'$filterArg\n"

            tcpdumpProcess = Runtime.getRuntime().exec("su")
            DataOutputStream(tcpdumpProcess?.outputStream).apply {
                writeBytes(command)
                flush()
            }
            // We do NOT write "exit\n" because we want the process to stay alive and capture

            PacketCaptureSession.started(CaptureMode.ROOT_TCPDUMP, file.absolutePath)
            ActiveTaskMonitor.addTask(TASK_NAME)
            monitorJob = scope.launch {
                // tcpdump writes straight to disk, so report the file size as progress.
                while (isActive) {
                    delay(1_000)
                    PacketCaptureSession.updateFileSize(file.length())
                }
            }
            return file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error starting tcpdump", e)
            PacketCaptureSession.failed("Failed to start tcpdump: ${e.message}")
            tcpdumpProcess = null
            return null
        }
    }

    @Synchronized
    fun stopCapture() {
        if (tcpdumpProcess == null) return
        try {
            // To cleanly stop tcpdump running under su (so it flushes), signal it explicitly.
            Runtime.getRuntime().exec(arrayOf("su", "-c", "killall tcpdump")).waitFor()
            try {
                DataOutputStream(tcpdumpProcess?.outputStream).apply {
                    writeBytes("exit\n")
                    flush()
                    close()
                }
            } catch (_: Exception) {
            }
            tcpdumpProcess?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping tcpdump", e)
        } finally {
            tcpdumpProcess = null
            monitorJob?.cancel()
            monitorJob = null
            ActiveTaskMonitor.removeTask(TASK_NAME)
            PacketCaptureSession.stopped(outputFile?.length())
        }
    }

    companion object {
        private const val TAG = "RootPcap"
        private const val TASK_NAME = "Root Packet Capture"

        // BPF expressions only need these characters; quotes, $, ;, backticks etc. are
        // rejected because the filter is passed through a root shell.
        private val filterRegex = Regex("""^[A-Za-z0-9 .:/()!&|=<>\[\]\-]*$""")

        fun isValidFilter(filter: String): Boolean = filter.length <= 256 && filterRegex.matches(filter)

        @Volatile private var instance: RootPcapCapture? = null

        fun get(context: Context): RootPcapCapture =
            instance ?: synchronized(this) {
                instance ?: RootPcapCapture(context).also { instance = it }
            }
    }
}
