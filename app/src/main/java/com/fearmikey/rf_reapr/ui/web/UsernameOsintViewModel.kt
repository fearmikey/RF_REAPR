package com.fearmikey.rf_reapr.ui.web

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.model.UsernameCheckResult
import com.fearmikey.rf_reapr.domain.model.UsernameSiteCategory
import com.fearmikey.rf_reapr.domain.model.UsernameSitesDatabase
import com.fearmikey.rf_reapr.domain.repository.UsernameOsintRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UsernameOsintUiState(
    val username: String = "",
    val isScanning: Boolean = false,
    val results: List<UsernameCheckResult> = emptyList(),
    val totalSites: Int = UsernameSitesDatabase.sites.size,
    val selectedCategory: UsernameSiteCategory? = null,
    val filterFoundOnly: Boolean = false
)

class UsernameOsintViewModel(
    private val repository: UsernameOsintRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UsernameOsintUiState())
    val uiState: StateFlow<UsernameOsintUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    fun onUsernameChanged(newUsername: String) {
        _uiState.value = _uiState.value.copy(username = newUsername)
    }

    fun toggleFilterFoundOnly() {
        _uiState.value = _uiState.value.copy(filterFoundOnly = !_uiState.value.filterFoundOnly)
    }

    fun selectCategory(category: UsernameSiteCategory?) {
        _uiState.value = _uiState.value.copy(
            selectedCategory = if (_uiState.value.selectedCategory == category) null else category
        )
    }

    fun startSearch() {
        val target = _uiState.value.username.trim()
        if (target.isEmpty()) return

        scanJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isScanning = true,
            results = emptyList()
        )

        scanJob = viewModelScope.launch {
            repository.checkUsername(target).collect { result ->
                val currentList = _uiState.value.results.toMutableList()
                currentList.add(result)
                _uiState.value = _uiState.value.copy(results = currentList)
            }
            _uiState.value = _uiState.value.copy(isScanning = false)
        }
    }

    fun cancelSearch() {
        scanJob?.cancel()
        _uiState.value = _uiState.value.copy(isScanning = false)
    }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
    }
}
