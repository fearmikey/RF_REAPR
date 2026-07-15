package com.fearmikey.rf_reapr.data.repository

import android.nfc.NdefMessage
import android.nfc.Tag
import android.nfc.tech.Ndef
import com.fearmikey.rf_reapr.domain.model.NfcTagData
import com.fearmikey.rf_reapr.domain.repository.NfcScannerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NfcScannerRepositoryImpl : NfcScannerRepository {
    private val _tagData = MutableStateFlow<NfcTagData?>(null)
    override val tagData: StateFlow<NfcTagData?> = _tagData.asStateFlow()

    override fun processTag(tag: Tag) {
        val id = tag.id.joinToString(":") { "%02X".format(it) }
        val techList = tag.techList.toList()
        val ndefMessages = mutableListOf<String>()
        
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            try {
                ndef.connect()
                val message = ndef.cachedNdefMessage
                message?.records?.forEach { record ->
                    ndefMessages.add(String(record.payload))
                }
                ndef.close()
            } catch (e: Exception) {
                ndefMessages.add("Error reading NDEF: ${e.message}")
            }
        }

        val (isVulnerable, description) = analyzeVulnerability(techList, ndefMessages)

        _tagData.value = NfcTagData(
            id = id,
            techList = techList,
            ndefMessages = ndefMessages,
            isVulnerable = isVulnerable,
            vulnerabilityDescription = description
        )
    }

    override fun clearTagData() {
        _tagData.value = null
    }

    private fun analyzeVulnerability(techList: List<String>, messages: List<String>): Pair<Boolean, String?> {
        // Example logic: flag unencrypted or legacy techs, or suspicious NDEF payloads
        if (techList.contains("android.nfc.tech.MifareClassic")) {
            return true to "Legacy Mifare Classic detected (vulnerable to cloning)"
        }

        if (techList.contains("android.nfc.tech.NfcA") && !techList.contains("android.nfc.tech.Ndef")) {
            // Many simple keyfobs use NfcA but aren't NDEF formatted.
            // This isn't necessarily a vulnerability, but we can highlight it for audit.
            return true to "Generic NfcA Tag (potential low-security keyfob)"
        }
        
        messages.forEach { msg ->
            if (msg.contains("http://") || msg.contains("https://")) {
                // Flag URLs as potential phishing/exploit vectors
                return true to "Potential malicious URL/Redirect detected in NDEF"
            }
        }
        
        return false to null
    }
}
