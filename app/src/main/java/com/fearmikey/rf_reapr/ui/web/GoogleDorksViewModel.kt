package com.fearmikey.rf_reapr.ui.web

import androidx.lifecycle.ViewModel
import com.fearmikey.rf_reapr.domain.model.DorkCategory
import com.fearmikey.rf_reapr.domain.model.GoogleDork
import com.fearmikey.rf_reapr.domain.model.GoogleDorkDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class GoogleDorksUiState(
    val selectedCategory: DorkCategory? = null,
    val searchQuery: String = "",
    val targetDomain: String = ""
)

class GoogleDorksViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(GoogleDorksUiState())
    val uiState: StateFlow<GoogleDorksUiState> = _uiState.asStateFlow()

    fun selectCategory(category: DorkCategory?) {
        _uiState.value = _uiState.value.copy(
            selectedCategory = if (_uiState.value.selectedCategory == category) null else category
        )
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun updateTargetDomain(domain: String) {
        _uiState.value = _uiState.value.copy(targetDomain = domain)
    }

    fun getFilteredDorks(): List<GoogleDork> {
        val state = _uiState.value
        return GoogleDorkDatabase.dorks.filter { dork ->
            val matchesCategory = state.selectedCategory == null || dork.category == state.selectedCategory
            val matchesSearch = state.searchQuery.isEmpty() ||
                    dork.title.contains(state.searchQuery, ignoreCase = true) ||
                    dork.description.contains(state.searchQuery, ignoreCase = true) ||
                    dork.query.contains(state.searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    fun buildDorkQuery(dork: GoogleDork): String {
        val domain = _uiState.value.targetDomain.trim()
        return if (domain.isNotEmpty()) {
            "site:$domain ${dork.query}"
        } else {
            dork.query
        }
    }

    fun buildGoogleSearchUrl(dork: GoogleDork): String {
        val fullQuery = buildDorkQuery(dork)
        val encodedQuery = URLEncoder.encode(fullQuery, StandardCharsets.UTF_8.toString())
        return "https://www.google.com/search?q=$encodedQuery"
    }
}
