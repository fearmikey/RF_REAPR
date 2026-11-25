package com.fearmikey.rf_reapr.ui.audit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.model.AuditRun
import com.fearmikey.rf_reapr.domain.repository.AuditRunRepository
import com.fearmikey.rf_reapr.domain.service.ActiveEngagement
import com.fearmikey.rf_reapr.domain.service.AuditRunDiff
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A stored run with how it differs from the run before it (null for the oldest run). */
data class AuditHistoryEntry(val run: AuditRun, val diff: AuditRunDiff.Result?)

class AuditHistoryViewModel(private val auditRuns: AuditRunRepository) : ViewModel() {

    /** Newest first; each entry is compared with the next older run of the same project. */
    val entries: StateFlow<List<AuditHistoryEntry>> = auditRuns.observeActive()
        .map { runs ->
            runs.mapIndexed { index, run ->
                AuditHistoryEntry(run, runs.getOrNull(index + 1)?.let { previous -> AuditRunDiff.compare(previous, run) })
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val projectName: StateFlow<String?> = ActiveEngagement.current.map { it?.name }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** The run's report as generated at the time, plus the comparison with the previous run. */
    fun reportFor(entry: AuditHistoryEntry): String =
        entry.run.reportMarkdown.trimEnd() + (entry.diff?.let { "\n\n" + AuditRunDiff.markdown(it) } ?: "\n")

    fun delete(run: AuditRun) = viewModelScope.launch { auditRuns.delete(run.id) }
}
