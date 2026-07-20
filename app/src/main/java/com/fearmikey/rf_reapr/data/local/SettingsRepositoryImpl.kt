package com.fearmikey.rf_reapr.data.local

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import com.fearmikey.rf_reapr.domain.model.ThemePreference
import com.fearmikey.rf_reapr.domain.repository.SettingsRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onStart

class SettingsRepositoryImpl(private val context: Context) : SettingsRepository {
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

    override val vulnerabilityApiKey: Flow<String> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == "vulnerability_api_key") {
                trySend(p.getString(key, "") ?: "")
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.onStart {
        emit(prefs.getString("vulnerability_api_key", "") ?: "")
    }

    override val hibpApiKey: Flow<String> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == "hibp_api_key") {
                trySend(p.getString(key, "") ?: "")
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.onStart {
        emit(prefs.getString("hibp_api_key", "") ?: "")
    }

    override val isCameraShortcutEnabled: Flow<Boolean> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == "camera_shortcut_enabled") {
                trySend(p.getBoolean(key, true))
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.onStart {
        emit(prefs.getBoolean("camera_shortcut_enabled", true))
    }

    override suspend fun setThemePreference(preference: ThemePreference) {
        prefs.edit().putString("theme_preference", preference.name).apply()
    }

    override suspend fun setVulnerabilityApiKey(key: String) {
        prefs.edit().putString("vulnerability_api_key", key).apply()
    }

    override suspend fun setHibpApiKey(key: String) {
        prefs.edit().putString("hibp_api_key", key).apply()
    }

    override suspend fun setCameraShortcutEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("camera_shortcut_enabled", enabled).apply()
        
        val packageManager = context.packageManager
        val componentName = ComponentName(context, "${context.packageName}.CameraShortcutActivity")
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        
        packageManager.setComponentEnabledSetting(
            componentName,
            state,
            PackageManager.DONT_KILL_APP
        )
    }
}
