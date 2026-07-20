package com.fearmikey.rf_reapr.ui.web

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.model.Breach
import com.fearmikey.rf_reapr.domain.model.Paste
import com.fearmikey.rf_reapr.domain.repository.HibpRepository
import com.fearmikey.rf_reapr.domain.repository.SettingsRepository
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
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<HibpUiState>(HibpUiState.Idle)
    val uiState: StateFlow<HibpUiState> = _uiState.asStateFlow()

    val isApiKeyMissing: StateFlow<Boolean> = settingsRepository.hibpApiKey
        .map { it.isBlank() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    fun checkAccount(account: String) {
        if (account.isBlank()) return

        viewModelScope.launch {
            val apiKey = settingsRepository.hibpApiKey.first()
            if (apiKey.isBlank()) {
                _uiState.value = HibpUiState.Error("Please set your HaveIBeenPwned API Key in Settings first.")
                return@launch
            }

            _uiState.value = HibpUiState.Loading
            
            val breachResult = repository.getBreachedAccounts(account)
            val pasteResult = repository.getPastes(account)

            if (breachResult.isSuccess && pasteResult.isSuccess) {
                val breaches = breachResult.getOrNull() ?: emptyList()
                val pastes = pasteResult.getOrNull() ?: emptyList()
                
                if (breaches.isEmpty() && pastes.isEmpty()) {
                    _uiState.value = HibpUiState.NoResults
                } else {
                    _uiState.value = HibpUiState.Success(breaches, pastes)
                }
            } else {
                val error = breachResult.exceptionOrNull()?.message 
                    ?: pasteResult.exceptionOrNull()?.message 
                    ?: "Unknown error occurred"
                _uiState.value = HibpUiState.Error(error)
            }
        }
    }

    fun reset() {
        _uiState.value = HibpUiState.Idle
    }
}

sealed class HibpUiState {
    data object Idle : HibpUiState()
    data object Loading : HibpUiState()
    data class Success(val breaches: List<Breach>, val pastes: List<Paste>) : HibpUiState()
    data object NoResults : HibpUiState()
    data class Error(val message: String) : HibpUiState()
}
