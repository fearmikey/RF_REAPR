package com.fearmikey.rf_reapr

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import com.fearmikey.rf_reapr.domain.service.ActiveTaskMonitor
import com.fearmikey.rf_reapr.system.ScanForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Application-scoped entry point.
 *
 * Observes [ActiveTaskMonitor] for the entire process lifetime (independent of any single
 * `Activity`/`ViewModel`) so that starting a scan reliably promotes the app into a real
 * foreground service - keeping it alive and visible in the notification shade - even if
 * the user immediately backgrounds the app or the hosting screen is torn down.
 */
class RfReaprApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        applicationScope.launch {
            ActiveTaskMonitor.activeTasks
                .map { it.isNotEmpty() }
                .distinctUntilChanged()
                .collect { hasActiveTasks ->
                    if (hasActiveTasks) {
                        val intent = Intent(this@RfReaprApplication, ScanForegroundService::class.java)
                        ContextCompat.startForegroundService(this@RfReaprApplication, intent)
                    }
                    // No explicit stop call here: ScanForegroundService stops itself once
                    // ActiveTaskMonitor reports no active tasks.
                }
        }
    }
}
