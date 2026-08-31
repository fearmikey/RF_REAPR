package com.fearmikey.rf_reapr.system

import android.app.Service
import android.content.Intent
import android.os.IBinder
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must be called within a few seconds of the service starting.
        startForeground(ScanNotificationManager.NOTIFICATION_ID, notificationManager.buildNotification())

        if (observerJob == null) {
            observerJob = serviceScope.launch {
                combine(ActiveTaskMonitor.activeTasks, ActiveTaskMonitor.statusText) { tasks, _ ->
                    tasks.isNotEmpty()
                }.collectLatest { hasActiveTasks ->
                    if (hasActiveTasks) {
                        notificationManager.updateNotification()
                    } else {
                        stopSelf()
                    }
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        observerJob?.cancel()
        observerJob = null
        notificationManager.cancelNotification()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
