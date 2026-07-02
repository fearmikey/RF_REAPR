package com.example.rf_reapr.domain.repository

import com.example.rf_reapr.domain.model.NfcTagData
import kotlinx.coroutines.flow.Flow

interface NfcScannerRepository {
    val tagData: Flow<NfcTagData?>
    fun processTag(tag: android.nfc.Tag)
    fun clearTagData()
}
