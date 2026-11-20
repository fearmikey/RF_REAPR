package com.fearmikey.rf_reapr.ui.ping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.repository.LogRepository
import com.fearmikey.rf_reapr.domain.repository.NetworkDiscoveryRepository
import com.fearmikey.rf_reapr.domain.repository.PingOptions
import com.fearmikey.rf_reapr.domain.repository.PingRepository
import com.fearmikey.rf_reapr.domain.repository.PingRepository.PingStatus
import com.fearmikey.rf_reapr.domain.repository.SettingsRepository
import com.fearmikey.rf_reapr.domain.service.ActiveTaskMonitor
import com.fearmikey.rf_reapr.domain.service.HostValidator
import com.fearmikey.rf_reapr.domain.service.PingOutputParser
import com.fearmikey.rf_reapr.domain.service.PingReply
import com.fearmikey.rf_reapr.domain.service.PingStats
import com.google.gson.Gson
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** UI-selectable count presets; null = continuous. */
val PingCountPresets: List<Int?> = listOf(4, 10, 50, null)
val PingIntervalPresets: List<Float> = listOf(1f, 0.5f, 0.2f)
val PingSizePresets: List<Int> = listOf(56, 512, 1472)

class PingViewModel(
    private val repository: PingRepository,
    private val logRepository: LogRepository,
    settingsRepository: SettingsRepository,
    private val discoveryRepository: NetworkDiscoveryRepository? = null
) : ViewModel() {

    val isPassiveMode: StateFlow<Boolean> = settingsRepository.isPassiveMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _uiState = MutableStateFlow<PingStatus>(PingStatus.Idle)
    val uiState: StateFlow<PingStatus> = _uiState.asStateFlow()

    private val _host = MutableStateFlow("1.1.1.1")
    val host: StateFlow<String> = _host.asStateFlow()

    private val _options = MutableStateFlow(PingOptions(host = ""))
    val options: StateFlow<PingOptions> = _options.asStateFlow()

    private val _pingLines = MutableStateFlow<List<String>>(emptyList())
    val pingLines: StateFlow<List<String>> = _pingLines.asStateFlow()

    private val _replies = MutableStateFlow<List<PingReply>>(emptyList())
    val replies: StateFlow<List<PingReply>> = _replies.asStateFlow()

    private val _stats = MutableStateFlow<PingStats?>(null)
    val stats: StateFlow<PingStats?> = _stats.asStateFlow()

    /** Highest icmp_seq seen (replies or errors), used to plot gaps for lost packets. */
    private val _lastSeq = MutableStateFlow(0)
    val lastSeq: StateFlow<Int> = _lastSeq.asStateFlow()

    private val _gatewayIp = MutableStateFlow<String?>(null)
    val gatewayIp: StateFlow<String?> = _gatewayIp.asStateFlow()

    private var pingJob: Job? = null
    private val gson = Gson()
    private var errorSeqs = mutableSetOf<Int>()
    private var summary: Pair<Int, Int>? = null
    private var currentHost: String = ""
    private var loggedCurrentRun = false

    init {
        viewModelScope.launch {
            ActiveTaskMonitor.stopAllSignal.collect { cancelPing() }
        }
        discoveryRepository?.let { repo ->
            viewModelScope.launch {
                _gatewayIp.value = runCatching { repo.getLocalNetworkInfo().gatewayIp }.getOrNull()
            }
        }
    }

    fun setHost(value: String) {
        _host.value = value
    }

    /** Pre-fills the target when opened from another tool (e.g. a traceroute hop). */
    fun setInitialHost(value: String?) {
        if (!value.isNullOrBlank() && !value.startsWith("{") && pingJob?.isActive != true) {
            _host.value = value
        }
    }

    fun updateOptions(transform: (PingOptions) -> PingOptions) {
        _options.update(transform)
    }

    fun runPing() {
        val target = HostValidator.normalize(_host.value)
        if (target == null) {
            _uiState.value = PingStatus.Error("Enter a valid hostname or IP address")
            return
        }
        if (isPassiveMode.value) return

        cancelPing()
        resetResults()
        currentHost = target
        _host.value = target
        loggedCurrentRun = false

        val taskName = "Ping ($target)"
        ActiveTaskMonitor.addTask(taskName)

        pingJob = viewModelScope.launch {
            try {
                repository.ping(_options.value.copy(host = target)).collect { status ->
                    when (status) {
                        is PingStatus.Progress -> handleLine(status.line)
                        is PingStatus.Success -> {
                            _uiState.value = status
                            logPingResults()
                        }
                        else -> _uiState.value = status
                    }
                }
            } finally {
                ActiveTaskMonitor.removeTask(taskName)
                withContext(NonCancellable) { logPingResults() }
            }
        }
    }

    private fun handleLine(line: String) {
        _uiState.value = PingStatus.Progress(line)
        _pingLines.update { (it + line).takeLast(MAX_CONSOLE_LINES) }

        PingOutputParser.parseReply(line)?.let { reply ->
            _replies.update { it + reply }
            _lastSeq.update { maxOf(it, reply.seq) }
        }
        PingOutputParser.parseErrorSeq(line)?.let { seq ->
            errorSeqs.add(seq)
            _lastSeq.update { maxOf(it, seq) }
        }
        PingOutputParser.parseSummary(line)?.let { summary = it }

        _stats.value = PingOutputParser.computeStats(_replies.value, errorSeqs, summary)
    }

    private suspend fun logPingResults() {
        val stats = _stats.value
        if (loggedCurrentRun || currentHost.isEmpty() || (stats == null && _pingLines.value.isEmpty())) return
        loggedCurrentRun = true
        val summaryText = stats?.let {
            "Pinged $currentHost: ${it.received}/${it.transmitted} replies, " +
                "%.0f%% loss".format(it.lossPercent) +
                (it.avgMs?.let { avg -> ", avg %.1f ms".format(avg) } ?: "")
        } ?: "Pinged $currentHost"
        logRepository.saveLog(
            type = "PING",
            summary = summaryText,
            detailJson = gson.toJson(
                mapOf(
                    "host" to currentHost,
                    "options" to _options.value.copy(host = currentHost),
                    "stats" to stats,
                    "lossPercent" to stats?.lossPercent,
                    "lines" to _pingLines.value
                )
            )
        )
    }

    fun cancelPing() {
        pingJob?.cancel()
        pingJob = null
        if (_uiState.value is PingStatus.Loading || _uiState.value is PingStatus.Progress) {
            _uiState.value = PingStatus.Idle
        }
    }

    fun reset() {
        cancelPing()
        resetResults()
        _uiState.value = PingStatus.Idle
    }

    private fun resetResults() {
        _pingLines.value = emptyList()
        _replies.value = emptyList()
        _stats.value = null
        _lastSeq.value = 0
        errorSeqs = mutableSetOf()
        summary = null
    }

    private companion object {
        const val MAX_CONSOLE_LINES = 500
    }
}
