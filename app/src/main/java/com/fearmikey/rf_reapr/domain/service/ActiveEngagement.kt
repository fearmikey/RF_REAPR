package com.fearmikey.rf_reapr.domain.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The active project (engagement) and its parsed scope. */
data class EngagementContext(
    val projectId: String,
    val name: String,
    val scopeText: String,
    val scope: ScopeMatcher
)

/**
 * Process-wide view of the active engagement, like [ActiveTaskMonitor]. Written only by
 * ActiveProjectStore; read by repositories (to tag logs/scans/findings) and by the
 * shared out-of-scope warning composable.
 */
object ActiveEngagement {
    private val _current = MutableStateFlow<EngagementContext?>(null)
    val current: StateFlow<EngagementContext?> = _current.asStateFlow()

    fun check(target: String): ScopeMatcher.Verdict =
        _current.value?.scope?.check(target) ?: ScopeMatcher.Verdict.NO_SCOPE

    internal fun publish(context: EngagementContext?) {
        _current.value = context
    }
}
