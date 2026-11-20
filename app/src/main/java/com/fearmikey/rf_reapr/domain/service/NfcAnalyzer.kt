package com.fearmikey.rf_reapr.domain.service

import com.fearmikey.rf_reapr.domain.model.RiskLevel

/** Raw NDEF record, mirroring android.nfc.NdefRecord without the Android dependency. */
class RawNdefRecord(val tnf: Short, val type: ByteArray, val payload: ByteArray)

data class DecodedNdefRecord(val kind: String, val value: String)

data class NfcFinding(val severity: RiskLevel, val title: String, val detail: String)

/** Inputs gathered from android.nfc.Tag and its tech classes. */
data class NfcTagFacts(
    val uid: ByteArray,
    val techList: List<String>,
    val atqa: ByteArray? = null,
    val sak: Int? = null,
    val ultralightType: Int? = null, // MifareUltralight.TYPE_ULTRALIGHT = 1, TYPE_ULTRALIGHT_C = 2
    val classicSize: Int? = null,
    val ndefMaxSize: Int? = null,
    val ndefWritable: Boolean? = null,
    val ndefCanMakeReadOnly: Boolean? = null,
    val historicalBytes: ByteArray? = null
) {
    fun has(tech: String) = techList.any { it.endsWith(".$tech") }
}

/** Decodes NDEF records and identifies NFC chip families from their RF parameters. */
object NfcAnalyzer {

    private val uriPrefixes = arrayOf(
        "", "http://www.", "https://www.", "http://", "https://", "tel:", "mailto:", "ftp://anonymous:anonymous@",
        "ftp://ftp.", "ftps://", "sftp://", "smb://", "nfs://", "ftp://", "dav://", "news:", "telnet://", "imap:",
        "rtsp://", "urn:", "pop:", "sip:", "sips:", "tftp:", "btspp://", "btl2cap://", "btgoep://", "tcpobex://",
        "irdaobex://", "file://", "urn:epc:id:", "urn:epc:tag:", "urn:epc:pat:", "urn:epc:raw:", "urn:epc:", "urn:nfc:"
    )

    private const val TNF_WELL_KNOWN: Short = 1
    private const val TNF_MIME: Short = 2
    private const val TNF_ABSOLUTE_URI: Short = 3
    private const val TNF_EXTERNAL: Short = 4

    fun hex(bytes: ByteArray, sep: String = ":"): String = bytes.joinToString(sep) { "%02X".format(it) }

    fun decode(record: RawNdefRecord): DecodedNdefRecord {
        val type = String(record.type, Charsets.US_ASCII)
        val p = record.payload
        return when (record.tnf) {
            TNF_WELL_KNOWN -> when (type) {
                "T" -> DecodedNdefRecord("Text", decodeText(p))
                "U" -> DecodedNdefRecord("URI", decodeUri(p))
                "Sp" -> DecodedNdefRecord("Smart poster", "(nested records)")
                "Hs", "Hr", "Hc" -> DecodedNdefRecord("Connection handover", hex(p, " ").take(120))
                else -> DecodedNdefRecord("Well-known \"$type\"", printableOrHex(p))
            }
            TNF_MIME -> when {
                type.equals("application/vnd.wfa.wsc", true) -> DecodedNdefRecord("Wi-Fi credentials", decodeWifi(p))
                type.startsWith("text/vcard", true) || type.startsWith("text/x-vcard", true) ->
                    DecodedNdefRecord("Contact (vCard)", String(p).lineSequence().firstOrNull { it.startsWith("FN") }?.substringAfter(':') ?: "vCard")
                type.startsWith("application/vnd.bluetooth", true) -> DecodedNdefRecord("Bluetooth pairing", hex(p, " ").take(120))
                type.startsWith("text/", true) -> DecodedNdefRecord("MIME $type", String(p, Charsets.UTF_8))
                else -> DecodedNdefRecord("MIME $type", printableOrHex(p))
            }
            TNF_ABSOLUTE_URI -> DecodedNdefRecord("URI", type)
            TNF_EXTERNAL -> if (type == "android.com:pkg") DecodedNdefRecord("Android app launch", String(p))
                else DecodedNdefRecord("External \"$type\"", printableOrHex(p))
            else -> DecodedNdefRecord("Record (TNF ${record.tnf})", printableOrHex(p))
        }
    }

    fun decodeText(p: ByteArray): String {
        if (p.isEmpty()) return ""
        val status = p[0].toInt()
        val langLen = status and 0x3F
        val charset = if (status and 0x80 != 0) Charsets.UTF_16 else Charsets.UTF_8
        if (1 + langLen > p.size) return printableOrHex(p)
        val lang = String(p, 1, langLen, Charsets.US_ASCII)
        val text = String(p, 1 + langLen, p.size - 1 - langLen, charset)
        return if (lang.isNotBlank()) "$text ($lang)" else text
    }

    fun decodeUri(p: ByteArray): String {
        if (p.isEmpty()) return ""
        val prefix = uriPrefixes.getOrElse(p[0].toInt() and 0xFF) { "" }
        return prefix + String(p, 1, p.size - 1, Charsets.UTF_8)
    }

    /** Wi-Fi Simple Config TLVs: SSID 0x1045, network key 0x1027, auth type 0x1003. */
    fun decodeWifi(p: ByteArray): String {
        var ssid: String? = null
        var key: String? = null
        var auth: Int? = null
        fun walk(data: ByteArray, start: Int, end: Int) {
            var i = start
            while (i + 4 <= end) {
                val t = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
                val len = ((data[i + 2].toInt() and 0xFF) shl 8) or (data[i + 3].toInt() and 0xFF)
                val vs = i + 4
                if (vs + len > end) return
                when (t) {
                    0x100E -> walk(data, vs, vs + len) // credential container
                    0x1045 -> ssid = String(data, vs, len)
                    0x1027 -> key = String(data, vs, len)
                    0x1003 -> if (len >= 2) auth = ((data[vs].toInt() and 0xFF) shl 8) or (data[vs + 1].toInt() and 0xFF)
                }
                i = vs + len
            }
        }
        walk(p, 0, p.size)
        val authName = when (auth) { 0x01 -> "Open"; 0x02 -> "WPA-PSK"; 0x20 -> "WPA2-PSK"; 0x22 -> "WPA/WPA2-PSK"; null -> null; else -> "auth 0x%02X".format(auth) }
        return listOfNotNull(ssid?.let { "SSID: $it" }, authName, key?.let { "Password: $it" }).joinToString(" · ").ifEmpty { "Unparsed Wi-Fi record" }
    }

    private fun printableOrHex(p: ByteArray): String {
        val s = String(p, Charsets.UTF_8)
        return if (s.isNotEmpty() && s.all { it.code in 32..126 || it == '\n' || it == '\t' || it.code > 159 }) s else hex(p, " ").take(200)
    }

    /** Best-effort chip family from SAK/ATQA, tech classes and NDEF capacity. */
    fun identifyChip(f: NfcTagFacts): String {
        val sak = f.sak
        return when {
            f.has("MifareClassic") || sak == 0x08 || sak == 0x18 || sak == 0x09 || sak == 0x01 -> when {
                sak == 0x18 || f.classicSize == 4096 -> "MIFARE Classic 4K"
                sak == 0x09 || f.classicSize == 320 -> "MIFARE Classic Mini"
                else -> "MIFARE Classic 1K"
            }
            sak == 0x10 || sak == 0x11 -> "MIFARE Plus (SL2)"
            f.has("MifareUltralight") || (sak == 0x00 && f.has("NfcA")) -> when {
                f.ultralightType == 2 -> "MIFARE Ultralight C"
                f.ndefMaxSize != null && f.ndefMaxSize in 130..150 -> "NTAG213"
                f.ndefMaxSize != null && f.ndefMaxSize in 480..510 -> "NTAG215"
                f.ndefMaxSize != null && f.ndefMaxSize in 860..890 -> "NTAG216"
                else -> "MIFARE Ultralight / NTAG (Type 2)"
            }
            f.has("IsoDep") && (sak == 0x20 || sak == 0x28 || sak == 0x38) -> when {
                f.historicalBytes?.firstOrNull() == 0x80.toByte() || f.historicalBytes?.isEmpty() == true -> "ISO 14443-4 smartcard (likely MIFARE DESFire)"
                else -> "ISO 14443-4 smartcard (DESFire, payment or ID card)"
            }
            f.has("IsoDep") -> "ISO 14443-4 smartcard"
            f.has("NfcV") -> "ISO 15693 vicinity tag (e.g. NXP ICODE)"
            f.has("NfcF") -> "FeliCa (JIS X 6319-4)"
            f.has("NfcB") -> "ISO 14443-B"
            f.has("NfcBarcode") -> "Kovio NFC barcode"
            else -> "Unknown tag type"
        }
    }

    fun isRandomUid(uid: ByteArray): Boolean = uid.size == 4 && uid[0] == 0x08.toByte()

    fun findings(f: NfcTagFacts, chip: String, records: List<DecodedNdefRecord>): List<NfcFinding> {
        val out = mutableListOf<NfcFinding>()
        if (chip.startsWith("MIFARE Classic")) {
            out += NfcFinding(
                RiskLevel.HIGH, "MIFARE Classic (Crypto-1)",
                "Crypto-1 is broken: sector keys can be recovered with nested/hardnested attacks and the card cloned onto a \"magic\" card. Not suitable for access control or payments."
            )
        }
        if (f.has("NfcA") && f.uid.size in setOf(4, 7) && !isRandomUid(f.uid) && !f.has("IsoDep")) {
            out += NfcFinding(
                RiskLevel.MEDIUM, "Static UID",
                "This tag always reports the same ${f.uid.size}-byte UID. Systems that authenticate by UID alone can be fooled with UID-changeable clones."
            )
        }
        if (f.ndefWritable == true) {
            out += NfcFinding(
                RiskLevel.MEDIUM, "Tag is writable",
                "Anyone with a phone can overwrite this tag's contents (e.g. swap a URL for a phishing link)." +
                    if (f.ndefCanMakeReadOnly == true) " It can be permanently locked read-only." else ""
            )
        }
        if (chip.contains("Ultralight") && !chip.endsWith(" C") || chip.startsWith("NTAG")) {
            out += NfcFinding(RiskLevel.LOW, "No cryptographic authentication", "Ultralight/NTAG tags can't prove they're genuine, so they're trivially cloned.")
        }
        if (f.has("NfcV")) {
            out += NfcFinding(RiskLevel.LOW, "ISO 15693 tag", "Many ICODE/vicinity tags have no or weak password protection and can be read from tens of centimetres.")
        }
        records.forEach { r ->
            when {
                r.kind == "Wi-Fi credentials" && r.value.contains("Password:") ->
                    out += NfcFinding(RiskLevel.MEDIUM, "Wi-Fi password stored in plaintext", "Anyone who taps this tag learns the network password.")
                r.kind == "URI" && r.value.startsWith("http://", true) ->
                    out += NfcFinding(RiskLevel.LOW, "Unencrypted URL", "${r.value} uses plain HTTP and can be intercepted or altered on the network.")
                r.kind == "URI" && Regex("(bit\\.ly|tinyurl|t\\.co|goo\\.gl|is\\.gd|ow\\.ly|rb\\.gy)/", RegexOption.IGNORE_CASE).containsMatchIn(r.value) ->
                    out += NfcFinding(RiskLevel.LOW, "Shortened URL", "The real destination of ${r.value} is hidden. Check it before opening.")
                r.kind == "Android app launch" ->
                    out += NfcFinding(RiskLevel.LOW, "Launches an app", "Tapping opens ${r.value} automatically (or the Play Store if it isn't installed).")
            }
        }
        if (f.has("IsoDep") && (f.sak == 0x20 || f.sak == 0x28)) {
            out += NfcFinding(RiskLevel.LOW, "Smartcard", "May be a payment, transit or ID card. Contents are protected by the card's own cryptography; RF-REAPR doesn't read them.")
        }
        return out.sortedByDescending { it.severity.ordinal }
    }
}
