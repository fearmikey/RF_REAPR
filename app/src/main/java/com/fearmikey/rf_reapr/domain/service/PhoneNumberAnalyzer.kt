package com.fearmikey.rf_reapr.domain.service

import com.fearmikey.rf_reapr.domain.model.PhoneOsintLink
import com.fearmikey.rf_reapr.domain.model.PhoneOsintResult
import com.fearmikey.rf_reapr.domain.model.PhoneValidity
import java.net.URLEncoder

/** [callingCode] is digits only (no plus sign). */
data class PhoneCountry(val callingCode: String, val iso: String, val name: String) {
    val dialCode: String get() = "+$callingCode"
}

data class PhoneParse(val result: PhoneOsintResult? = null, val error: String? = null)

/**
 * Offline phone-number parsing: country from the ITU calling code, NANP area-code regions,
 * plausibility checks, a rough line-type estimate and the number written in the formats
 * people publish it in. Nothing here contacts a network; the lookups are links the user opens.
 */
object PhoneNumberAnalyzer {

    // "code|ISO|name". ITU-T E.164 country codes are prefix-free, so the longest match wins.
    private const val COUNTRY_TABLE = """
1|US|United States / Canada (NANP)
7|RU|Russia
20|EG|Egypt
211|SS|South Sudan
212|MA|Morocco
213|DZ|Algeria
216|TN|Tunisia
218|LY|Libya
220|GM|Gambia
221|SN|Senegal
222|MR|Mauritania
223|ML|Mali
224|GN|Guinea
225|CI|Côte d'Ivoire
226|BF|Burkina Faso
227|NE|Niger
228|TG|Togo
229|BJ|Benin
230|MU|Mauritius
231|LR|Liberia
232|SL|Sierra Leone
233|GH|Ghana
234|NG|Nigeria
235|TD|Chad
236|CF|Central African Republic
237|CM|Cameroon
238|CV|Cape Verde
239|ST|São Tomé and Príncipe
240|GQ|Equatorial Guinea
241|GA|Gabon
242|CG|Congo (Brazzaville)
243|CD|DR Congo
244|AO|Angola
245|GW|Guinea-Bissau
246|IO|British Indian Ocean Territory
247|AC|Ascension Island
248|SC|Seychelles
249|SD|Sudan
250|RW|Rwanda
251|ET|Ethiopia
252|SO|Somalia
253|DJ|Djibouti
254|KE|Kenya
255|TZ|Tanzania
256|UG|Uganda
257|BI|Burundi
258|MZ|Mozambique
260|ZM|Zambia
261|MG|Madagascar
262|RE|Réunion / Mayotte
263|ZW|Zimbabwe
264|NA|Namibia
265|MW|Malawi
266|LS|Lesotho
267|BW|Botswana
268|SZ|Eswatini
269|KM|Comoros
27|ZA|South Africa
290|SH|Saint Helena
291|ER|Eritrea
297|AW|Aruba
298|FO|Faroe Islands
299|GL|Greenland
30|GR|Greece
31|NL|Netherlands
32|BE|Belgium
33|FR|France
34|ES|Spain
350|GI|Gibraltar
351|PT|Portugal
352|LU|Luxembourg
353|IE|Ireland
354|IS|Iceland
355|AL|Albania
356|MT|Malta
357|CY|Cyprus
358|FI|Finland
359|BG|Bulgaria
36|HU|Hungary
370|LT|Lithuania
371|LV|Latvia
372|EE|Estonia
373|MD|Moldova
374|AM|Armenia
375|BY|Belarus
376|AD|Andorra
377|MC|Monaco
378|SM|San Marino
380|UA|Ukraine
381|RS|Serbia
382|ME|Montenegro
383|XK|Kosovo
385|HR|Croatia
386|SI|Slovenia
387|BA|Bosnia and Herzegovina
389|MK|North Macedonia
39|IT|Italy
40|RO|Romania
41|CH|Switzerland
420|CZ|Czech Republic
421|SK|Slovakia
423|LI|Liechtenstein
43|AT|Austria
44|GB|United Kingdom
45|DK|Denmark
46|SE|Sweden
47|NO|Norway
48|PL|Poland
49|DE|Germany
500|FK|Falkland Islands
501|BZ|Belize
502|GT|Guatemala
503|SV|El Salvador
504|HN|Honduras
505|NI|Nicaragua
506|CR|Costa Rica
507|PA|Panama
508|PM|Saint Pierre and Miquelon
509|HT|Haiti
51|PE|Peru
52|MX|Mexico
53|CU|Cuba
54|AR|Argentina
55|BR|Brazil
56|CL|Chile
57|CO|Colombia
58|VE|Venezuela
590|GP|Guadeloupe
591|BO|Bolivia
592|GY|Guyana
593|EC|Ecuador
594|GF|French Guiana
595|PY|Paraguay
596|MQ|Martinique
597|SR|Suriname
598|UY|Uruguay
599|CW|Curaçao / Caribbean Netherlands
60|MY|Malaysia
61|AU|Australia
62|ID|Indonesia
63|PH|Philippines
64|NZ|New Zealand
65|SG|Singapore
66|TH|Thailand
670|TL|Timor-Leste
672|NF|Norfolk Island / Antarctica
673|BN|Brunei
674|NR|Nauru
675|PG|Papua New Guinea
676|TO|Tonga
677|SB|Solomon Islands
678|VU|Vanuatu
679|FJ|Fiji
680|PW|Palau
681|WF|Wallis and Futuna
682|CK|Cook Islands
683|NU|Niue
685|WS|Samoa
686|KI|Kiribati
687|NC|New Caledonia
688|TV|Tuvalu
689|PF|French Polynesia
690|TK|Tokelau
691|FM|Micronesia
692|MH|Marshall Islands
800|XT|International freephone (UIFN)
81|JP|Japan
82|KR|South Korea
84|VN|Vietnam
850|KP|North Korea
852|HK|Hong Kong
853|MO|Macau
855|KH|Cambodia
856|LA|Laos
86|CN|China
870|XN|Inmarsat satellite
880|BD|Bangladesh
881|XS|Satellite (Iridium / Globalstar)
882|XV|International networks
883|XV|International networks
886|TW|Taiwan
90|TR|Turkey
91|IN|India
92|PK|Pakistan
93|AF|Afghanistan
94|LK|Sri Lanka
95|MM|Myanmar
960|MV|Maldives
961|LB|Lebanon
962|JO|Jordan
963|SY|Syria
964|IQ|Iraq
965|KW|Kuwait
966|SA|Saudi Arabia
967|YE|Yemen
968|OM|Oman
970|PS|Palestine
971|AE|United Arab Emirates
972|IL|Israel
973|BH|Bahrain
974|QA|Qatar
975|BT|Bhutan
976|MN|Mongolia
977|NP|Nepal
98|IR|Iran
992|TJ|Tajikistan
993|TM|Turkmenistan
994|AZ|Azerbaijan
995|GE|Georgia
996|KG|Kyrgyzstan
998|UZ|Uzbekistan
"""

    private val byCode: Map<String, PhoneCountry> by lazy {
        COUNTRY_TABLE.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.associate { line ->
            val (code, iso, name) = line.split('|', limit = 3)
            code to PhoneCountry(code, iso, name)
        }
    }

    /** Every calling code, sorted by country name, for the default-country picker. */
    val countries: List<PhoneCountry> by lazy { byCode.values.sortedBy { it.name } }

    /** NANP area codes outside the United States: ISO code to name. */
    private val nanpTerritories: Map<String, Pair<String, String>> = mapOf(
        "242" to ("BS" to "Bahamas"), "246" to ("BB" to "Barbados"), "264" to ("AI" to "Anguilla"),
        "268" to ("AG" to "Antigua and Barbuda"), "284" to ("VG" to "British Virgin Islands"),
        "340" to ("VI" to "US Virgin Islands"), "345" to ("KY" to "Cayman Islands"), "441" to ("BM" to "Bermuda"),
        "473" to ("GD" to "Grenada"), "649" to ("TC" to "Turks and Caicos Islands"), "658" to ("JM" to "Jamaica"),
        "664" to ("MS" to "Montserrat"), "670" to ("MP" to "Northern Mariana Islands"), "671" to ("GU" to "Guam"),
        "684" to ("AS" to "American Samoa"), "721" to ("SX" to "Sint Maarten"), "758" to ("LC" to "Saint Lucia"),
        "767" to ("DM" to "Dominica"), "784" to ("VC" to "Saint Vincent and the Grenadines"),
        "787" to ("PR" to "Puerto Rico"), "939" to ("PR" to "Puerto Rico"),
        "809" to ("DO" to "Dominican Republic"), "829" to ("DO" to "Dominican Republic"), "849" to ("DO" to "Dominican Republic"),
        "868" to ("TT" to "Trinidad and Tobago"), "869" to ("KN" to "Saint Kitts and Nevis"), "876" to ("JM" to "Jamaica")
    )

    private val canadianAreaCodes = setOf(
        "204", "226", "236", "249", "250", "263", "289", "306", "343", "354", "365", "367", "368", "382", "387",
        "403", "416", "418", "428", "431", "437", "438", "450", "460", "468", "474", "506", "514", "519", "548",
        "579", "581", "584", "587", "604", "613", "639", "647", "672", "683", "705", "709", "742", "753", "778",
        "780", "782", "807", "819", "825", "867", "873", "879", "902", "905"
    )

    private val nanpTollFree = setOf("800", "833", "844", "855", "866", "877", "888")

    /** National-number lengths (without the country code) for plans where they are fixed enough to check. */
    private val nationalLengths: Map<String, IntRange> = mapOf(
        "1" to 10..10, "7" to 10..10, "31" to 9..9, "33" to 9..9, "34" to 9..9, "41" to 9..9,
        "44" to 7..10, "45" to 8..8, "48" to 9..9, "49" to 6..13, "52" to 10..10, "55" to 10..11,
        "61" to 9..9, "65" to 8..8, "81" to 9..10, "90" to 10..10, "91" to 10..10
    )

    private val keypad = mapOf(
        'A' to '2', 'B' to '2', 'C' to '2', 'D' to '3', 'E' to '3', 'F' to '3', 'G' to '4', 'H' to '4', 'I' to '4',
        'J' to '5', 'K' to '5', 'L' to '5', 'M' to '6', 'N' to '6', 'O' to '6', 'P' to '7', 'Q' to '7', 'R' to '7',
        'S' to '7', 'T' to '8', 'U' to '8', 'V' to '8', 'W' to '9', 'X' to '9', 'Y' to '9', 'Z' to '9'
    )

    private val extensionPattern = Regex("(?i)\\s*(?:ext\\.?|extension|x|#)\\s*(\\d{1,6})\\s*$")

    fun countryForDigits(digits: String): PhoneCountry? {
        for (length in 3 downTo 1) {
            if (digits.length >= length) byCode[digits.take(length)]?.let { return it }
        }
        return null
    }

    /** [defaultCallingCode] ("+44" or "44") is used when the input has no international prefix. */
    fun analyze(rawInput: String, defaultCallingCode: String): PhoneParse {
        var text = rawInput.trim()
        if (text.isEmpty()) return PhoneParse()
        text = text.removePrefix("tel:").removePrefix("sms:")

        val notes = mutableListOf<String>()
        var extension: String? = null
        extensionPattern.find(text)?.let { match ->
            // "x" is also a vanity letter, so only treat it as an extension after real digits.
            if (text.substring(0, match.range.first).count { it.isDigit() } >= 7) {
                extension = match.groupValues[1]
                text = text.substring(0, match.range.first)
            }
        }

        if (text.any { it.isLetter() }) {
            text = text.map { c -> keypad[c.uppercaseChar()] ?: c }.joinToString("")
            notes += "Vanity letters were converted to keypad digits."
        }

        val hasPlus = text.trimStart().startsWith("+")
        var digits = text.filter { it.isDigit() }
        val defaultCode = defaultCallingCode.filter { it.isDigit() }.ifEmpty { "1" }

        val international: String = when {
            hasPlus -> digits
            digits.startsWith("00") -> digits.drop(2)
            defaultCode == "1" && digits.startsWith("011") -> digits.drop(3)
            else -> {
                // National format: strip the trunk prefix the country uses, then add its code.
                digits = when {
                    defaultCode == "1" -> if (digits.length == 11 && digits.startsWith("1")) digits.drop(1) else digits
                    defaultCode == "7" -> if (digits.length == 11 && digits.startsWith("8")) digits.drop(1) else digits
                    defaultCode == "39" -> digits // Italian numbers keep their leading zero
                    digits.startsWith("0") && digits.length > 1 -> digits.drop(1)
                    else -> digits
                }
                defaultCode + digits
            }
        }

        if (international.length < 5) {
            return PhoneParse(error = "Too short to be a phone number (${international.length} digits).")
        }
        if (international.length > 15) {
            return PhoneParse(error = "Too long: E.164 numbers have at most 15 digits, this has ${international.length}.")
        }
        val country = countryForDigits(international)
            ?: return PhoneParse(error = "+${international.take(3)} is not an assigned country calling code.")
        val national = international.drop(country.callingCode.length)
        if (national.length < 4) return PhoneParse(error = "The national number is too short (${national.length} digits).")

        var countryName = country.name
        var countryIso = country.iso
        var region: String? = null
        var validity = nationalLengths[country.callingCode]?.let { range ->
            if (national.length in range) PhoneValidity.VALID_LENGTH else PhoneValidity.UNUSUAL_LENGTH
        } ?: PhoneValidity.POSSIBLE

        when (country.callingCode) {
            "1" -> if (national.length == 10) {
                val area = national.take(3)
                val exchange = national.substring(3, 6)
                when {
                    area in nanpTerritories -> nanpTerritories.getValue(area).let { (iso, name) -> countryIso = iso; countryName = name }
                    area in canadianAreaCodes -> { countryIso = "CA"; countryName = "Canada" }
                    area in nanpTollFree || area == "900" || area.startsWith("5") && area[1] == area[2] -> {
                        countryIso = "US"; countryName = "North America (non-geographic)"
                    }
                    else -> { countryIso = "US"; countryName = "United States" }
                }
                region = "Area code $area"
                if (area[0] !in '2'..'9' || exchange[0] !in '2'..'9' || area.endsWith("11") || exchange.endsWith("11")) {
                    validity = PhoneValidity.INVALID
                    notes += "Area codes and exchanges must start with 2-9 and cannot be N11 service codes."
                }
                if (exchange == "555" && national.substring(6).toInt() in 100..199) {
                    notes += "555-0100 to 555-0199 is reserved for fiction; this is not a real subscriber."
                }
            }
            "7" -> if (national.startsWith("6") || national.startsWith("7")) { countryIso = "KZ"; countryName = "Kazakhstan" }
            "44" -> when {
                national.startsWith("1481") || national.startsWith("7781") || national.startsWith("7839") ->
                    { countryIso = "GG"; countryName = "Guernsey" }
                national.startsWith("1534") || national.startsWith("7797") -> { countryIso = "JE"; countryName = "Jersey" }
                national.startsWith("1624") || national.startsWith("7624") -> { countryIso = "IM"; countryName = "Isle of Man" }
            }
        }

        val e164 = "+$international"
        val variants = searchVariants(country.callingCode, national)
        return PhoneParse(
            result = PhoneOsintResult(
                rawInput = rawInput,
                e164 = e164,
                digitsOnly = international,
                countryCallingCode = country.dialCode,
                countryName = countryName,
                countryIso = countryIso,
                nationalNumber = national,
                formattedInternational = formatInternational(country.callingCode, national),
                estimatedLineType = estimateLineType(country.callingCode, national),
                validity = validity,
                region = region,
                extension = extension,
                notes = notes,
                searchVariants = variants,
                osintLinks = links(international, countryIso, national, variants)
            )
        )
    }

    fun formatInternational(callingCode: String, national: String): String = when {
        callingCode == "1" && national.length == 10 ->
            "+1 (${national.take(3)}) ${national.substring(3, 6)}-${national.substring(6)}"
        callingCode == "44" && national.length == 10 && national.startsWith("7") ->
            "+44 ${national.take(4)} ${national.substring(4)}"
        else -> "+$callingCode ${group(national)}"
    }

    /** Groups of three, with a last group of up to four digits. */
    private fun group(digits: String): String {
        val groups = mutableListOf<String>()
        var rest = digits
        while (rest.length > 4) {
            groups += rest.take(3)
            rest = rest.drop(3)
        }
        if (rest.isNotEmpty()) groups += rest
        return groups.joinToString(" ")
    }

    /** The number as it is typically written, most specific first. Used for exact-match searches. */
    fun searchVariants(callingCode: String, national: String): List<String> {
        val variants = linkedSetOf("+$callingCode$national", formatInternational(callingCode, national))
        if (callingCode == "1" && national.length == 10) {
            val a = national.take(3); val b = national.substring(3, 6); val c = national.substring(6)
            variants += listOf("($a) $b-$c", "$a-$b-$c", "$a.$b.$c", "1-$a-$b-$c", national)
        } else {
            // Most other plans write national numbers with a leading trunk zero (Italy already has it).
            val trunk = if (callingCode == "39" || national.startsWith("0")) national else "0$national"
            variants += listOf("$callingCode$national", trunk, "+$callingCode $national")
        }
        return variants.toList()
    }

    /** A single query matching any of the common spellings, capped so the query stays usable. */
    fun exactMatchQuery(variants: List<String>): String = variants.take(5).joinToString(" OR ") { "\"$it\"" }

    fun estimateLineType(callingCode: String, national: String): String {
        if (national.isEmpty()) return "Unknown"
        fun starts(vararg prefixes: String) = prefixes.any { national.startsWith(it) }
        return when (callingCode) {
            "1" -> when {
                national.take(3) in nanpTollFree -> "Toll-Free"
                starts("900") -> "Premium rate"
                else -> "Geographic (mobile or landline; NANP does not separate them)"
            }
            "44" -> when {
                starts("7") -> "Mobile"
                starts("800", "808") -> "Toll-Free"
                starts("84", "87", "9") -> "Premium / special rate"
                starts("1", "2") -> "Fixed line (geographic)"
                starts("3") -> "Fixed line (non-geographic)"
                else -> "Special service"
            }
            "49" -> when {
                starts("15", "16", "17") -> "Mobile"
                starts("800") -> "Toll-Free"
                starts("900") -> "Premium rate"
                else -> "Fixed line (geographic)"
            }
            "33" -> when {
                starts("6", "7") -> "Mobile"
                starts("8") -> "Toll-Free / shared cost"
                starts("9") -> "VoIP (non-geographic)"
                else -> "Fixed line"
            }
            "34" -> when {
                starts("6", "7") -> "Mobile"
                starts("800", "900") -> "Toll-Free"
                starts("8", "9") -> "Fixed line"
                else -> "Special service"
            }
            "39" -> when {
                starts("3") -> "Mobile"
                starts("0") -> "Fixed line"
                starts("800", "803") -> "Toll-Free"
                else -> "Special service"
            }
            "61" -> when {
                starts("4") -> "Mobile"
                starts("1800") -> "Toll-Free"
                else -> "Fixed line"
            }
            "91" -> when {
                starts("1800") -> "Toll-Free"
                national.first() in '6'..'9' -> "Mobile"
                else -> "Fixed line"
            }
            "86" -> when {
                starts("400", "800") -> "Toll-Free / business"
                starts("1") -> "Mobile"
                else -> "Fixed line"
            }
            "7" -> if (starts("9")) "Mobile" else if (starts("800")) "Toll-Free" else "Fixed line"
            "81" -> if (starts("70", "80", "90")) "Mobile" else if (starts("120", "800")) "Toll-Free" else "Fixed line"
            "55" -> if (national.length == 11 && national[2] == '9') "Mobile" else "Fixed line"
            "800" -> "International toll-free"
            "870", "881", "882", "883" -> "Satellite / international network"
            else -> "Unknown (no numbering-plan data for this country)"
        }
    }

    private fun links(digits: String, iso: String, national: String, variants: List<String>): List<PhoneOsintLink> {
        fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
        val query = enc(exactMatchQuery(variants))
        return listOf(
            PhoneOsintLink("Web mentions (Google)", "Exact-match search across the common ways the number is written", "https://www.google.com/search?q=$query"),
            PhoneOsintLink("Web mentions (DuckDuckGo)", "Same search without a Google account in the loop", "https://duckduckgo.com/?q=$query"),
            PhoneOsintLink("WhatsApp", "Shows whether the number has an account (opens a chat without saving a contact)", "https://wa.me/$digits"),
            PhoneOsintLink("Telegram", "Opens the account linked to the number, if its privacy settings allow it", "https://t.me/+$digits"),
            PhoneOsintLink("Truecaller", "Crowd-sourced caller ID and spam reports", "https://www.truecaller.com/search/${iso.lowercase()}/$national"),
            PhoneOsintLink("Sync.me", "Reverse caller-ID directory", "https://sync.me/search/?number=${enc("+$digits")}"),
            PhoneOsintLink("Call", "Opens the dialer; the call is not placed until you confirm", "tel:+$digits", isDirectAppAction = true),
            PhoneOsintLink("Text message", "Opens the messaging app with the number filled in", "sms:+$digits", isDirectAppAction = true)
        )
    }

    fun report(result: PhoneOsintResult): String = buildString {
        appendLine("Phone number: ${result.e164}")
        appendLine("Formatted: ${result.formattedInternational}")
        appendLine("Country: ${result.countryName} (${result.countryIso}), ${result.countryCallingCode}")
        result.region?.let { appendLine(it) }
        appendLine("National number: ${result.nationalNumber}")
        result.extension?.let { appendLine("Extension: $it") }
        appendLine("Line type (estimate): ${result.estimatedLineType}")
        appendLine("Validity: ${result.validity.label}")
        result.notes.forEach { appendLine("Note: $it") }
        appendLine("Search formats: ${result.searchVariants.joinToString(" | ")}")
    }.trim()
}
