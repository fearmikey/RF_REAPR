package com.fearmikey.rf_reapr.system

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.fearmikey.rf_reapr.MainActivity
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PcapVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var captureThread: Thread? = null
    private var isCapturing = false
    private var outputFile: File? = null

    companion object {
        const val ACTION_START = "com.fearmikey.rf_reapr.START_PCAP"
        const val ACTION_STOP = "com.fearmikey.rf_reapr.STOP_PCAP"
        private const val TAG = "PcapVpnService"
        private const val NOTIFICATION_ID = 888
        private const val CHANNEL_ID = "pcap_capture_channel"
        
        // PCAP Constants
        private const val PCAP_MAGIC_NUMBER = 0xa1b2c3d4L
        private const val PCAP_VERSION_MAJOR = 2
        private const val PCAP_VERSION_MINOR = 4
        private const val PCAP_LINKTYPE_RAW = 101 // Raw IP
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
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Packet Capture Active")
            .setContentText("RF_REAPR is capturing network traffic")
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Placeholder
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "capture_$timestamp.pcap"
        val pcapDir = File(filesDir, "pcaps")
        if (!pcapDir.exists()) pcapDir.mkdirs()
        outputFile = File(pcapDir, fileName)

        setupVpn()
    }

    private fun setupVpn() {
        val builder = Builder()
        builder.setSession("RF_REAPR Capture")
        builder.addAddress("10.0.0.2", 24)
        builder.addRoute("0.0.0.0", 0)

        try {
            vpnInterface = builder.establish()
            isCapturing = true
            captureThread = Thread { captureLoop() }
            captureThread?.start()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to establish VPN", e)
            stopSelf()
        }
    }

    private fun captureLoop() {
        val vpnFd = vpnInterface?.fileDescriptor ?: return
        val inputStream = FileInputStream(vpnFd)
        
        try {
            FileOutputStream(outputFile).use { outputStream ->
                writePcapGlobalHeader(outputStream)
                
                // Max size of an IP packet is 65535 bytes
                val buffer = ByteArray(65535) 
                
                while (isCapturing && !Thread.currentThread().isInterrupted) {
                    val length = inputStream.read(buffer)
                    if (length > 0) {
                        writePcapPacketHeader(outputStream, length)
                        outputStream.write(buffer, 0, length)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in capture loop", e)
        } finally {
            try {
                inputStream.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun writePcapGlobalHeader(outputStream: FileOutputStream) {
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

    private fun writePcapPacketHeader(outputStream: FileOutputStream, length: Int) {
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
        isCapturing = false
        captureThread?.interrupt()
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface", e)
        }
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCapture()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Packet Capture",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
