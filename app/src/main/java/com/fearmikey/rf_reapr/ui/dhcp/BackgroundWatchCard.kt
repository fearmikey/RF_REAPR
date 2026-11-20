package com.fearmikey.rf_reapr.ui.dhcp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fearmikey.rf_reapr.data.local.NetworkWatchStore
import com.fearmikey.rf_reapr.data.worker.NetworkWatchWorker
import kotlinx.coroutines.delay

/**
 * Opt-in toggle for [NetworkWatchWorker]: a sweep every ~15 minutes on unmetered networks
 * that notifies about devices never seen before on that network.
 */
@Composable
fun BackgroundWatchCard(isPassiveMode: Boolean) {
    val context = LocalContext.current
    val store = remember { NetworkWatchStore(context) }
    var enabled by remember { mutableStateOf(store.enabled) }
    var lastRun by remember { mutableStateOf(store.lastRunSummary) }

    // Refresh the "last run" line while the screen is visible.
    LaunchedEffect(enabled) {
        while (enabled) {
            lastRun = store.lastRunSummary
            delay(10_000)
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Background watch", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Sweep about every 15 min on Wi-Fi/unmetered networks, even when the app is closed, and notify when a never-seen device joins.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { on ->
                        enabled = on
                        store.enabled = on
                        if (on) NetworkWatchWorker.schedule(context) else NetworkWatchWorker.cancel(context)
                    }
                )
            }
            if (enabled) {
                if (isPassiveMode) {
                    Text("Passive (stealth) mode is on, so background sweeps are skipped.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Text(
                    lastRun?.let { "Last run: $it" } ?: "Waiting for the first run (the first sweep on each network only records a baseline).",
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = { store.clearBaselines(); lastRun = "Baselines cleared; the next sweep records a new one" }) {
                    Text("Reset background baselines")
                }
            }
        }
    }
}
