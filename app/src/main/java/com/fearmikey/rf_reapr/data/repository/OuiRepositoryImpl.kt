package com.fearmikey.rf_reapr.data.repository

import android.content.Context
import android.util.Log
import com.fearmikey.rf_reapr.domain.repository.OuiRepository
import com.fearmikey.rf_reapr.domain.service.DeviceIdentificationService
import com.fearmikey.rf_reapr.domain.service.MacAddressAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Merges the built-in OUI table from [DeviceIdentificationService] with any entries in the
 * optional `assets/oui.json` file (`{"AA:BB:CC": "Vendor"}`), which take precedence so a
 * fuller IEEE export can be dropped in without code changes. Loaded once, lazily, off the
 * main thread.
 */
class OuiRepositoryImpl(context: Context) : OuiRepository {

    private val appContext = context.applicationContext
    private val mutex = Mutex()
    @Volatile private var table: Map<String, String>? = null

    private suspend fun table(): Map<String, String> {
        table?.let { return it }
        return mutex.withLock {
            table ?: withContext(Dispatchers.IO) { load() }.also { table = it }
        }
    }

    private fun load(): Map<String, String> {
        val merged = HashMap(DeviceIdentificationService.builtInOuiTable)
        try {
            val json = appContext.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
            val obj = JSONObject(json)
            obj.keys().forEach { key ->
                val oui = MacAddressAnalyzer.ouiOf(key) ?: return@forEach
                merged[oui] = obj.getString(key)
            }
        } catch (e: Exception) {
            // The asset is optional; the built-in table still works without it.
            Log.w(TAG, "Could not load $ASSET_NAME: ${e.message}")
        }
        return merged
    }

    override suspend fun lookupVendor(oui: String): String? = table()[oui.uppercase()]

    override suspend fun entryCount(): Int = table().size

    private companion object {
        const val TAG = "OuiRepository"
        const val ASSET_NAME = "oui.json"
    }
}
