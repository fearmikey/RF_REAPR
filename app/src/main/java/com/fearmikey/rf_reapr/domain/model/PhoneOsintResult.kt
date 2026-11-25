package com.fearmikey.rf_reapr.domain.model

enum class PhoneValidity(val label: String) {
    VALID_LENGTH("Length matches the country's numbering plan"),
    POSSIBLE("Possible (valid E.164 length)"),
    UNUSUAL_LENGTH("Unusual length for this country"),
    INVALID("Not a valid number in this numbering plan")
}

data class PhoneOsintResult(
    val rawInput: String,
    val e164: String,
    val digitsOnly: String,
    val countryCallingCode: String,
    val countryName: String,
    val countryIso: String,
    val nationalNumber: String,
    val formattedInternational: String,
    val estimatedLineType: String,
    val validity: PhoneValidity,
    /** State / territory for NANP numbers, when the area code pins it down. */
    val region: String? = null,
    val extension: String? = null,
    /** Things worth knowing about the number (fictional range, converted vanity letters, …). */
    val notes: List<String> = emptyList(),
    /** The same number written the ways people actually publish it, for exact-match searches. */
    val searchVariants: List<String> = emptyList(),
    val osintLinks: List<PhoneOsintLink> = emptyList()
) {
    val isValidFormat: Boolean get() = validity == PhoneValidity.VALID_LENGTH || validity == PhoneValidity.POSSIBLE
}

data class PhoneOsintLink(
    val title: String,
    val subtitle: String,
    val url: String,
    /** Opens a local app (dialer, SMS) instead of looking the number up. */
    val isDirectAppAction: Boolean = false
)
