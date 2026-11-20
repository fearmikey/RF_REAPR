package com.fearmikey.rf_reapr.domain.repository

import android.nfc.Tag
import com.fearmikey.rf_reapr.domain.model.NfcTagData
import kotlinx.coroutines.flow.StateFlow

interface NfcScannerRepository {
    val tagData: StateFlow<NfcTagData?>
    /** Tags scanned this session, newest first. */
    val history: StateFlow<List<NfcTagData>>
    /** True while the NFC screen wants reader mode; MainActivity enables it only then. */
    val readerRequested: StateFlow<Boolean>

    fun processTag(tag: Tag)
    fun clearTagData()
    fun clearHistory()
    fun setReaderRequested(requested: Boolean)
}
