package com.fearmikey.rf_reapr.domain.model

import com.fearmikey.rf_reapr.domain.service.DecodedNdefRecord
import com.fearmikey.rf_reapr.domain.service.NfcFinding

data class NfcTagData(
    val id: String,
    val techList: List<String>,
    /** Human-readable "kind: value" lines (kept for older log readers). */
    val ndefMessages: List<String>,
    val isVulnerable: Boolean = false,
    val vulnerabilityDescription: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val chipType: String = "Unknown tag type",
    val records: List<DecodedNdefRecord> = emptyList(),
    val findings: List<NfcFinding> = emptyList(),
    val atqa: String? = null,
    val sak: String? = null,
    val ndefCapacity: Int? = null,
    val ndefUsed: Int? = null,
    val writable: Boolean? = null,
    val randomUid: Boolean = false,
    val readError: String? = null
)
