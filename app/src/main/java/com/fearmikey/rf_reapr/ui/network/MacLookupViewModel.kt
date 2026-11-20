package com.fearmikey.rf_reapr.ui.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.repository.OuiRepository
import com.fearmikey.rf_reapr.domain.service.MacAddressAnalyzer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One resolved lookup. [mac] is null when only an OUI prefix (6-11 hex digits) was given. */
data class MacLookupEntry(
    val input: String,
    val mac: MacAddressAnalyzer.MacInfo?,
    val oui: String,
    val vendor: String?
) {
    val isRandomized: Boolean get() = mac?.isRandomized == true
    val ouiColon: String get() = oui.chunked(2).joinToString(":")
}

sealed interface SingleLookupState {
    data object Empty : SingleLookupState
    data class Invalid(val message: String) : SingleLookupState
    data class Result(val entry: MacLookupEntry) : SingleLookupState
}

data class BulkLookupResult(val entries: List<MacLookupEntry>) {
    val vendorCount: Int get() = entries.mapNotNull { it.vendor }.distinct().size
    val randomizedCount: Int get() = entries.count { it.isRandomized }
    val unknownCount: Int get() = entries.count { it.vendor == null && !it.isRandomized }
}

class MacLookupViewModel(
    private val ouiRepository: OuiRepository
) : ViewModel() {

    /** Number of OUIs in the offline database; null while loading. */
    private val _databaseSize = MutableStateFlow<Int?>(null)
    val databaseSize: StateFlow<Int?> = _databaseSize.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _single = MutableStateFlow<SingleLookupState>(SingleLookupState.Empty)
    val single: StateFlow<SingleLookupState> = _single.asStateFlow()

    private val _history = MutableStateFlow<List<MacLookupEntry>>(emptyList())
    val history: StateFlow<List<MacLookupEntry>> = _history.asStateFlow()

    private val _bulkInput = MutableStateFlow("")
    val bulkInput: StateFlow<String> = _bulkInput.asStateFlow()

    private val _bulkResult = MutableStateFlow<BulkLookupResult?>(null)
    val bulkResult: StateFlow<BulkLookupResult?> = _bulkResult.asStateFlow()

    private var lookupJob: Job? = null

    init {
        viewModelScope.launch { _databaseSize.value = ouiRepository.entryCount() }
    }

    /** Live lookup as the user types. */
    fun onQueryChange(text: String) {
        _query.value = text
        lookupJob?.cancel()
        lookupJob = viewModelScope.launch { _single.value = resolve(text) }
    }

    /** Called on explicit submit (IME action / search button); records the result in history. */
    fun commitQuery() {
        viewModelScope.launch {
            lookupJob?.join() // make sure the latest keystroke has been resolved
            val entry = (_single.value as? SingleLookupState.Result)?.entry ?: return@launch
            _history.update { current ->
                (listOf(entry) + current.filterNot { it.oui == entry.oui && it.mac == entry.mac })
                    .take(MAX_HISTORY)
            }
        }
    }

    fun clearHistory() {
        _history.value = emptyList()
    }

    fun onBulkInputChange(text: String) {
        _bulkInput.value = text
    }

    fun runBulkLookup() {
        val text = _bulkInput.value
        viewModelScope.launch {
            val entries = MacAddressAnalyzer.extractAll(text).map { mac ->
                MacLookupEntry(mac.colonFormat, mac, mac.oui, ouiRepository.lookupVendor(mac.oui))
            }
            _bulkResult.value = BulkLookupResult(entries)
        }
    }

    fun clearBulk() {
        _bulkInput.value = ""
        _bulkResult.value = null
    }

    private suspend fun resolve(text: String): SingleLookupState {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return SingleLookupState.Empty
        val hex = MacAddressAnalyzer.cleanHex(trimmed)
            ?: return SingleLookupState.Invalid("Only hex digits (0-9, A-F) and : - . separators are allowed")
        return when {
            hex.length < 6 -> SingleLookupState.Invalid("Enter at least 6 hex digits (the OUI prefix)")
            hex.length > 12 -> SingleLookupState.Invalid("Too many digits; a MAC address has 12")
            else -> {
                val oui = hex.substring(0, 6)
                SingleLookupState.Result(
                    MacLookupEntry(
                        input = trimmed,
                        mac = MacAddressAnalyzer.parse(hex),
                        oui = oui,
                        vendor = ouiRepository.lookupVendor(oui)
                    )
                )
            }
        }
    }

    companion object {
        private const val MAX_HISTORY = 20

        /** CSV export of bulk results, for copying into notes or a spreadsheet. */
        fun toCsv(entries: List<MacLookupEntry>): String = buildString {
            appendLine("mac,oui,vendor,type")
            entries.forEach { e ->
                val type = when {
                    e.mac?.isMulticast == true -> "multicast"
                    e.isRandomized -> "randomized"
                    else -> "vendor-assigned"
                }
                val vendor = (e.vendor ?: "").replace("\"", "\"\"")
                appendLine("${e.mac?.colonFormat ?: e.input},${e.ouiColon},\"$vendor\",$type")
            }
        }.trimEnd()
    }
}
