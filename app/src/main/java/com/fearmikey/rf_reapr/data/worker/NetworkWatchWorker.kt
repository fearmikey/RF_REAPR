package com.fearmikey.rf_reapr.data.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fearmikey.rf_reapr.RfReaprApplication
import com.fearmikey.rf_reapr.data.local.NetworkWatchStore
import com.fearmikey.rf_reapr.domain.scanner.NetworkScanner
import com.fearmikey.rf_reapr.domain.service.NetworkWatchEvaluator
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Opt-in background watch: periodically sweeps the current subnet and notifies when a
 * device appears that has never been seen on this network before. Only runs on unmetered
 * networks with a healthy battery, and never while passive (stealth) mode is on.
 */
class NetworkWatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = NetworkWatchStore(applicationContext)
        if (!store.enabled) return Result.success()
        val container = (applicationContext as RfReaprApplication).container

        if (container.settingsRepository.isPassiveMode.first()) {
            store.lastRunSummary = "${now()}: skipped (passive mode is on)"
            return Result.success()
        }

        return try {
            val info = container.discoveryRepository.getLocalNetworkInfo()
            val finished = withTimeoutOrNull(SWEEP_TIMEOUT_MS) {
                container.discoveryRepository.discoverDevices()
                    .filter { it is NetworkScanner.ScanResult.Finished || it is NetworkScanner.ScanResult.Error }
                    .first()
            }
            val nodes = (finished as? NetworkScanner.ScanResult.Finished)?.foundData
                ?: run {
                    store.lastRunSummary = "${now()}: sweep failed" + ((finished as? NetworkScanner.ScanResult.Error)?.let { " (${it.message})" } ?: " (timed out)")
                    return Result.success()
                }

            val networkKey = NetworkWatchEvaluator.networkKey(info.gatewayIp, info.prefixLength, info.localIp)
            val seen = nodes.filter { it.ipAddress != info.localIp }
                .map { NetworkWatchEvaluator.SeenDevice(it.ipAddress, it.macAddress, it.hostname) }
            val result = NetworkWatchEvaluator.evaluate(store.baseline(networkKey), seen)
            store.saveBaseline(networkKey, result.updatedBaseline)

            if (result.newDevices.isNotEmpty()) {
                val lines = result.newDevices.joinToString("\n") { d ->
                    listOfNotNull(d.ip, d.hostname, d.mac).joinToString(" · ")
                }
                container.networkAlertNotifier.notifySecurityAlert(
                    title = if (result.newDevices.size == 1) "New device on your network" else "${result.newDevices.size} new devices on your network",
                    text = lines,
                    key = "network_watch_$networkKey"
                )
                container.logRepository.saveLog(
                    type = "TOPOLOGY",
                    summary = "Background watch: ${result.newDevices.size} new device(s) on ${info.gatewayIp ?: "network"}",
                    detailJson = com.google.gson.Gson().toJson(mapOf("tool" to "Background network watch", "newDevices" to result.newDevices))
                )
            }
            store.lastRunSummary = "${now()}: ${seen.size} devices" + when {
                result.firstSweep -> " (baseline recorded)"
                result.newDevices.isNotEmpty() -> ", ${result.newDevices.size} new"
                else -> ", nothing new"
            }
            Result.success()
        } catch (e: Exception) {
            store.lastRunSummary = "${now()}: error (${e.message})"
            Result.retry()
        }
    }

    private fun now() = SimpleDateFormat("MMM d HH:mm", Locale.getDefault()).format(Date())

    companion object {
        private const val WORK_NAME = "NetworkWatch"
        private const val SWEEP_TIMEOUT_MS = 4 * 60_000L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<NetworkWatchWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
