package com.example.rf_reapr.ui.compliance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rf_reapr.domain.model.ComplianceControl
import com.example.rf_reapr.domain.model.ComplianceFramework
import com.example.rf_reapr.domain.model.ComplianceStatus
import com.example.rf_reapr.domain.repository.ComplianceRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

class ComplianceViewModel(
    private val repository: ComplianceRepository
) : ViewModel() {

    private val _frameworks = MutableStateFlow<List<ComplianceFramework>>(emptyList())
    val frameworks: StateFlow<List<ComplianceFramework>> = _frameworks.asStateFlow()

    private val _controls = MutableStateFlow<List<ComplianceControl>>(emptyList())
    val controls: StateFlow<List<ComplianceControl>> = _controls.asStateFlow()

    private val updateFlow = MutableSharedFlow<ComplianceControl>()
    
    private var loadJob: Job? = null
    private var currentFrameworkId: String? = null

    init {
        _frameworks.value = repository.getFrameworks()
        setupUpdateDebounce()
    }

    @OptIn(FlowPreview::class)
    private fun setupUpdateDebounce() {
        viewModelScope.launch {
            updateFlow
                .debounce(500L) // Wait 500ms after last change before writing to DB
                .collect { control ->
                    repository.updateControlStatus(
                        controlId = control.id,
                        frameworkId = control.frameworkId,
                        status = control.status,
                        notes = control.notes
                    )
                }
        }
    }

    fun loadControls(frameworkId: String) {
        // If already loading this framework, don't restart
        if (currentFrameworkId == frameworkId && _controls.value.isNotEmpty()) return

        currentFrameworkId = frameworkId
        _controls.value = emptyList() // Clear stale data immediately
        
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            repository.getControlsForFramework(frameworkId).collectLatest { fetchedControls ->
                // Safety check: ensure we still care about this framework
                if (currentFrameworkId == frameworkId) {
                    _controls.value = fetchedControls
                }
            }
        }
    }

    fun updateControlStatus(controlId: String, frameworkId: String, status: ComplianceStatus, notes: String) {
        // 1. Immediately update local state for UI responsiveness
        val currentList = _controls.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == controlId }
        if (index != -1) {
            val updatedControl = currentList[index].copy(status = status, notes = notes)
            currentList[index] = updatedControl
            _controls.value = currentList
            
            // 2. Queue for debounced DB write
            viewModelScope.launch {
                updateFlow.emit(updatedControl)
            }
        }
    }
}
