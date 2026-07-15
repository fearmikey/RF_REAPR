package com.fearmikey.rf_reapr.ui.compliance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.model.Evidence
import com.fearmikey.rf_reapr.domain.model.EvidenceFolder
import com.fearmikey.rf_reapr.domain.model.EvidenceProject
import com.fearmikey.rf_reapr.domain.repository.EvidenceRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class RecycleBinViewModel(
    private val evidenceRepository: EvidenceRepository
) : ViewModel() {

    private val _deletedProjects = MutableStateFlow<List<EvidenceProject>>(emptyList())
    val deletedProjects: StateFlow<List<EvidenceProject>> = _deletedProjects.asStateFlow()

    private val _deletedFolders = MutableStateFlow<List<EvidenceFolder>>(emptyList())
    val deletedFolders: StateFlow<List<EvidenceFolder>> = _deletedFolders.asStateFlow()

    private val _deletedEvidence = MutableStateFlow<List<Evidence>>(emptyList())
    val deletedEvidence: StateFlow<List<Evidence>> = _deletedEvidence.asStateFlow()

    val isEmpty: StateFlow<Boolean> = combine(_deletedProjects, _deletedFolders, _deletedEvidence) { p, f, e ->
        p.isEmpty() && f.isEmpty() && e.isEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    init {
        viewModelScope.launch {
            evidenceRepository.getDeletedProjects().collect { _deletedProjects.value = it }
        }
        viewModelScope.launch {
            evidenceRepository.getDeletedFolders().collect { _deletedFolders.value = it }
        }
        viewModelScope.launch {
            evidenceRepository.getDeletedEvidence().collect { _deletedEvidence.value = it }
        }
    }

    fun restoreProject(project: EvidenceProject) {
        viewModelScope.launch { evidenceRepository.restoreProject(project) }
    }

    fun restoreFolder(folder: EvidenceFolder) {
        viewModelScope.launch { evidenceRepository.restoreFolder(folder) }
    }

    fun restoreEvidence(evidence: Evidence) {
        viewModelScope.launch { evidenceRepository.restoreEvidence(evidence) }
    }

    fun purgeProject(project: EvidenceProject) {
        viewModelScope.launch { evidenceRepository.purgeProject(project) }
    }

    fun purgeFolder(folder: EvidenceFolder) {
        viewModelScope.launch { evidenceRepository.purgeFolder(folder) }
    }

    fun purgeEvidence(evidence: Evidence) {
        viewModelScope.launch { evidenceRepository.purgeEvidence(evidence) }
    }

    fun emptyRecycleBin() {
        viewModelScope.launch { evidenceRepository.emptyRecycleBin() }
    }
}
