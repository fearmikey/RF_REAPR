package com.fearmikey.rf_reapr.ui.web

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.model.UsernameCheckResult
import com.fearmikey.rf_reapr.domain.model.UsernameSiteCategory
import com.fearmikey.rf_reapr.domain.model.UsernameSitesDatabase
import com.fearmikey.rf_reapr.domain.model.UsernameStatus
import com.fearmikey.rf_reapr.domain.repository.LogRepository
import com.fearmikey.rf_reapr.domain.repository.UsernameOsintRepository
import com.fearmikey.rf_reapr.domain.service.ActiveTaskMonitor
import com.fearmikey.rf_reapr.domain.service.UsernameCheckEvaluator
import com.google.gson.Gson
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UsernameOsintUiState(
    val username: String = "",
    /** The cleaned name the current results belong to. */
    val searchedName: String? = null,
    val isScanning: Boolean = false,
    val results: List<UsernameCheckResult> = emptyList(),
    val totalSites: Int = UsernameSitesDatabase.sites.size,
    val selectedCategory: UsernameSiteCategory? = null,
    val filterFoundOnly: Boolean = false,
    val inputError: String? = null
) {
    val foundCount: Int get() = results.count { it.status == UsernameStatus.FOUND }
    val uncertainCount: Int get() = results.count { it.status == UsernameStatus.UNCERTAIN || it.status == UsernameStatus.ERROR }

    /** Hits first, then entries that need a manual look, then misses. */
    val displayedResults: List<UsernameCheckResult>
        get() = UsernameCheckEvaluator.sorted(
            results.filter { result ->
                (selectedCategory == null || result.site.category == selectedCategory) &&
                    (!filterFoundOnly || result.status == UsernameStatus.FOUND)
            }
        )
}

class UsernameOsintViewModel(
    private val repository: UsernameOsintRepository,
    private val logRepository: LogRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(UsernameOsintUiState())
    val uiState: StateFlow<UsernameOsintUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    init {
        viewModelScope.launch {
            ActiveTaskMonitor.stopAllSignal.collect { cancelSearch() }
        }
    }

    fun onUsernameChanged(newUsername: String) {
        _uiState.update { it.copy(username = newUsername, inputError = null) }
    }

    fun toggleFilterFoundOnly() {
        _uiState.update { it.copy(filterFoundOnly = !it.filterFoundOnly) }
    }

    fun selectCategory(category: UsernameSiteCategory?) {
        _uiState.update { it.copy(selectedCategory = if (it.selectedCategory == category) null else category) }
    }

    fun startSearch() {
        val input = _uiState.value.username
        if (input.isBlank()) return
        val name = UsernameCheckEvaluator.normalize(input)
        if (name == null) {
            _uiState.update { it.copy(inputError = "Usernames may only contain letters, digits, dots, hyphens and underscores.") }
            return
        }

        scanJob?.cancel()
        _uiState.update { it.copy(isScanning = true, results = emptyList(), searchedName = name, inputError = null) }

        val taskName = "Username OSINT ($name)"
        ActiveTaskMonitor.addTask(taskName)
        scanJob = viewModelScope.launch {
            try {
                repository.checkUsername(name).collect { result ->
                    _uiState.update { it.copy(results = it.results + result) }
                }
                val finished = _uiState.value
                logRepository?.saveLog(
                    type = "USERNAME",
                    summary = "Username \"$name\": found on ${finished.foundCount} of ${finished.results.size} sites",
                    detailJson = Gson().toJson(
                        mapOf(
                            "username" to name,
                            "found" to finished.results.filter { it.status == UsernameStatus.FOUND }
                                .map { mapOf("site" to it.site.name, "url" to it.profileUrl) },
                            "unchecked" to finished.results.filter { it.status == UsernameStatus.UNCERTAIN || it.status == UsernameStatus.ERROR }
                                .map { it.site.name }
                        )
                    )
                )
            } finally {
                _uiState.update { it.copy(isScanning = false) }
                ActiveTaskMonitor.removeTask(taskName)
            }
        }
    }

    fun cancelSearch() {
        scanJob?.cancel()
        scanJob = null
    }

    /** Plain-text summary of the current results for the clipboard. */
    fun report(): String = _uiState.value.let { UsernameCheckEvaluator.report(it.searchedName.orEmpty(), it.results) }
}
