package com.fearmikey.rf_reapr.domain.repository

import com.fearmikey.rf_reapr.domain.model.ThemePreference
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val themePreference: Flow<ThemePreference>
    val vulnerabilityApiKey: Flow<String>
    val hibpApiKey: Flow<String>
    val isCameraShortcutEnabled: Flow<Boolean>

    suspend fun setThemePreference(preference: ThemePreference)
    suspend fun setVulnerabilityApiKey(key: String)
    suspend fun setHibpApiKey(key: String)
    suspend fun setCameraShortcutEnabled(enabled: Boolean)
}
