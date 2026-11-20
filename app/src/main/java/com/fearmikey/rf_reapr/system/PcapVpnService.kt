package com.fearmikey.rf_reapr.system

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.fearmikey.rf_reapr.MainActivity
import com.fearmikey.rf_reapr.domain.service.ActiveTaskMonitor
import com.fearmikey.rf_reapr.domain.service.CaptureAggregator
import com.fearmikey.rf_reapr.domain.service.CaptureMode
import com.fearmikey.rf_reapr.domain.service.PacketCaptureSession
import com.fearmikey.rf_reapr.domain.service.PacketSummarizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Captures outbound IP packets from other apps via a local VpnService and writes them to a
 * pcap file (LINKTYPE_RAW).
 *
 * Packets are recorded but not forwarded (there is no userspace TCP/IP stack), so apps
 * routed through the VPN lose connectivity during capture. RF-REAPR itself is excluded
 * from the VPN so the app keeps working.
 */
class PcapVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var captureThread: Thread? = null
    @Volatile private var isCapturing = false
    private var outputFile: File? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        const val ACTION_START = "com.fearmikey.rf_reapr.START_PCAP"
        const val ACTION_STOP = "com.fearmikey.rf_reapr.STOP_PCAP"
        private const val TAG = "PcapVpnService"
        private const val NOTIFICATION_ID = 888
        private const val CHANNEL_ID = "pcap_capture_channel"
        private const val PUBLISH_INTERVAL_MS = 500L
        private const val FLUSH_INTERVAL_MS = 2_000L

        // PCAP Constants
        private const val PCAP_MAGIC_NUMBER = 0xa1b2c3d4L
        private const val PCAP_VERSION_MAJOR = 2
        private const val PCAP_VERSION_MINOR = 4
        private const val PCAP_LINKTYPE_RAW = 101 // Raw IP
    }

    override fun onCreate() {
        super.onCreate()
        // Honour the global "Stop All Tasks" action even if the capture screen is closed.
        serviceScope.launch {
            ActiveTaskMonitor.stopAllSignal.collect { stopCapture() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startCapture()
            ACTION_STOP -> stopCapture()
        }
        return START_NOT_STICKY
    }

    private fun startCapture() {
        if (isCapturing) return

        createNotificationChannel()
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, PcapVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Packet Capture Active")
            .setContentText("Other apps' traffic is being recorded (and not forwarded)")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openApp)
            .addAction(android.R.drawable.ic_media_pause, "Stop capture", stopIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val pcapDir = File(filesDir, "pcaps")
        if (!pcapDir.exists()) pcapDir.mkdirs()
        outputFile = File(pcapDir, "capture_$timestamp.pcap")

        setupVpn()
    }

    private fun setupVpn() {
        val builder = Builder()
            .setSession("RF_REAPR Capture")
            .addAddress("10.0.0.2", 24)
            .addRoute("0.0.0.0", 0)

        try {
            // Capture IPv6 too, otherwise v6 traffic silently bypasses the capture.
            builder.addAddress("fd00:7265:6170::2", 64).addRoute("::", 0)
        } catch (e: Exception) {
            Log.w(TAG, "IPv6 capture unavailable", e)
        }
        try {
            // Keep RF-REAPR's own traffic (online lookups, iperf, etc.) off the black-hole VPN.
            builder.addDisallowedApplication(packageName)
        } catch (e: Exception) {
            Log.w(TAG, "Could not exclude own package", e)
        }

        try {
            val pfd = builder.establish()
            if (pfd == null) {
                fail("VPN permission was revoked or another app holds an always-on VPN")
                return
            }
            vpnInterface = pfd
            isCapturing = true
            PacketCaptureSession.started(CaptureMode.STANDARD_VPN, outputFile?.absolutePath)
            captureThread = Thread({ captureLoop() }, "pcap-capture").also { it.start() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to establish VPN", e)
            fail("Failed to establish VPN: ${e.message}")
        }
    }

    private fun fail(message: String) {
        PacketCaptureSession.failed(message)
        isCapturing = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun captureLoop() {
        val vpnFd = vpnInterface?.fileDescriptor ?: return
        val inputStream = FileInputStream(vpnFd)
        val aggregator = CaptureAggregator()
        var lastPublish = 0L
        var lastFlush = 0L

        try {
            BufferedOutputStream(FileOutputStream(outputFile), 64 * 1024).use { outputStream ->
                writePcapGlobalHeader(outputStream)

                // Max size of an IP packet is 65535 bytes
                val buffer = ByteArray(65535)

                while (isCapturing && !Thread.currentThread().isInterrupted) {
                    val length = inputStream.read(buffer)
                    if (length > 0) {
                        writePcapPacketHeader(outputStream, length)
                        outputStream.write(buffer, 0, length)
                        aggregator.add(PacketSummarizer.parse(buffer, length), length)
                    }
                    val now = System.currentTimeMillis()
                    if (now - lastPublish >= PUBLISH_INTERVAL_MS) {
                        PacketCaptureSession.publish(aggregator)
                        lastPublish = now
                    }
                    if (now - lastFlush >= FLUSH_INTERVAL_MS) {
                        outputStream.flush()
                        PacketCaptureSession.updateFileSize(outputFile?.length() ?: 0L)
                        lastFlush = now
                    }
                }
            }
        } catch (e: Exception) {
            // Expected when the TUN fd is closed on stop.
            if (isCapturing) Log.e(TAG, "Error in capture loop", e)
        } finally {
            try {
                inputStream.close()
            } catch (_: Exception) {
                // Ignore
            }
            PacketCaptureSession.publish(aggregator)
            PacketCaptureSession.stopped(outputFile?.length())
        }
    }

    private fun writePcapGlobalHeader(outputStream: OutputStream) {
        val header = ByteBuffer.allocate(24)
        header.order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(PCAP_MAGIC_NUMBER.toInt())
        header.putShort(PCAP_VERSION_MAJOR.toShort())
        header.putShort(PCAP_VERSION_MINOR.toShort())
        header.putInt(0) // thiszone
        header.putInt(0) // sigfigs
        header.putInt(65535) // snaplen
        header.putInt(PCAP_LINKTYPE_RAW) // network
        outputStream.write(header.array())
    }

    private fun writePcapPacketHeader(outputStream: OutputStream, length: Int) {
        val header = ByteBuffer.allocate(16)
        header.order(ByteOrder.LITTLE_ENDIAN)
        val now = System.currentTimeMillis()
        val tsSec = (now / 1000).toInt()
        val tsUsec = ((now % 1000) * 1000).toInt()

        header.putInt(tsSec)
        header.putInt(tsUsec)
        header.putInt(length) // incl_len
        header.putInt(length) // orig_len
        outputStream.write(header.array())
    }

    private fun stopCapture() {
        val wasCapturing = isCapturing
        isCapturing = false
        captureThread?.interrupt()
        try {
            vpnInterface?.close() // unblocks the read() in captureLoop
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface", e)
        }
        vpnInterface = null
        if (!wasCapturing) PacketCaptureSession.stopped()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() {
        // Another VPN took over or the user revoked permission from system settings.
        PacketCaptureSession.failed("Capture stopped: VPN permission revoked by the system")
        stopCapture()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopCapture()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Packet Capture",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }
}
