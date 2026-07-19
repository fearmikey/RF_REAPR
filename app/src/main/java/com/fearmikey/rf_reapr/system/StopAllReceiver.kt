package com.fearmikey.rf_reapr.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fearmikey.rf_reapr.domain.service.ActiveTaskMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class StopAllReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        CoroutineScope(Dispatchers.Main).launch {
            ActiveTaskMonitor.stopAll()
            val notificationManager = ScanNotificationManager(context)
            notificationManager.cancelNotification()
        }
    }
}
