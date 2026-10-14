package com.fearmikey.rf_reapr.domain.model

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
    val isValidFormat: Boolean,
    val osintLinks: List<PhoneOsintLink>
)

data class PhoneOsintLink(
    val title: String,
    val subtitle: String,
    val url: String,
    val isDirectAppAction: Boolean = false
)
