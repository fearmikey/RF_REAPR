package com.example.rf_reapr.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.rf_reapr.domain.model.ThemePreference
import com.example.rf_reapr.domain.repository.SettingsRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onStart

class SettingsRepositoryImpl(context: Context) : SettingsRepository {
    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    override val themePreference: Flow<ThemePreference> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == "theme_preference") {
                val value = p.getString(key, ThemePreference.SYSTEM.name)
                trySend(ThemePreference.valueOf(value ?: ThemePreference.SYSTEM.name))
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.onStart {
        val value = prefs.getString("theme_preference", ThemePreference.SYSTEM.name)
        emit(ThemePreference.valueOf(value ?: ThemePreference.SYSTEM.name))
    }

    override suspend fun setThemePreference(preference: ThemePreference) {
        prefs.edit().putString("theme_preference", preference.name).apply()
    }
}
