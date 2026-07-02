package com.example.rf_reapr.ui.http

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rf_reapr.domain.repository.HttpInspectorRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HttpInspectorViewModel(
    private val repository: HttpInspectorRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<HttpInspectorRepository.InspectorResult?>(null)
    val uiState: StateFlow<HttpInspectorRepository.InspectorResult?> = _uiState.asStateFlow()

    fun inspectUrl(url: String) {
        viewModelScope.launch {
            repository.inspectUrl(url).collect {
                _uiState.value = it
            }
        }
    }

    fun clearState() {
        _uiState.value = null
    }
}
