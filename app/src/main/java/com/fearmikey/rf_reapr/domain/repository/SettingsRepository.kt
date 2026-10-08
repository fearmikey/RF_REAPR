package com.fearmikey.rf_reapr.domain.repository

import com.fearmikey.rf_reapr.domain.model.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface SettingsRepository {
    val themePreference: Flow<ThemePreference>
        get() = flowOf(ThemePreference.SYSTEM)
    val vulnerabilityApiKey: Flow<String>
        get() = flowOf("")
    val hibpApiKey: Flow<String>
        get() = flowOf("")
    val isCameraShortcutEnabled: Flow<Boolean>
        get() = flowOf(true)
    val isPassiveMode: Flow<Boolean>
        get() = flowOf(false)
    val shodanApiKey: Flow<String>
        get() = flowOf("")
    val sdrIp: Flow<String>
        get() = flowOf("127.0.0.1")
    val sdrPort: Flow<Int>
        get() = flowOf(1234)
    val supportDialogNeverAsk: Flow<Boolean>
        get() = flowOf(false)
    val appLaunchCount: Flow<Int>
        get() = flowOf(1)

    suspend fun setThemePreference(preference: ThemePreference) {}
    suspend fun setVulnerabilityApiKey(key: String) {}
    suspend fun setHibpApiKey(key: String) {}
    suspend fun setCameraShortcutEnabled(enabled: Boolean) {}
    suspend fun setPassiveMode(enabled: Boolean) {}
    suspend fun setShodanApiKey(key: String) {}
    suspend fun setSdrIp(ip: String) {}
    suspend fun setSdrPort(port: Int) {}
    suspend fun incrementAppLaunchCount(): Int = 1
    suspend fun setSupportDialogNeverAsk(neverAsk: Boolean) {}
    suspend fun setSupportDialogLastShownLaunch(launchCount: Int) {}
    fun getSupportDialogLastShownLaunch(): Int = 0
}
