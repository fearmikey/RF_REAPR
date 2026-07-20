package com.fearmikey.rf_reapr.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.model.ThemePreference
import com.fearmikey.rf_reapr.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class SettingsViewModel(
    private val repository: SettingsRepository
) : ViewModel() {

    private val _exportStatus = MutableSharedFlow<String>()
    val exportStatus: SharedFlow<String> = _exportStatus

    val themePreference: StateFlow<ThemePreference> = repository.themePreference
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ThemePreference.SYSTEM
        )

    val vulnerabilityApiKey: StateFlow<String> = repository.vulnerabilityApiKey
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )

    val hibpApiKey: StateFlow<String> = repository.hibpApiKey
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ""
        )

    val isCameraShortcutEnabled: StateFlow<Boolean> = repository.isCameraShortcutEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    fun setThemePreference(preference: ThemePreference) {
        viewModelScope.launch {
            repository.setThemePreference(preference)
        }
    }

    fun setVulnerabilityApiKey(key: String) {
        viewModelScope.launch {
            repository.setVulnerabilityApiKey(key)
        }
    }

    fun setHibpApiKey(key: String) {
        viewModelScope.launch {
            repository.setHibpApiKey(key)
        }
    }

    fun setCameraShortcutEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setCameraShortcutEnabled(enabled)
        }
    }
}
