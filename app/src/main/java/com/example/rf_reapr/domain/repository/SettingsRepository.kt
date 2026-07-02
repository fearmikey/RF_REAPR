package com.example.rf_reapr.domain.repository

import com.example.rf_reapr.domain.model.ThemePreference
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val themePreference: Flow<ThemePreference>
    suspend fun setThemePreference(preference: ThemePreference)
}
