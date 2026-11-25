package com.fearmikey.rf_reapr.ui.engagement

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.repository.EngagementRepository
import com.fearmikey.rf_reapr.domain.service.TimelineBuilder
import com.fearmikey.rf_reapr.domain.service.TimelineEvent
import com.fearmikey.rf_reapr.domain.service.TimelineKind
import com.fearmikey.rf_reapr.ui.components.exportFileName
import com.fearmikey.rf_reapr.ui.components.writeTextToUri
import com.fearmikey.rf_reapr.ui.scanner.SeverityBadge
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class TimelineViewModel(private val repository: EngagementRepository) : ViewModel() {

    private val all: StateFlow<List<TimelineEvent>> = repository.activeProjectId
        .flatMapLatest { repository.observeTimeline(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _kinds = MutableStateFlow(emptySet<TimelineKind>())
    /** Kinds to show; empty = everything. */
    val kinds: StateFlow<Set<TimelineKind>> = _kinds

    /** Filtered events, newest first, grouped by day. */
    val days: StateFlow<List<Pair<Long, List<TimelineEvent>>>> = combine(all, _kinds) { events, kinds ->
        TimelineBuilder.groupByDay(events.filter { kinds.isEmpty() || it.kind in kinds })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val projectName: StateFlow<String?> = repository.observeActiveProject().map { it?.name }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun toggle(kind: TimelineKind) = _kinds.update { if (kind in it) it - kind else it + kind }

    /** The full (unfiltered) timeline as a Markdown appendix. */
    fun buildMarkdown(): String = TimelineBuilder.toMarkdown(projectName.value ?: "Unassigned work", all.value)
}

/** Everything recorded under the active engagement, in the order it happened. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(viewModel: TimelineViewModel, onBack: () -> Unit) {
    val days by viewModel.days.collectAsStateWithLifecycle()
    val kinds by viewModel.kinds.collectAsStateWithLifecycle()
    val projectName by viewModel.projectName.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null) {
            scope.launch { snackbar.showSnackbar(writeTextToUri(context, uri, viewModel.buildMarkdown()) ?: "Timeline exported") }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Activity timeline")
                        Text(
                            projectName ?: "No active project (all activity)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { exportLauncher.launch(exportFileName(projectName ?: "rf-reapr", "timeline", "md")) }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export timeline as Markdown")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "filters") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TimelineKind.entries.forEach { kind ->
                        FilterChip(selected = kind in kinds, onClick = { viewModel.toggle(kind) }, label = { Text(kind.label) })
                    }
                }
            }
            if (days.isEmpty()) {
                item(key = "empty") {
                    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Timeline, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("Nothing recorded yet", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Scans, tool runs, findings, audits and evidence captured under this project appear here in order.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            days.forEach { (dayStart, events) ->
                item(key = "day_$dayStart") {
                    Text(
                        SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date(dayStart)),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(events) { event -> TimelineRow(event) }
            }
        }
    }
}

@Composable
private fun TimelineRow(event: TimelineEvent) {
    val icon = when (event.kind) {
        TimelineKind.SCAN -> Icons.Default.Router
        TimelineKind.LOG -> Icons.Default.Terminal
        TimelineKind.FINDING -> Icons.Default.BugReport
        TimelineKind.EVIDENCE -> Icons.Default.PhotoCamera
        TimelineKind.AUDIT -> Icons.Default.FactCheck
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(event.timestamp)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(44.dp).padding(top = 2.dp)
        )
        Icon(icon, contentDescription = event.kind.label, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                event.severity?.let { SeverityBadge(it) }
                Text(event.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
            event.detail?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
