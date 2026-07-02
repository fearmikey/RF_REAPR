package com.example.rf_reapr.ui.rdap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rf_reapr.domain.repository.RdapRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RdapViewModel(
    private val repository: RdapRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<RdapRepository.RdapStatus>(RdapRepository.RdapStatus.Idle)
    val uiState: StateFlow<RdapRepository.RdapStatus> = _uiState.asStateFlow()

    fun queryDomain(domain: String) {
        if (domain.isBlank()) return
        viewModelScope.launch {
            repository.queryDomain(domain).collect {
                _uiState.value = it
            }
        }
    }

    fun reset() {
        _uiState.value = RdapRepository.RdapStatus.Idle
    }
}
