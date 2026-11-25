package com.fearmikey.rf_reapr.ui.menu

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fearmikey.rf_reapr.domain.model.AUDIT_TASK_NAME
import com.fearmikey.rf_reapr.domain.service.ActiveTaskMonitor

/** Main-menu entry for Audit Mode; shows live progress while an audit is running. */
@Composable
fun AuditModeCard(onClick: () -> Unit) {
    val tasks by ActiveTaskMonitor.activeTasks.collectAsStateWithLifecycle()
    val status by ActiveTaskMonitor.statusText.collectAsStateWithLifecycle()
    val running = AUDIT_TASK_NAME in tasks
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (running) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.AutoMirrored.Filled.FactCheck, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Audit Mode",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    if (running) status?.takeIf { it.startsWith(AUDIT_TASK_NAME) } ?: "Audit running…"
                    else "Pick checks and run them as one graded audit of this network",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}
