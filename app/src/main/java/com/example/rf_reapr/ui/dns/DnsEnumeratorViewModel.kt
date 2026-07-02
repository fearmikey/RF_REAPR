package com.example.rf_reapr.ui.dns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rf_reapr.domain.repository.DnsEnumeratorRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DnsEnumeratorViewModel(
    private val repository: DnsEnumeratorRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<DnsEnumeratorRepository.EnumerationStatus?>(null)
    val uiState: StateFlow<DnsEnumeratorRepository.EnumerationStatus?> = _uiState.asStateFlow()

    fun enumerateDomain(domain: String) {
        viewModelScope.launch {
            repository.enumerateDomain(domain).collect {
                _uiState.value = it
            }
        }
    }

    fun clearState() {
        _uiState.value = null
    }
}
