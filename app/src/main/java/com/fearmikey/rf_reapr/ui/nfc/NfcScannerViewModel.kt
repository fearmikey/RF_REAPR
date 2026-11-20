package com.fearmikey.rf_reapr.ui.nfc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.model.NfcTagData
import com.fearmikey.rf_reapr.domain.repository.FindingRepository
import com.fearmikey.rf_reapr.domain.repository.LogRepository
import com.fearmikey.rf_reapr.domain.repository.NfcScannerRepository
import com.fearmikey.rf_reapr.domain.service.FindingAdapters
import com.google.gson.Gson
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class NfcScannerViewModel(
    private val repository: NfcScannerRepository,
    private val logRepository: LogRepository? = null,
    private val findingRepository: FindingRepository? = null
) : ViewModel() {

    val tagData: StateFlow<NfcTagData?> = repository.tagData
    val history: StateFlow<List<NfcTagData>> = repository.history
    private val gson = Gson()

    init {
        // Log each new scan so it appears in reports.
        viewModelScope.launch {
            repository.tagData.drop(1).filterNotNull().collect { tag ->
                logRepository?.saveLog(
                    type = "NFC",
                    summary = "NFC: ${tag.chipType} ${tag.id}" + (tag.vulnerabilityDescription?.let { " · $it" } ?: ""),
                    detailJson = gson.toJson(tag)
                )
                findingRepository?.record(FindingAdapters.nfc(tag.id, tag.chipType, tag.findings))
            }
        }
    }

    fun setReaderActive(active: Boolean) = repository.setReaderRequested(active)

    fun clearData() = repository.clearTagData()

    fun clearHistory() = repository.clearHistory()

    private val _selected = kotlinx.coroutines.flow.MutableStateFlow<NfcTagData?>(null)

    /** Re-displays a tag from history without rescanning. */
    fun show(tag: NfcTagData) { _selected.value = tag }

    val selected: StateFlow<NfcTagData?> = _selected

    fun clearSelection() { _selected.value = null }

    fun reportText(tag: NfcTagData): String = buildString {
        appendLine("RF-REAPR NFC tag report")
        appendLine("UID: ${tag.id}${if (tag.randomUid) " (random)" else ""}")
        appendLine("Chip: ${tag.chipType}")
        tag.atqa?.let { appendLine("ATQA: $it  SAK: ${tag.sak}") }
        appendLine("Technologies: ${tag.techList.joinToString { it.substringAfterLast('.') }}")
        tag.ndefCapacity?.let { appendLine("NDEF: ${tag.ndefUsed ?: 0}/$it bytes, writable=${tag.writable}") }
        tag.records.forEach { appendLine("${it.kind}: ${it.value}") }
        if (tag.findings.isNotEmpty()) {
            appendLine("Findings:")
            tag.findings.forEach { appendLine("[${it.severity}] ${it.title}: ${it.detail}") }
        }
    }

    override fun onCleared() {
        repository.setReaderRequested(false)
        super.onCleared()
    }
}
