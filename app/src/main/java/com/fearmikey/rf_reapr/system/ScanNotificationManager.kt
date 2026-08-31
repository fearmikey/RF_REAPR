package com.fearmikey.rf_reapr.system

import android.app.Notification
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
import com.fearmikey.rf_reapr.domain.service.ActiveTaskMonitor

class ScanNotificationManager(private val context: Context) {
    private val notificationManager = NotificationManagerCompat.from(context)

    companion object {
        private const val CHANNEL_ID = "background_audit_channel"
        const val NOTIFICATION_ID = 1001
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Background Audit"
            val descriptionText = "Notifications for active background network audits"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Builds the (live) notification content from [ActiveTaskMonitor]'s current state.
     * Called both when the foreground service starts and every time task/status state
     * changes, so the notification content stays up to date for the duration of a scan.
     */
    fun buildNotification(): Notification {
        val activeTasks = ActiveTaskMonitor.activeTasks.value
        val statusText = ActiveTaskMonitor.statusText.value
        val contentText = when {
            !statusText.isNullOrBlank() -> statusText
            activeTasks.isNotEmpty() -> "Active: ${activeTasks.joinToString(", ")}"
            else -> "Running..."
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(context, StopAllReceiver::class.java)
        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Using foreground launcher icon as fallback
            .setContentTitle("Network Audit in Progress")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Stop All Tasks", stopPendingIntent)
            .build()
    }

    /**
     * Posts/updates the notification with the latest content, if there's anything active
     * to show. Safe to call repeatedly (e.g. on every progress tick).
     */
    fun updateNotification() {
        if (!ActiveTaskMonitor.hasActiveTasks()) {
            cancelNotification()
            return
        }
        try {
            notificationManager.notify(NOTIFICATION_ID, buildNotification())
        } catch (_: SecurityException) {
            // Permission missing
        }
    }

    @Deprecated("Use updateNotification(), kept for compatibility.", ReplaceWith("updateNotification()"))
    fun showBackgroundNotification() = updateNotification()

    fun cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
