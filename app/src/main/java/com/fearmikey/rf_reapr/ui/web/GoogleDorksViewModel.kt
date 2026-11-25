package com.fearmikey.rf_reapr.ui.web

import androidx.lifecycle.ViewModel
import com.fearmikey.rf_reapr.domain.model.DorkCategory
import com.fearmikey.rf_reapr.domain.model.GoogleDork
import com.fearmikey.rf_reapr.domain.model.GoogleDorkDatabase
import com.fearmikey.rf_reapr.domain.service.DorkQueryBuilder
import com.fearmikey.rf_reapr.domain.service.SearchEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class GoogleDorksUiState(
    val selectedCategory: DorkCategory? = null,
    val searchQuery: String = "",
    val targetDomain: String = "",
    val engine: SearchEngine = SearchEngine.GOOGLE,
    val customQuery: String = ""
) {
    /** The cleaned target host; "" when none was entered, null when the input isn't a hostname. */
    val target: String? get() = DorkQueryBuilder.normalizeTarget(targetDomain)

    val filteredDorks: List<GoogleDork>
        get() = DorkQueryBuilder.filter(GoogleDorkDatabase.dorks, selectedCategory, searchQuery)
}

/** Builds queries only; a search runs in the user's browser when they tap a dork. */
class GoogleDorksViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(GoogleDorksUiState())
    val uiState: StateFlow<GoogleDorksUiState> = _uiState.asStateFlow()

    fun selectCategory(category: DorkCategory?) {
        _uiState.update { it.copy(selectedCategory = if (it.selectedCategory == category) null else category) }
    }

    fun updateSearchQuery(query: String) = _uiState.update { it.copy(searchQuery = query) }

    fun updateTargetDomain(domain: String) = _uiState.update { it.copy(targetDomain = domain) }

    fun selectEngine(engine: SearchEngine) = _uiState.update { it.copy(engine = engine) }

    fun updateCustomQuery(query: String) = _uiState.update { it.copy(customQuery = query) }

    /** Appends an operator chip to the custom query, separated by a space from what is already there. */
    fun appendOperator(operator: String) = _uiState.update { state ->
        val current = state.customQuery
        val separator = if (current.isEmpty() || current.endsWith(" ")) "" else " "
        state.copy(customQuery = current + separator + operator)
    }
}
