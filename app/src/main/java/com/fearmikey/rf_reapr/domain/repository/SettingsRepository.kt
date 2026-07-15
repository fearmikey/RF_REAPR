package com.fearmikey.rf_reapr.domain.repository

import com.fearmikey.rf_reapr.domain.model.ThemePreference
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val themePreference: Flow<ThemePreference>
    val vulnerabilityApiKey: Flow<String>

    suspend fun setThemePreference(preference: ThemePreference)
    suspend fun setVulnerabilityApiKey(key: String)
}
