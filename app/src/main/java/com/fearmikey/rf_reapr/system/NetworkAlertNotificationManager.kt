package com.fearmikey.rf_reapr.system

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.fearmikey.rf_reapr.MainActivity
import com.fearmikey.rf_reapr.R
import com.fearmikey.rf_reapr.domain.model.MonitoredDevice
import com.fearmikey.rf_reapr.domain.service.NetworkAlertNotifier

/**
 * Heads-up style alerts for the network monitor, on their own channel so users can tune
 * them separately from the low-priority "scan in progress" notification.
 */
class NetworkAlertNotificationManager(context: Context) : NetworkAlertNotifier {

    private val appContext = context.applicationContext
    private val notificationManager = NotificationManagerCompat.from(appContext)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Network Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "New or changed devices detected by the network monitor"
            }
            (appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun contentIntent(): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(appContext, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    override fun notifySecurityAlert(title: String, text: String, key: String) {
        if (!notificationManager.areNotificationsEnabled()) return
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()
        try {
            notificationManager.notify(NOTIFICATION_ID_BASE + 5_000 + key.hashCode() % 1000, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted.
        }
    }

    override fun notifyDeviceDetected(device: MonitoredDevice, reason: String) {
        if (!notificationManager.areNotificationsEnabled()) return

        val pendingIntent = contentIntent()

        val details = listOfNotNull(device.ipAddress, device.macAddress, device.manufacturer).joinToString(" · ")
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("${device.displayName} $reason")
            .setContentText(details)
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            // One notification per IP so repeated alerts for the same host replace each other.
            notificationManager.notify(NOTIFICATION_ID_BASE + device.ipAddress.hashCode(), notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted.
        }
    }

    private companion object {
        const val CHANNEL_ID = "network_alerts_channel"
        const val NOTIFICATION_ID_BASE = 20_000
    }
}
