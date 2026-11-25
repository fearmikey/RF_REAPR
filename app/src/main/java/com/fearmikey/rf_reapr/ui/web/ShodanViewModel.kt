package com.fearmikey.rf_reapr.ui.web

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.model.InternetDbResponse
import com.fearmikey.rf_reapr.domain.model.ShodanHostReport
import com.fearmikey.rf_reapr.domain.model.ShodanMatch
import com.fearmikey.rf_reapr.domain.repository.FindingRepository
import com.fearmikey.rf_reapr.domain.repository.InternetDbRepository
import com.fearmikey.rf_reapr.domain.repository.LogRepository
import com.fearmikey.rf_reapr.domain.repository.SettingsRepository
import com.fearmikey.rf_reapr.domain.repository.ShodanRepository
import com.fearmikey.rf_reapr.domain.service.HostValidator
import com.fearmikey.rf_reapr.domain.service.PublicIpGuard
import com.fearmikey.rf_reapr.domain.service.ShodanAnalyzer
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress

class ShodanViewModel(
    private val repository: ShodanRepository,
    private val internetDbRepository: InternetDbRepository,
    private val settingsRepository: SettingsRepository,
    private val logRepository: LogRepository,
    private val findingRepository: FindingRepository? = null
) : ViewModel() {

    private val gson = Gson()

    private val _uiState = MutableStateFlow<ShodanUiState>(ShodanUiState.Idle)
    val uiState: StateFlow<ShodanUiState> = _uiState.asStateFlow()

    val isApiKeyMissing: StateFlow<Boolean> = settingsRepository.shodanApiKey
        .map { it.isBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private var job: Job? = null

    /** Full host report (needs an API key). */
    fun getHostInfo(input: String) = lookup(input, requiresKey = true) { ip ->
        repository.getHostInfo(ip).fold(
            onSuccess = { report ->
                val drafts = ShodanAnalyzer.toDrafts(report)
                if (drafts.isNotEmpty()) findingRepository?.record(drafts)
                logRepository.saveLog("SHODAN", "Shodan Host Recon: ${report.ip}", gson.toJson(ShodanAnalyzer.hostLog(report)))
                ShodanUiState.HostSuccess(report, drafts.size)
            },
            onFailure = { ShodanUiState.Error(it.message ?: "Shodan lookup failed.") }
        )
    }

    /** Free InternetDB summary (no key). */
    fun getInternetDbInfo(input: String) = lookup(input, requiresKey = false) { ip ->
        internetDbRepository.getIpInfo(ip).fold(
            onSuccess = { data ->
                val drafts = ShodanAnalyzer.toDrafts(data)
                if (drafts.isNotEmpty()) findingRepository?.record(drafts)
                logRepository.saveLog("SHODAN", "InternetDB Quick Recon: ${data.ip}", gson.toJson(ShodanAnalyzer.internetDbLog(data)))
                ShodanUiState.InternetDbSuccess(data, drafts.size)
            },
            onFailure = { ShodanUiState.Error(it.message ?: "InternetDB lookup failed.") }
        )
    }

    private fun lookup(input: String, requiresKey: Boolean, fetch: suspend (ip: String) -> ShodanUiState) {
        val target = input.trim()
        if (target.isEmpty() || _uiState.value is ShodanUiState.Loading) return
        job?.cancel()
        job = viewModelScope.launch {
            if (requiresKey && settingsRepository.shodanApiKey.first().isBlank()) {
                _uiState.value = ShodanUiState.Error("Set your Shodan API key under Settings → API Keys first.")
                return@launch
            }
            _uiState.value = ShodanUiState.Loading
            _uiState.value = when (val resolved = resolve(target)) {
                is Resolved.Failure -> ShodanUiState.Error(resolved.message)
                is Resolved.Ip -> fetch(resolved.ip)
            }
        }
    }

    fun search(query: String) {
        val text = query.trim()
        if (text.isEmpty() || _uiState.value is ShodanUiState.Loading) return
        job?.cancel()
        job = viewModelScope.launch {
            _uiState.value = ShodanUiState.Loading
            _uiState.value = repository.search(text, 1).fold(
                onSuccess = { response ->
                    logRepository.saveLog("SHODAN", "Shodan Search: $text", gson.toJson(ShodanAnalyzer.searchLog(text, response)))
                    ShodanUiState.SearchSuccess(text, response.total, response.matches, page = 1)
                },
                onFailure = { ShodanUiState.Error(it.message ?: "Shodan search failed.") }
            )
        }
    }

    /** Appends the next page of the current search (each page costs a query credit). */
    fun loadMore() {
        val current = _uiState.value as? ShodanUiState.SearchSuccess ?: return
        if (current.isLoadingMore || !current.hasMore) return
        _uiState.value = current.copy(isLoadingMore = true, loadMoreError = null)
        job?.cancel()
        job = viewModelScope.launch {
            _uiState.value = repository.search(current.query, current.page + 1).fold(
                onSuccess = { next ->
                    current.copy(
                        matches = current.matches + next.matches,
                        page = current.page + 1,
                        isLoadingMore = false,
                        // An empty page means Shodan has nothing more to give, whatever the total says.
                        exhausted = next.matches.isEmpty()
                    )
                },
                onFailure = { current.copy(isLoadingMore = false, loadMoreError = it.message ?: "Could not load more results.") }
            )
        }
    }

    private sealed class Resolved {
        data class Ip(val ip: String) : Resolved()
        data class Failure(val message: String) : Resolved()
    }

    /**
     * Turns the input into a public IP. Hostnames are resolved with the device's DNS; private
     * and other non-routable addresses are refused locally instead of being sent to Shodan.
     */
    private suspend fun resolve(input: String): Resolved {
        val literal = PublicIpGuard.check(input)
        val verdict = if (literal != PublicIpGuard.Verdict.NotAnIp) literal else {
            val host = HostValidator.normalize(input) ?: return Resolved.Failure("\"$input\" is not a valid IP address or hostname.")
            val address = withContext(Dispatchers.IO) { runCatching { InetAddress.getByName(host).hostAddress }.getOrNull() }
                ?: return Resolved.Failure("Could not resolve $host.")
            PublicIpGuard.check(address)
        }
        return when (verdict) {
            is PublicIpGuard.Verdict.Public -> Resolved.Ip(verdict.ip)
            is PublicIpGuard.Verdict.NotRoutable -> Resolved.Failure(PublicIpGuard.refusal(verdict, "Shodan"))
            PublicIpGuard.Verdict.NotAnIp -> Resolved.Failure("Could not resolve $input to an IP address.")
        }
    }

    fun reset() {
        job?.cancel()
        _uiState.value = ShodanUiState.Idle
    }
}

sealed class ShodanUiState {
    data object Idle : ShodanUiState()
    data object Loading : ShodanUiState()
    data class HostSuccess(val report: ShodanHostReport, val recordedFindings: Int = 0) : ShodanUiState()
    data class InternetDbSuccess(val data: InternetDbResponse, val recordedFindings: Int = 0) : ShodanUiState()
    data class SearchSuccess(
        val query: String,
        val total: Int,
        val matches: List<ShodanMatch>,
        val page: Int,
        val isLoadingMore: Boolean = false,
        val loadMoreError: String? = null,
        val exhausted: Boolean = false
    ) : ShodanUiState() {
        val hasMore: Boolean get() = !exhausted && matches.size < total
    }
    data class Error(val message: String) : ShodanUiState()
}
