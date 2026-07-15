package com.fearmikey.rf_reapr.ui.nfc

import androidx.lifecycle.ViewModel
import com.fearmikey.rf_reapr.domain.model.NfcTagData
import com.fearmikey.rf_reapr.domain.repository.NfcScannerRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow

class NfcScannerViewModel(
    private val repository: NfcScannerRepository
) : ViewModel() {

    val tagData: StateFlow<NfcTagData?> = repository.tagData as StateFlow<NfcTagData?>

    fun clearData() {
        repository.clearTagData()
    }
}
