package com.fearmikey.rf_reapr.ui.web

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.model.Breach
import com.fearmikey.rf_reapr.domain.model.Paste
import com.fearmikey.rf_reapr.domain.repository.FindingRepository
import com.fearmikey.rf_reapr.domain.repository.HibpRepository
import com.fearmikey.rf_reapr.domain.repository.LogRepository
import com.fearmikey.rf_reapr.domain.repository.SettingsRepository
import com.fearmikey.rf_reapr.domain.service.BreachAnalyzer
import com.fearmikey.rf_reapr.domain.service.BreachSummary
import com.google.gson.Gson
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HibpViewModel(
    private val repository: HibpRepository,
    private val settingsRepository: SettingsRepository,
    private val logRepository: LogRepository,
    private val findingRepository: FindingRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow<HibpUiState>(HibpUiState.Idle)
    val uiState: StateFlow<HibpUiState> = _uiState.asStateFlow()

    private val _passwordState = MutableStateFlow<PasswordCheckState>(PasswordCheckState.Idle)
    val passwordState: StateFlow<PasswordCheckState> = _passwordState.asStateFlow()

    val isApiKeyMissing: StateFlow<Boolean> = settingsRepository.hibpApiKey
        .map { it.isBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private var accountJob: Job? = null
    private var passwordJob: Job? = null

    fun checkAccount(input: String) {
        val account = input.trim()
        if (account.isEmpty() || _uiState.value is HibpUiState.Loading) return

        accountJob?.cancel()
        accountJob = viewModelScope.launch {
            if (settingsRepository.hibpApiKey.first().isBlank()) {
                _uiState.value = HibpUiState.Error("Set your Have I Been Pwned API key under Settings → API Keys first.")
                return@launch
            }
            _uiState.value = HibpUiState.Loading

            val breachResult = repository.getBreachedAccounts(account)
            val breaches = breachResult.getOrElse {
                _uiState.value = HibpUiState.Error(it.message ?: "Breach lookup failed.")
                return@launch
            }
            // A failed paste lookup (rate limit on the second request) must not discard the breaches.
            val pasteResult = repository.getPastes(account)
            val pastes = pasteResult.getOrDefault(emptyList())
            val pasteNote = when {
                !BreachAnalyzer.isEmail(account) -> "Pastes are only indexed by email address, so none were searched."
                pasteResult.isFailure -> "Paste lookup failed: ${pasteResult.exceptionOrNull()?.message}"
                else -> null
            }

            val sorted = BreachAnalyzer.sorted(breaches)
            val drafts = BreachAnalyzer.toDrafts(account, sorted, pastes)
            if (drafts.isNotEmpty()) findingRepository?.record(drafts)

            _uiState.value = if (sorted.isEmpty() && pastes.isEmpty()) {
                HibpUiState.NoResults(account, pasteNote)
            } else {
                HibpUiState.Success(account, sorted, pastes, BreachAnalyzer.summarize(sorted, pastes), pasteNote, drafts.size)
            }

            // Stored keys (account, breaches[].name/domain/breachDate/dataClasses) are read by the report builder.
            logRepository.saveLog(
                type = "HIBP",
                summary = "HIBP Check: $account (${sorted.size} breaches)",
                detailJson = Gson().toJson(mapOf("account" to account, "breaches" to sorted, "pastes" to pastes))
            )
        }
    }

    /**
     * Checks a password against Pwned Passwords. The password is hashed here and never stored,
     * logged or sent; only the count comes back into state.
     */
    fun checkPassword(password: String) {
        if (password.isEmpty() || _passwordState.value is PasswordCheckState.Loading) return
        _passwordState.value = PasswordCheckState.Loading
        passwordJob?.cancel()
        passwordJob = viewModelScope.launch {
            _passwordState.value = repository.pwnedPasswordCount(password).fold(
                onSuccess = { PasswordCheckState.Result(it) },
                onFailure = { PasswordCheckState.Error(it.message ?: "Password check failed.") }
            )
        }
    }

    fun clearPasswordResult() {
        passwordJob?.cancel()
        _passwordState.value = PasswordCheckState.Idle
    }

    fun reset() {
        accountJob?.cancel()
        _uiState.value = HibpUiState.Idle
    }
}

sealed class HibpUiState {
    data object Idle : HibpUiState()
    data object Loading : HibpUiState()
    data class Success(
        val account: String,
        val breaches: List<Breach>,
        val pastes: List<Paste>,
        val summary: BreachSummary,
        val pasteNote: String? = null,
        val recordedFindings: Int = 0
    ) : HibpUiState()
    data class NoResults(val account: String, val pasteNote: String? = null) : HibpUiState()
    data class Error(val message: String) : HibpUiState()
}

sealed class PasswordCheckState {
    data object Idle : PasswordCheckState()
    data object Loading : PasswordCheckState()
    data class Result(val count: Int) : PasswordCheckState()
    data class Error(val message: String) : PasswordCheckState()
}
