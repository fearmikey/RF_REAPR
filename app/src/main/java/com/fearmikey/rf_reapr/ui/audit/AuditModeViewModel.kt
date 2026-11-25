package com.fearmikey.rf_reapr.ui.audit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.data.audit.AuditEngine
import com.fearmikey.rf_reapr.domain.model.AuditCheck
import com.fearmikey.rf_reapr.domain.model.AuditConfig
import com.fearmikey.rf_reapr.domain.model.AuditPreset
import com.fearmikey.rf_reapr.domain.model.AuditRunState
import com.fearmikey.rf_reapr.domain.model.AuditTargets
import com.fearmikey.rf_reapr.domain.model.PortDepth
import com.fearmikey.rf_reapr.domain.repository.AuditRunRepository
import com.fearmikey.rf_reapr.domain.repository.SettingsRepository
import com.fearmikey.rf_reapr.domain.service.AuditRunDiff
import kotlinx.coroutines.flow.flowOf
import com.fearmikey.rf_reapr.domain.service.ActiveEngagement
import com.fearmikey.rf_reapr.domain.service.AuditPlanner
import com.fearmikey.rf_reapr.domain.service.AuditReportBuilder
import com.fearmikey.rf_reapr.domain.service.EngagementContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** What the user is configuring before pressing Start. */
data class AuditDraft(
    val selected: Set<AuditCheck> = AuditPreset.QUICK_POSTURE.checks,
    val useTargetList: Boolean = false,
    val targetText: String = "",
    val portDepth: PortDepth = PortDepth.QUICK,
    val respectScope: Boolean = true
)

class AuditModeViewModel(
    private val engine: AuditEngine,
    settingsRepository: SettingsRepository,
    private val appVersion: String,
    auditRuns: AuditRunRepository? = null
) : ViewModel() {

    val run: StateFlow<AuditRunState> = engine.state

    /**
     * How the run on screen differs from the previous stored run of this project; null while
     * running, or when there is no earlier run to compare with.
     */
    val comparison: StateFlow<AuditRunDiff.Result?> =
        (auditRuns?.let { repository ->
            combine(engine.state, repository.observeActive()) { state, runs ->
                if (state.isRunning || state.startedAt == null) return@combine null
                val latest = runs.firstOrNull { it.startedAt == state.startedAt } ?: return@combine null
                val previous = runs.firstOrNull { it.finishedAt < latest.startedAt } ?: return@combine null
                AuditRunDiff.compare(previous, latest)
            }
        } ?: flowOf(null)).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val engagement: StateFlow<EngagementContext?> = ActiveEngagement.current

    val isPassiveMode: StateFlow<Boolean> = settingsRepository.isPassiveMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _draft = MutableStateFlow(AuditDraft())
    val draft: StateFlow<AuditDraft> = _draft.asStateFlow()

    val parsedTargets: StateFlow<AuditPlanner.ParsedTargets> = _draft
        .map { AuditPlanner.parseTargets(it.targetText) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AuditPlanner.ParsedTargets(emptyList(), emptyList(), false))

    val plan: StateFlow<List<AuditPlanner.PlannedStep>> = combine(_draft, isPassiveMode) { d, passive ->
        AuditPlanner.plan(d.toConfig(), passive)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun applyPreset(preset: AuditPreset) = _draft.update { it.copy(selected = preset.checks) }

    fun toggle(check: AuditCheck) = _draft.update {
        it.copy(selected = if (check in it.selected) it.selected - check else it.selected + check)
    }

    fun setUseTargetList(value: Boolean) = _draft.update { it.copy(useTargetList = value) }
    fun setTargetText(value: String) = _draft.update { it.copy(targetText = value) }
    fun setPortDepth(value: PortDepth) = _draft.update { it.copy(portDepth = value) }
    fun setRespectScope(value: Boolean) = _draft.update { it.copy(respectScope = value) }

    fun start() {
        val config = _draft.value.toConfig()
        val steps = AuditPlanner.plan(config, isPassiveMode.value)
        when {
            config.checks.isEmpty() -> showMessage("Select at least one check")
            steps.none { it.skipReason == null } -> showMessage("Nothing can run with the current selection")
            _draft.value.useTargetList && parsedTargets.value.invalid.isNotEmpty() ->
                showMessage("Fix the invalid targets first: ${parsedTargets.value.invalid.take(3).joinToString()}")
            !engine.start(config) -> showMessage("An audit is already running")
        }
    }

    fun cancel() = engine.cancel()

    /** Back to the setup screen, keeping the previous selection. */
    fun newAudit() = engine.clear()

    fun buildReport(): String = AuditReportBuilder.markdown(run.value, appVersion)

    fun showMessage(text: String) {
        _message.value = text
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun AuditDraft.toConfig() = AuditConfig(
        checks = selected,
        targets = if (useTargetList) AuditTargets.Manual(AuditPlanner.parseTargets(targetText).hosts) else AuditTargets.LocalNetwork,
        portDepth = portDepth,
        respectScope = respectScope
    )
}
