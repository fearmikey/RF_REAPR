package com.example.rf_reapr.ui.compliance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rf_reapr.domain.model.Evidence
import com.example.rf_reapr.domain.repository.EvidenceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RecycleBinViewModel(
    private val evidenceRepository: EvidenceRepository
) : ViewModel() {

    private val _deletedEvidence = MutableStateFlow<List<Evidence>>(emptyList())
    val deletedEvidence: StateFlow<List<Evidence>> = _deletedEvidence.asStateFlow()

    init {
        viewModelScope.launch {
            evidenceRepository.getDeletedEvidence().collect {
                _deletedEvidence.value = it
            }
        }
    }

    fun restoreEvidence(evidence: Evidence) {
        viewModelScope.launch {
            evidenceRepository.restoreEvidence(evidence)
        }
    }

    fun purgeEvidence(evidence: Evidence) {
        viewModelScope.launch {
            evidenceRepository.purgeEvidence(evidence)
        }
    }

    fun emptyRecycleBin() {
        viewModelScope.launch {
            evidenceRepository.emptyRecycleBin()
        }
    }
}
