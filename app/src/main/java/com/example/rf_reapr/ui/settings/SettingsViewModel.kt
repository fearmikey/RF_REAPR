package com.example.rf_reapr.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rf_reapr.domain.model.ThemePreference
import com.example.rf_reapr.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: SettingsRepository
) : ViewModel() {

    val themePreference: StateFlow<ThemePreference> = repository.themePreference
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ThemePreference.SYSTEM
        )

    fun setThemePreference(preference: ThemePreference) {
        viewModelScope.launch {
            repository.setThemePreference(preference)
        }
    }
}
