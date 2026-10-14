package com.fearmikey.rf_reapr.domain.model

enum class PayloadFormat(val displayName: String) {
    BASE64("Base64 (Standard)"),
    BASE64_URL("Base64 (URL-Safe)"),
    HEX("Hexadecimal"),
    URL_ENCODE("URL Encoding"),
    HTML_ENTITIES("HTML Entities"),
    BINARY("Binary (8-bit)"),
    ROT13("ROT13")
}

data class JwtInspectionResult(
    val isValidJwt: Boolean = false,
    val headerJson: String = "",
    val payloadJson: String = "",
    val signatureHex: String = "",
    val algorithm: String? = null,
    val type: String? = null,
    val issuer: String? = null,
    val subject: String? = null,
    val audience: String? = null,
    val jwtId: String? = null,
    val issuedAtFormatted: String? = null,
    val expiresAtFormatted: String? = null,
    val notBeforeFormatted: String? = null,
    val isExpired: Boolean? = null,
    val expirationRelative: String? = null,
    val customClaims: Map<String, String> = emptyMap(),
    val errorMessage: String? = null
)
