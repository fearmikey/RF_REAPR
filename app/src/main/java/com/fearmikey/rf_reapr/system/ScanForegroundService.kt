package com.fearmikey.rf_reapr.system

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.fearmikey.rf_reapr.domain.repository.SURVEY_TASK_NAME
import com.fearmikey.rf_reapr.domain.service.ActiveTaskMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * A lightweight foreground [Service] that keeps the process alive and shows a live status
 * notification for as long as [ActiveTaskMonitor] reports at least one active scan/audit
 * task. It is started by [com.fearmikey.rf_reapr.RfReaprApplication] whenever a task
 * begins, and stops itself once [ActiveTaskMonitor] becomes empty.
 *
 * This exists so that long-running scans (network discovery, port scans, automated recon
 * workflows, etc.) reliably keep running - and keep the user informed via the
 * notification bar - even while the app is backgrounded, instead of relying solely on
 * `ViewModel` coroutines plus a best-effort notification shown on `Activity.onStop()`.
 */
class ScanForegroundService : Service() {

    private lateinit var notificationManager: ScanNotificationManager
    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var observerJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = ScanNotificationManager(this)
    }

    /** Foreground service types currently declared to the system (0 = not promoted yet). */
    private var foregroundTypes = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must be called within a few seconds of the service starting.
        promote(ActiveTaskMonitor.activeTasks.value, force = true)

        if (observerJob == null) {
            observerJob = serviceScope.launch {
                combine(ActiveTaskMonitor.activeTasks, ActiveTaskMonitor.statusText) { tasks, _ ->
                    tasks
                }.collectLatest { tasks ->
                    if (tasks.isNotEmpty()) {
                        // A geotagged survey starting (or ending) changes the required type.
                        promote(tasks, force = false)
                        notificationManager.updateNotification()
                    } else {
                        stopSelf()
                    }
                }
            }
        }

        return START_NOT_STICKY
    }

    /**
     * Enters (or re-enters) the foreground with the types the running tasks need. The
     * `location` type is only requested while a geotagged survey is active and the
     * permission is granted: requesting it otherwise throws on Android 14+.
     */
    private fun promote(tasks: Set<String>, force: Boolean) {
        val wantsLocation = SURVEY_TASK_NAME in tasks &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val types = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or (if (wantsLocation) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)
        } else {
            0
        }
        if (!force && types == foregroundTypes) return
        val notification = notificationManager.buildNotification()
        try {
            ServiceCompat.startForeground(this, ScanNotificationManager.NOTIFICATION_ID, notification, types)
            foregroundTypes = types
        } catch (e: Exception) {
            // The location type can be refused if the app left the foreground first. Scans must
            // still be kept alive, so fall back to the plain data-sync type.
            val fallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
            runCatching { ServiceCompat.startForeground(this, ScanNotificationManager.NOTIFICATION_ID, notification, fallback) }
            foregroundTypes = fallback
        }
    }

    override fun onDestroy() {
        observerJob?.cancel()
        observerJob = null
        notificationManager.cancelNotification()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
