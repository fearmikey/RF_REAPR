package com.fearmikey.rf_reapr.ui.web

import androidx.lifecycle.ViewModel
import com.fearmikey.rf_reapr.domain.model.PhoneOsintLink
import com.fearmikey.rf_reapr.domain.model.PhoneOsintResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

data class CountryCodeInfo(
    val code: String,
    val iso: String,
    val name: String
)

data class PhoneOsintUiState(
    val inputPhone: String = "",
    val defaultCountryCode: String = "+1",
    val result: PhoneOsintResult? = null,
    val errorMessage: String? = null
)

class PhoneOsintViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PhoneOsintUiState())
    val uiState: StateFlow<PhoneOsintUiState> = _uiState.asStateFlow()

    fun updateInputPhone(input: String) {
        _uiState.update { it.copy(inputPhone = input) }
        parsePhoneNumber(input, _uiState.value.defaultCountryCode)
    }

    fun setDefaultCountryCode(code: String) {
        _uiState.update { it.copy(defaultCountryCode = code) }
        parsePhoneNumber(_uiState.value.inputPhone, code)
    }

    fun clear() {
        _uiState.update { it.copy(inputPhone = "", result = null, errorMessage = null) }
    }

    fun loadSample(sample: String) {
        updateInputPhone(sample)
    }

    private fun parsePhoneNumber(rawInput: String, defaultCountry: String) {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(result = null, errorMessage = null) }
            return
        }

        // Clean common punctuation
        var cleaned = trimmed
            .replace("(", "")
            .replace(")", "")
            .replace("-", "")
            .replace(".", "")
            .replace(" ", "")

        // Handle international prefix 00
        if (cleaned.startsWith("00")) {
            cleaned = "+" + cleaned.substring(2)
        }

        // If no leading +, prepend default country calling code
        if (!cleaned.startsWith("+")) {
            val prefix = if (defaultCountry.startsWith("+")) defaultCountry else "+$defaultCountry"
            // If the user entered a leading 0 (domestic trunk prefix), strip it
            val national = if (cleaned.startsWith("0") && cleaned.length > 1) cleaned.substring(1) else cleaned
            cleaned = "$prefix$national"
        }

        val digitsOnly = cleaned.removePrefix("+").filter { it.isDigit() }
        if (digitsOnly.length < 5) {
            _uiState.update {
                it.copy(
                    result = null,
                    errorMessage = "Phone number is too short (${digitsOnly.length} digits). Minimum length is 5 digits."
                )
            }
            return
        }

        // Match against ITU-T calling codes
        val matchedCountry = findCountryByCallingCode(digitsOnly)
        val callingCode = matchedCountry?.code ?: "+${digitsOnly.take(3)}"
        val countryName = matchedCountry?.name ?: "Unknown / International"
        val countryIso = matchedCountry?.iso ?: "UN"

        val callingDigits = callingCode.removePrefix("+")
        val nationalNumber = if (digitsOnly.startsWith(callingDigits)) {
            digitsOnly.substring(callingDigits.length)
        } else {
            digitsOnly
        }

        val formattedIntl = formatInternational(callingCode, nationalNumber)
        val lineType = estimateLineType(callingCode, nationalNumber)
        val isValid = digitsOnly.length in 7..15

        val osintLinks = generateOsintLinks(
            digitsOnly = digitsOnly,
            countryIso = countryIso,
            nationalNumber = nationalNumber
        )

        _uiState.update {
            it.copy(
                errorMessage = null,
                result = PhoneOsintResult(
                    rawInput = rawInput,
                    e164 = "+$digitsOnly",
                    digitsOnly = digitsOnly,
                    countryCallingCode = callingCode,
                    countryName = countryName,
                    countryIso = countryIso,
                    nationalNumber = nationalNumber,
                    formattedInternational = formattedIntl,
                    estimatedLineType = lineType,
                    isValidFormat = isValid,
                    osintLinks = osintLinks
                )
            )
        }
    }

    private fun formatInternational(callingCode: String, nationalNumber: String): String {
        return when {
            callingCode == "+1" && nationalNumber.length == 10 -> {
                "$callingCode (${nationalNumber.substring(0, 3)}) ${nationalNumber.substring(3, 6)}-${nationalNumber.substring(6)}"
            }
            nationalNumber.length >= 8 -> {
                val mid = nationalNumber.length / 2
                "$callingCode ${nationalNumber.substring(0, mid)} ${nationalNumber.substring(mid)}"
            }
            else -> "$callingCode $nationalNumber"
        }
    }

    private fun estimateLineType(callingCode: String, national: String): String {
        if (national.isEmpty()) return "Unknown"
        return when (callingCode) {
            "+1" -> {
                when {
                    national.startsWith("800") || national.startsWith("888") ||
                    national.startsWith("877") || national.startsWith("866") ||
                    national.startsWith("855") || national.startsWith("844") ||
                    national.startsWith("833") -> "Toll-Free (USA/Canada)"
                    national.startsWith("900") -> "Premium Rate"
                    else -> "Geographic (Mobile or Landline)"
                }
            }
            "+44" -> {
                when {
                    national.startsWith("7") -> "Mobile"
                    national.startsWith("1") || national.startsWith("2") -> "Fixed Line (Geographic)"
                    national.startsWith("800") || national.startsWith("808") -> "Toll-Free"
                    national.startsWith("84") || national.startsWith("87") || national.startsWith("9") -> "Premium / Special"
                    else -> "Fixed Line / Special"
                }
            }
            "+49" -> {
                when {
                    national.startsWith("15") || national.startsWith("16") || national.startsWith("17") -> "Mobile"
                    national.startsWith("800") -> "Toll-Free"
                    national.startsWith("900") -> "Premium Rate"
                    else -> "Fixed Line (Geographic)"
                }
            }
            "+33" -> {
                when {
                    national.startsWith("6") || national.startsWith("7") -> "Mobile"
                    national.startsWith("8") -> "Toll-Free / Shared Cost"
                    else -> "Fixed Line"
                }
            }
            "+61" -> {
                when {
                    national.startsWith("4") -> "Mobile"
                    national.startsWith("1800") -> "Toll-Free"
                    else -> "Fixed Line"
                }
            }
            "+91" -> {
                when {
                    national.firstOrNull() in listOf('6', '7', '8', '9') -> "Mobile"
                    national.startsWith("1800") -> "Toll-Free"
                    else -> "Fixed Line"
                }
            }
            "+86" -> {
                when {
                    national.startsWith("1") -> "Mobile"
                    national.startsWith("400") || national.startsWith("800") -> "Toll-Free / Business"
                    else -> "Fixed Line"
                }
            }
            else -> {
                if (national.length in 9..11 && national.startsWith("9") || national.startsWith("7") || national.startsWith("6")) {
                    "Likely Mobile"
                } else {
                    "Fixed Line / Mobile"
                }
            }
        }
    }

    private fun generateOsintLinks(
        digitsOnly: String,
        countryIso: String,
        nationalNumber: String
    ): List<PhoneOsintLink> {
        val links = mutableListOf<PhoneOsintLink>()

        // 1. WhatsApp Direct Chat
        links.add(
            PhoneOsintLink(
                title = "WhatsApp Direct Chat",
                subtitle = "Start chat without saving contact to address book",
                url = "https://wa.me/$digitsOnly"
            )
        )

        // 2. Telegram Profile
        links.add(
            PhoneOsintLink(
                title = "Telegram Search",
                subtitle = "Lookup Telegram profile or channel for this number",
                url = "https://t.me/+$digitsOnly"
            )
        )

        // 3. Truecaller Lookup
        val tcIso = countryIso.lowercase(Locale.US)
        links.add(
            PhoneOsintLink(
                title = "Truecaller Directory",
                subtitle = "Reverse caller ID & spam report reputation",
                url = "https://www.truecaller.com/search/$tcIso/$nationalNumber"
            )
        )

        // 4. Sync.me Global Directory
        links.add(
            PhoneOsintLink(
                title = "Sync.me Directory",
                subtitle = "Global public caller search",
                url = "https://sync.me/search/?number=%2B$digitsOnly"
            )
        )

        // 5. Google Exact Match Dork
        links.add(
            PhoneOsintLink(
                title = "Google Exact Match Dork",
                subtitle = "Find web mentions, leaks, and pastebins",
                url = "https://www.google.com/search?q=%22%2B$digitsOnly%22"
            )
        )

        // 6. Direct Dial Action
        links.add(
            PhoneOsintLink(
                title = "System Dialer (Call)",
                subtitle = "Open default telephone app",
                url = "tel:+$digitsOnly",
                isDirectAppAction = true
            )
        )

        // 7. Direct SMS Action
        links.add(
            PhoneOsintLink(
                title = "SMS Message",
                subtitle = "Compose text message in messaging app",
                url = "sms:+$digitsOnly",
                isDirectAppAction = true
            )
        )

        return links
    }

    private fun findCountryByCallingCode(digitsOnly: String): CountryCodeInfo? {
        // Match largest prefix first
        for (candidate in ALL_COUNTRY_CODES) {
            val codeDigits = candidate.code.removePrefix("+")
            if (digitsOnly.startsWith(codeDigits)) {
                return candidate
            }
        }
        return null
    }

    companion object {
        val ALL_COUNTRY_CODES: List<CountryCodeInfo> = listOf(
            // 3-digit calling codes
            CountryCodeInfo("+971", "AE", "United Arab Emirates"),
            CountryCodeInfo("+972", "IL", "Israel"),
            CountryCodeInfo("+973", "BH", "Bahrain"),
            CountryCodeInfo("+974", "QA", "Qatar"),
            CountryCodeInfo("+977", "NP", "Nepal"),
            CountryCodeInfo("+961", "LB", "Lebanon"),
            CountryCodeInfo("+962", "JO", "Jordan"),
            CountryCodeInfo("+963", "SY", "Syria"),
            CountryCodeInfo("+964", "IQ", "Iraq"),
            CountryCodeInfo("+965", "KW", "Kuwait"),
            CountryCodeInfo("+966", "SA", "Saudi Arabia"),
            CountryCodeInfo("+880", "BD", "Bangladesh"),
            CountryCodeInfo("+351", "PT", "Portugal"),
            CountryCodeInfo("+352", "LU", "Luxembourg"),
            CountryCodeInfo("+353", "IE", "Ireland"),
            CountryCodeInfo("+354", "IS", "Iceland"),
            CountryCodeInfo("+358", "FI", "Finland"),
            CountryCodeInfo("+370", "LT", "Lithuania"),
            CountryCodeInfo("+371", "LV", "Latvia"),
            CountryCodeInfo("+372", "EE", "Estonia"),
            CountryCodeInfo("+380", "UA", "Ukraine"),
            CountryCodeInfo("+420", "CZ", "Czech Republic"),
            CountryCodeInfo("+421", "SK", "Slovakia"),
            CountryCodeInfo("+212", "MA", "Morocco"),
            CountryCodeInfo("+213", "DZ", "Algeria"),
            CountryCodeInfo("+216", "TN", "Tunisia"),
            CountryCodeInfo("+218", "LY", "Libya"),
            CountryCodeInfo("+220", "GM", "Gambia"),
            CountryCodeInfo("+221", "SN", "Senegal"),
            CountryCodeInfo("+234", "NG", "Nigeria"),
            CountryCodeInfo("+254", "KE", "Kenya"),
            CountryCodeInfo("+255", "TZ", "Tanzania"),
            CountryCodeInfo("+256", "UG", "Uganda"),
            CountryCodeInfo("+260", "ZM", "Zambia"),
            CountryCodeInfo("+263", "ZW", "Zimbabwe"),

            // 2-digit calling codes
            CountryCodeInfo("+20", "EG", "Egypt"),
            CountryCodeInfo("+27", "ZA", "South Africa"),
            CountryCodeInfo("+30", "GR", "Greece"),
            CountryCodeInfo("+31", "NL", "Netherlands"),
            CountryCodeInfo("+32", "BE", "Belgium"),
            CountryCodeInfo("+33", "FR", "France"),
            CountryCodeInfo("+34", "ES", "Spain"),
            CountryCodeInfo("+36", "HU", "Hungary"),
            CountryCodeInfo("+39", "IT", "Italy"),
            CountryCodeInfo("+40", "RO", "Romania"),
            CountryCodeInfo("+41", "CH", "Switzerland"),
            CountryCodeInfo("+43", "AT", "Austria"),
            CountryCodeInfo("+44", "GB", "United Kingdom"),
            CountryCodeInfo("+45", "DK", "Denmark"),
            CountryCodeInfo("+46", "SE", "Sweden"),
            CountryCodeInfo("+47", "NO", "Norway"),
            CountryCodeInfo("+48", "PL", "Poland"),
            CountryCodeInfo("+49", "DE", "Germany"),
            CountryCodeInfo("+51", "PE", "Peru"),
            CountryCodeInfo("+52", "MX", "Mexico"),
            CountryCodeInfo("+54", "AR", "Argentina"),
            CountryCodeInfo("+55", "BR", "Brazil"),
            CountryCodeInfo("+56", "CL", "Chile"),
            CountryCodeInfo("+57", "CO", "Colombia"),
            CountryCodeInfo("+58", "VE", "Venezuela"),
            CountryCodeInfo("+60", "MY", "Malaysia"),
            CountryCodeInfo("+61", "AU", "Australia"),
            CountryCodeInfo("+62", "ID", "Indonesia"),
            CountryCodeInfo("+63", "PH", "Philippines"),
            CountryCodeInfo("+64", "NZ", "New Zealand"),
            CountryCodeInfo("+65", "SG", "Singapore"),
            CountryCodeInfo("+66", "TH", "Thailand"),
            CountryCodeInfo("+81", "JP", "Japan"),
            CountryCodeInfo("+82", "KR", "South Korea"),
            CountryCodeInfo("+84", "VN", "Vietnam"),
            CountryCodeInfo("+86", "CN", "China"),
            CountryCodeInfo("+90", "TR", "Turkey"),
            CountryCodeInfo("+91", "IN", "India"),
            CountryCodeInfo("+92", "PK", "Pakistan"),
            CountryCodeInfo("+93", "AF", "Afghanistan"),
            CountryCodeInfo("+94", "LK", "Sri Lanka"),
            CountryCodeInfo("+95", "MM", "Myanmar"),
            CountryCodeInfo("+98", "IR", "Iran"),

            // 1-digit calling codes
            CountryCodeInfo("+1", "US", "United States / Canada"),
            CountryCodeInfo("+7", "RU", "Russia / Kazakhstan")
        )
    }
}
