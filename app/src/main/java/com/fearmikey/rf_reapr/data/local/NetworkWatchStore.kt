package com.fearmikey.rf_reapr.data.local

import android.content.Context
import androidx.core.content.edit

/** Persists the background-watch toggle, per-network baselines and last-run summary. */
class NetworkWatchStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_ENABLED, value) }

    var lastRunSummary: String?
        get() = prefs.getString(KEY_LAST_RUN, null)
        set(value) = prefs.edit { putString(KEY_LAST_RUN, value) }

    fun baseline(networkKey: String): Set<String>? = prefs.getStringSet(baselineKey(networkKey), null)?.toSet()

    fun saveBaseline(networkKey: String, keys: Set<String>) = prefs.edit { putStringSet(baselineKey(networkKey), keys) }

    fun clearBaselines() = prefs.edit {
        prefs.all.keys.filter { it.startsWith(BASELINE_PREFIX) }.forEach(::remove)
    }

    private fun baselineKey(networkKey: String) = BASELINE_PREFIX + networkKey

    companion object {
        const val PREFS_NAME = "network_watch"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_LAST_RUN = "last_run"
        private const val BASELINE_PREFIX = "baseline:"
    }
}
