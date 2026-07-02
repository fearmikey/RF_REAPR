package com.example.rf_reapr.ui.tls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rf_reapr.domain.repository.TlsAuditorRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TlsAuditorViewModel(
    private val repository: TlsAuditorRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<TlsAuditorRepository.AuditStatus?>(null)
    val uiState: StateFlow<TlsAuditorRepository.AuditStatus?> = _uiState.asStateFlow()

    fun auditUrl(url: String) {
        viewModelScope.launch {
            repository.auditUrl(url).collect {
                _uiState.value = it
            }
        }
    }

    fun clearState() {
        _uiState.value = null
    }
}
