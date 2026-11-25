package com.fearmikey.rf_reapr.domain.model

enum class PayloadFormat(val displayName: String) {
    BASE64("Base64 (Standard)"),
    BASE64_URL("Base64 (URL-Safe)"),
    BASE32("Base32"),
    HEX("Hexadecimal"),
    URL_ENCODE("URL Encoding"),
    URL_ENCODE_ALL("URL Encoding (every character)"),
    HTML_ENTITIES("HTML Entities"),
    UNICODE_ESCAPE("Unicode Escapes (\\uXXXX)"),
    CHAR_CODES("Character Codes (decimal)"),
    BINARY("Binary (8-bit)"),
    ROT13("ROT13")
}

/** A security observation about a JWT. */
data class JwtFinding(val severity: RiskLevel, val title: String, val detail: String)

data class JwtInspectionResult(
    val isValidJwt: Boolean = false,
    val headerJson: String = "",
    val payloadJson: String = "",
    val signatureHex: String = "",
    val algorithm: String? = null,
    val type: String? = null,
    val keyId: String? = null,
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
    val findings: List<JwtFinding> = emptyList(),
    /** True when the token is signed with an HMAC algorithm, so a secret can be tested against it. */
    val isHmac: Boolean = false,
    val errorMessage: String? = null
)
