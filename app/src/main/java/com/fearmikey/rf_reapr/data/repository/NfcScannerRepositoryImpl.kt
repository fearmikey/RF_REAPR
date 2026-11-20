package com.fearmikey.rf_reapr.data.repository

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA
import com.fearmikey.rf_reapr.domain.model.NfcTagData
import com.fearmikey.rf_reapr.domain.model.RiskLevel
import com.fearmikey.rf_reapr.domain.repository.NfcScannerRepository
import com.fearmikey.rf_reapr.domain.service.DecodedNdefRecord
import com.fearmikey.rf_reapr.domain.service.NfcAnalyzer
import com.fearmikey.rf_reapr.domain.service.NfcTagFacts
import com.fearmikey.rf_reapr.domain.service.RawNdefRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class NfcScannerRepositoryImpl : NfcScannerRepository {
    private val _tagData = MutableStateFlow<NfcTagData?>(null)
    override val tagData: StateFlow<NfcTagData?> = _tagData.asStateFlow()

    private val _history = MutableStateFlow<List<NfcTagData>>(emptyList())
    override val history: StateFlow<List<NfcTagData>> = _history.asStateFlow()

    private val _readerRequested = MutableStateFlow(false)
    override val readerRequested: StateFlow<Boolean> = _readerRequested.asStateFlow()

    override fun setReaderRequested(requested: Boolean) {
        _readerRequested.value = requested
    }

    /** Called on the NFC binder thread, so blocking tag I/O is fine here. */
    override fun processTag(tag: Tag) {
        val techList = tag.techList.toList()
        var readError: String? = null

        val nfcA = NfcA.get(tag)
        val ndef = Ndef.get(tag)
        var message: NdefMessage? = null
        var maxSize: Int? = null
        var writable: Boolean? = null
        var canLock: Boolean? = null
        if (ndef != null) {
            try {
                ndef.connect()
                message = ndef.ndefMessage ?: ndef.cachedNdefMessage
                maxSize = ndef.maxSize
                writable = ndef.isWritable
                canLock = runCatching { ndef.canMakeReadOnly() }.getOrNull()
            } catch (e: Exception) {
                message = ndef.cachedNdefMessage
                readError = "NDEF read interrupted: ${e.message ?: e.javaClass.simpleName} (hold the tag still)"
            } finally {
                runCatching { ndef.close() }
            }
        }

        val facts = NfcTagFacts(
            uid = tag.id ?: ByteArray(0),
            techList = techList,
            atqa = nfcA?.atqa,
            sak = nfcA?.sak?.toInt(),
            ultralightType = MifareUltralight.get(tag)?.type,
            classicSize = MifareClassic.get(tag)?.size,
            ndefMaxSize = maxSize,
            ndefWritable = writable,
            ndefCanMakeReadOnly = canLock,
            historicalBytes = IsoDep.get(tag)?.historicalBytes
        )

        val records = message?.records?.flatMap { decodeWithNested(it) }.orEmpty()
        val chip = NfcAnalyzer.identifyChip(facts)
        val findings = NfcAnalyzer.findings(facts, chip, records)
        val worst = findings.maxByOrNull { it.severity.ordinal }

        val data = NfcTagData(
            id = NfcAnalyzer.hex(facts.uid),
            techList = techList,
            ndefMessages = records.map { "${it.kind}: ${it.value}" },
            isVulnerable = worst != null && worst.severity >= RiskLevel.MEDIUM,
            vulnerabilityDescription = worst?.title,
            chipType = chip,
            records = records,
            findings = findings,
            atqa = facts.atqa?.let { NfcAnalyzer.hex(it, "") },
            sak = facts.sak?.let { "%02X".format(it) },
            ndefCapacity = maxSize,
            ndefUsed = message?.byteArrayLength,
            writable = writable,
            randomUid = NfcAnalyzer.isRandomUid(facts.uid),
            readError = readError
        )
        _tagData.value = data
        _history.update { list -> (listOf(data) + list.filterNot { it.id == data.id }).take(50) }
    }

    private fun decodeWithNested(record: NdefRecord): List<DecodedNdefRecord> {
        val decoded = NfcAnalyzer.decode(RawNdefRecord(record.tnf, record.type, record.payload))
        if (decoded.kind == "Smart poster") {
            val nested = runCatching { NdefMessage(record.payload).records.map { NfcAnalyzer.decode(RawNdefRecord(it.tnf, it.type, it.payload)) } }
                .getOrDefault(emptyList())
            return listOf(decoded.copy(value = "${nested.size} nested records")) + nested
        }
        return listOf(decoded)
    }

    override fun clearTagData() {
        _tagData.value = null
    }

    override fun clearHistory() {
        _history.value = emptyList()
    }
}
