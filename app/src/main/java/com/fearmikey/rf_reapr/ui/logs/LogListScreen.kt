package com.fearmikey.rf_reapr.ui.logs

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fearmikey.rf_reapr.data.db.entity.EventLogEntity
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogListScreen(
    type: String,
    title: String,
    viewModel: LogViewModel,
    onBack: () -> Unit
) {
    val logs by viewModel.logs.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(type) {
        viewModel.loadLogs(type)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.clearLogs(type) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear All")
                    }
                }
            )
        }
    ) { padding ->
        if (logs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No logs found for $type", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(logs) { log ->
                    LogCard(
                        log = log,
                        onDelete = { viewModel.deleteLog(log.id) },
                        onExport = {
                            val sendIntent: Intent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, "Log Type: ${log.type}\nDate: ${Date(log.timestamp)}\nSummary: ${log.summary}\nDetails: ${log.detailJson}")
                                setType("text/plain")
                            }
                            val shareIntent = Intent.createChooser(sendIntent, "Export Log")
                            context.startActivity(shareIntent)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun LogCard(
    log: EventLogEntity,
    onDelete: () -> Unit,
    onExport: () -> Unit
) {
    val date = remember(log.timestamp) {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = date, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Row {
                    IconButton(onClick = onExport) {
                        Icon(Icons.Default.Share, contentDescription = "Export")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = log.summary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "JSON size: ${log.detailJson.length} chars",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
