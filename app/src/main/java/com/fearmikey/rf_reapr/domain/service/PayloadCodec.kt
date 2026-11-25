package com.fearmikey.rf_reapr.domain.service

import com.fearmikey.rf_reapr.domain.model.JwtFinding
import com.fearmikey.rf_reapr.domain.model.JwtInspectionResult
import com.fearmikey.rf_reapr.domain.model.PayloadFormat
import com.fearmikey.rf_reapr.domain.model.RiskLevel
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

/** Text encoders / decoders used when crafting and reading web payloads. Pure JVM, no Android APIs. */
object PayloadCodec {

    private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun encode(input: String, format: PayloadFormat): String {
        val bytes = input.toByteArray(Charsets.UTF_8)
        return when (format) {
            PayloadFormat.BASE64 -> Base64.getEncoder().encodeToString(bytes)
            PayloadFormat.BASE64_URL -> Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            PayloadFormat.BASE32 -> base32Encode(bytes)
            PayloadFormat.HEX -> bytes.joinToString("") { "%02x".format(it) }
            PayloadFormat.URL_ENCODE -> percentEncode(bytes, all = false)
            PayloadFormat.URL_ENCODE_ALL -> percentEncode(bytes, all = true)
            PayloadFormat.HTML_ENTITIES -> htmlEncode(input)
            PayloadFormat.UNICODE_ESCAPE -> input.map { "\\u%04x".format(it.code) }.joinToString("")
            PayloadFormat.CHAR_CODES -> input.codePoints().toArray().joinToString(",")
            PayloadFormat.BINARY -> bytes.joinToString(" ") { (it.toInt() and 0xFF).toString(2).padStart(8, '0') }
            PayloadFormat.ROT13 -> rot13(input)
        }
    }

    /** @throws IllegalArgumentException with a user-readable message when [input] isn't valid for [format]. */
    fun decode(input: String, format: PayloadFormat): String = when (format) {
        PayloadFormat.BASE64, PayloadFormat.BASE64_URL -> bytesToText(base64Decode(input))
        PayloadFormat.BASE32 -> bytesToText(base32Decode(input))
        PayloadFormat.HEX -> bytesToText(hexDecode(input))
        PayloadFormat.URL_ENCODE, PayloadFormat.URL_ENCODE_ALL -> try {
            URLDecoder.decode(input, "UTF-8")
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("Malformed percent-encoding: every % must be followed by two hex digits")
        }
        PayloadFormat.HTML_ENTITIES -> htmlDecode(input)
        PayloadFormat.UNICODE_ESCAPE -> Regex("\\\\u([0-9a-fA-F]{4})|\\\\x([0-9a-fA-F]{2})").replace(input) { m ->
            (m.groupValues[1].ifEmpty { m.groupValues[2] }).toInt(16).toChar().toString()
        }
        PayloadFormat.CHAR_CODES -> if (input.isBlank()) "" else {
            val codes = input.split(Regex("[^0-9]+")).filter { it.isNotEmpty() }
            if (codes.isEmpty()) throw IllegalArgumentException("Expected decimal character codes separated by commas or spaces")
            buildString {
                codes.forEach { code ->
                    val value = code.toIntOrNull()?.takeIf { Character.isValidCodePoint(it) }
                        ?: throw IllegalArgumentException("$code is not a valid character code")
                    appendCodePoint(value)
                }
            }
        }
        PayloadFormat.BINARY -> {
            val clean = input.trim()
            val chunks = if (clean.any { it.isWhitespace() }) clean.split(Regex("\\s+")) else clean.chunked(8)
            bytesToText(
                chunks.map { chunk ->
                    if (chunk.isEmpty() || chunk.length > 8 || chunk.any { it != '0' && it != '1' }) {
                        throw IllegalArgumentException("Binary input must be groups of up to 8 digits of 0 and 1")
                    }
                    chunk.toInt(2).toByte()
                }.toByteArray()
            )
        }
        PayloadFormat.ROT13 -> rot13(input)
    }

    /** Standard and URL-safe alphabets, with or without padding, ignoring whitespace. */
    fun base64Decode(input: String): ByteArray {
        var s = input.filterNot { it.isWhitespace() }.replace('-', '+').replace('_', '/').trimEnd('=')
        if (s.length % 4 == 1) throw IllegalArgumentException("Invalid Base64 length")
        while (s.length % 4 != 0) s += "="
        return try {
            Base64.getDecoder().decode(s)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Not valid Base64: ${e.message}")
        }
    }

    private fun hexDecode(input: String): ByteArray {
        val clean = input.replace(Regex("0x|\\\\x|[\\s:,-]"), "")
        if (clean.isEmpty()) return ByteArray(0)
        if (clean.length % 2 != 0) throw IllegalArgumentException("Hex input needs an even number of digits")
        if (!clean.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) throw IllegalArgumentException("Hex input may only contain 0-9 and a-f")
        return ByteArray(clean.length / 2) { i -> clean.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }

    private fun base32Encode(bytes: ByteArray): String {
        val out = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                out.append(BASE32_ALPHABET[(buffer shr (bits - 5)) and 0x1F])
                bits -= 5
            }
        }
        if (bits > 0) out.append(BASE32_ALPHABET[(buffer shl (5 - bits)) and 0x1F])
        while (out.length % 8 != 0) out.append('=')
        return out.toString()
    }

    private fun base32Decode(input: String): ByteArray {
        val clean = input.filterNot { it.isWhitespace() }.trimEnd('=').uppercase()
        val out = ArrayList<Byte>(clean.length * 5 / 8)
        var buffer = 0
        var bits = 0
        for (c in clean) {
            val value = BASE32_ALPHABET.indexOf(c)
            if (value < 0) throw IllegalArgumentException("'$c' is not a Base32 character (A-Z, 2-7)")
            buffer = (buffer shl 5) or value
            bits += 5
            if (bits >= 8) {
                out.add(((buffer shr (bits - 8)) and 0xFF).toByte())
                bits -= 8
            }
        }
        return out.toByteArray()
    }

    /** RFC 3986 percent-encoding of UTF-8 bytes; [all] also encodes letters and digits. */
    private fun percentEncode(bytes: ByteArray, all: Boolean): String = buildString {
        for (b in bytes) {
            val c = (b.toInt() and 0xFF).toChar()
            val unreserved = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '-' || c == '.' || c == '_' || c == '~'
            if (unreserved && !all) append(c) else append("%%%02X".format(b.toInt() and 0xFF))
        }
    }

    private fun htmlEncode(input: String): String = buildString {
        var i = 0
        while (i < input.length) {
            val codePoint = input.codePointAt(i)
            when (codePoint) {
                '&'.code -> append("&amp;")
                '<'.code -> append("&lt;")
                '>'.code -> append("&gt;")
                '"'.code -> append("&quot;")
                '\''.code -> append("&#39;")
                else -> if (codePoint > 127) append("&#x").append(Integer.toHexString(codePoint)).append(';') else appendCodePoint(codePoint)
            }
            i += Character.charCount(codePoint)
        }
    }

    private val namedEntities = mapOf("lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ", "amp" to "&")

    private fun htmlDecode(input: String): String =
        Regex("&(#x[0-9a-fA-F]{1,6}|#[0-9]{1,7}|[a-zA-Z]{2,6});").replace(input) { match ->
            val body = match.groupValues[1]
            val codePoint = when {
                body.startsWith("#x") || body.startsWith("#X") -> body.drop(2).toIntOrNull(16)
                body.startsWith("#") -> body.drop(1).toIntOrNull()
                else -> return@replace namedEntities[body.lowercase()] ?: match.value
            }
            if (codePoint != null && Character.isValidCodePoint(codePoint)) String(Character.toChars(codePoint)) else match.value
        }

    private fun rot13(input: String): String = buildString(input.length) {
        for (c in input) {
            append(
                when (c) {
                    in 'a'..'z' -> 'a' + (c - 'a' + 13) % 26
                    in 'A'..'Z' -> 'A' + (c - 'A' + 13) % 26
                    else -> c
                }
            )
        }
    }

    /** Decoded bytes as text, or a hex dump when they aren't valid UTF-8 (binary payloads). */
    fun bytesToText(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: CharacterCodingException) {
        "[binary data, ${bytes.size} bytes]\n" + bytes.joinToString(" ") { "%02x".format(it) }
    }

    /** Formats [input] plausibly is encoded in, most specific first. Used to suggest a decoder. */
    fun detect(input: String): List<PayloadFormat> {
        val text = input.trim()
        if (text.length < 2) return emptyList()
        val compact = text.filterNot { it.isWhitespace() }
        return buildList {
            if (compact.all { it == '0' || it == '1' } && compact.length % 8 == 0) add(PayloadFormat.BINARY)
            if (Regex("^(0x)?([0-9a-fA-F]{2})+$").matches(compact) && compact.any { it.isLetter() || compact.length >= 8 }) add(PayloadFormat.HEX)
            if (Regex("%[0-9a-fA-F]{2}").containsMatchIn(text)) add(PayloadFormat.URL_ENCODE)
            if (Regex("&(#x?[0-9a-fA-F]+|[a-zA-Z]{2,6});").containsMatchIn(text)) add(PayloadFormat.HTML_ENTITIES)
            if (Regex("\\\\u[0-9a-fA-F]{4}").containsMatchIn(text)) add(PayloadFormat.UNICODE_ESCAPE)
            if (Regex("^[A-Z2-7]{8,}=*$").matches(compact) && compact.length % 8 == 0) add(PayloadFormat.BASE32)
            if (compact.length >= 8 && Regex("^[A-Za-z0-9+/]+={0,2}$").matches(compact) && compact.length % 4 == 0 &&
                PayloadFormat.HEX !in this
            ) add(PayloadFormat.BASE64)
            if (compact.length >= 8 && Regex("^[A-Za-z0-9_-]+$").matches(compact) && (compact.contains('-') || compact.contains('_'))) {
                add(PayloadFormat.BASE64_URL)
            }
        }
    }
}

/** Decodes JSON Web Tokens and points out the weaknesses a tester looks for. Never contacts a network. */
object JwtAnalyzer {

    private val standardClaims = setOf("iss", "sub", "aud", "exp", "nbf", "iat", "jti")
    private val sensitiveClaimHints = listOf("password", "passwd", "pwd", "secret", "ssn", "credit", "card", "api_key", "apikey", "private")
    private val hmacAlgorithms = mapOf("HS256" to "HmacSHA256", "HS384" to "HmacSHA384", "HS512" to "HmacSHA512")
    private const val LONG_LIFETIME_SECONDS = 30L * 24 * 3600

    fun looksLikeJwt(input: String): Boolean = cleanToken(input).let { it.startsWith("eyJ") && it.count { c -> c == '.' } in 1..4 }

    private fun cleanToken(input: String): String =
        input.trim().removePrefix("Authorization:").trim().removePrefix("Bearer ").removePrefix("bearer ").filterNot { it.isWhitespace() }

    fun inspect(rawToken: String, nowMillis: Long = System.currentTimeMillis()): JwtInspectionResult? {
        val token = cleanToken(rawToken)
        if (token.isEmpty()) return null
        val parts = token.split(".")
        if (parts.size == 5) {
            return invalid("This is an encrypted token (JWE, five segments). Its claims can't be read without the decryption key.")
        }
        if (parts.size !in 2..3) {
            return invalid("Malformed JWT: expected header.payload.signature, found ${parts.size} segment${if (parts.size == 1) "" else "s"}.")
        }

        val header: JsonObject
        val payload: JsonObject
        try {
            header = JsonParser.parseString(String(PayloadCodec.base64Decode(parts[0]), Charsets.UTF_8)).asJsonObject
            payload = JsonParser.parseString(String(PayloadCodec.base64Decode(parts[1]), Charsets.UTF_8)).asJsonObject
        } catch (e: Exception) {
            return invalid("Header or payload is not Base64URL-encoded JSON: ${e.message ?: e.javaClass.simpleName}")
        }

        val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
        val signatureBytes = parts.getOrNull(2)?.takeIf { it.isNotEmpty() }?.let { runCatching { PayloadCodec.base64Decode(it) }.getOrNull() }
        val algorithm = header.text("alg")
        val exp = payload.epochSeconds("exp")
        val iat = payload.epochSeconds("iat")
        val nbf = payload.epochSeconds("nbf")
        val nowSeconds = nowMillis / 1000
        val isExpired = exp?.let { nowSeconds >= it }

        val customClaims = payload.entrySet().filter { it.key !in standardClaims }
            .associate { it.key to (if (it.value.isJsonPrimitive) it.value.asString else it.value.toString()) }

        return JwtInspectionResult(
            isValidJwt = true,
            headerJson = gson.toJson(header),
            payloadJson = gson.toJson(payload),
            signatureHex = signatureBytes?.joinToString("") { "%02x".format(it) } ?: "None / unsigned",
            algorithm = algorithm,
            type = header.text("typ"),
            keyId = header.text("kid"),
            issuer = payload.text("iss"),
            subject = payload.text("sub"),
            audience = payload.text("aud"),
            jwtId = payload.text("jti"),
            issuedAtFormatted = iat?.let(::formatTime),
            expiresAtFormatted = exp?.let(::formatTime),
            notBeforeFormatted = nbf?.let(::formatTime),
            isExpired = isExpired,
            expirationRelative = exp?.let { relative(it, nowSeconds) },
            customClaims = customClaims,
            findings = findings(header, payload, algorithm, signatureBytes, exp, iat, nbf, nowSeconds),
            isHmac = algorithm?.uppercase() in hmacAlgorithms && signatureBytes != null
        )
    }

    private fun invalid(message: String) = JwtInspectionResult(isValidJwt = false, errorMessage = message)

    private fun findings(
        header: JsonObject, payload: JsonObject, algorithm: String?, signature: ByteArray?,
        exp: Long?, iat: Long?, nbf: Long?, now: Long
    ): List<JwtFinding> = buildList {
        val alg = algorithm?.uppercase()
        when {
            alg == null || alg == "NONE" -> add(
                JwtFinding(
                    RiskLevel.CRITICAL, "Unsigned token (alg: ${algorithm ?: "missing"})",
                    "Nothing protects the claims. If the server accepts this token, anyone can forge any identity."
                )
            )
            signature == null -> add(
                JwtFinding(RiskLevel.HIGH, "Signature missing", "The header declares $algorithm but the signature segment is empty.")
            )
            alg in hmacAlgorithms -> add(
                JwtFinding(
                    RiskLevel.LOW, "Symmetric signature ($algorithm)",
                    "Every party that verifies the token can also mint one, and a weak secret can be guessed offline from a single token."
                )
            )
        }
        listOf("jku", "x5u", "jwk", "x5c").filter { header.has(it) }.takeIf { it.isNotEmpty() }?.let { keys ->
            add(
                JwtFinding(
                    RiskLevel.MEDIUM, "Header supplies its own key (${keys.joinToString(", ")})",
                    "Check that the server ignores key material and key URLs from the token and only trusts its own configured keys."
                )
            )
        }
        header.text("kid")?.takeIf { kid -> kid.any { it in "/\\'\";|`$" } || ".." in kid }?.let { kid ->
            add(
                JwtFinding(
                    RiskLevel.MEDIUM, "Unusual characters in key ID",
                    "kid = \"$kid\". Key IDs used in file paths or database lookups are an injection point worth testing."
                )
            )
        }
        when {
            exp == null -> add(
                JwtFinding(RiskLevel.MEDIUM, "No expiration (exp)", "The token never expires on its own; a leaked copy stays valid until the key is rotated.")
            )
            now >= exp -> add(JwtFinding(RiskLevel.LOW, "Token expired", "Expired ${formatTime(exp)}. A server that still accepts it is not validating exp."))
            exp - (iat ?: now) > LONG_LIFETIME_SECONDS -> add(
                JwtFinding(RiskLevel.LOW, "Long-lived token", "Valid for ${(exp - (iat ?: now)) / 86400} days, which widens the window for a stolen token.")
            )
        }
        if (nbf != null && nbf > now) {
            add(JwtFinding(RiskLevel.LOW, "Not valid yet (nbf)", "Becomes valid ${formatTime(nbf)}."))
        }
        payload.keySet().filter { key -> sensitiveClaimHints.any { key.lowercase().contains(it) } }.takeIf { it.isNotEmpty() }?.let { keys ->
            add(
                JwtFinding(
                    RiskLevel.MEDIUM, "Sensitive-looking claims (${keys.joinToString(", ")})",
                    "JWT payloads are only encoded, not encrypted: anyone holding the token can read these values."
                )
            )
        }
    }.sortedByDescending { it.severity }

    /**
     * Checks an HMAC-signed token against [secret]. Returns null when the token isn't HS256/384/512
     * or is malformed. Useful to confirm a known or default secret during an assessment.
     */
    fun verifyHmac(rawToken: String, secret: String): Boolean? {
        val parts = cleanToken(rawToken).split(".")
        if (parts.size != 3 || parts[2].isEmpty()) return null
        val algorithm = runCatching {
            JsonParser.parseString(String(PayloadCodec.base64Decode(parts[0]), Charsets.UTF_8)).asJsonObject.text("alg")
        }.getOrNull()?.uppercase() ?: return null
        val macName = hmacAlgorithms[algorithm] ?: return null
        val expected = runCatching { PayloadCodec.base64Decode(parts[2]) }.getOrNull() ?: return null
        // An empty key is rejected by SecretKeySpec, and no server should accept one either.
        if (secret.isEmpty()) return false
        val mac = Mac.getInstance(macName).apply { init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), macName)) }
        val actual = mac.doFinal("${parts[0]}.${parts[1]}".toByteArray(Charsets.US_ASCII))
        return MessageDigest.isEqual(actual, expected)
    }

    private fun formatTime(epochSeconds: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(epochSeconds * 1000L))

    private fun relative(exp: Long, now: Long): String {
        val diff = abs(exp - now)
        val days = diff / 86400
        val hours = (diff % 86400) / 3600
        val minutes = (diff % 3600) / 60
        val span = if (days > 0) "${days}d ${hours}h" else "${hours}h ${minutes}m"
        return if (now >= exp) "Expired $span ago" else "Valid (expires in $span)"
    }

    /** Claim as text; arrays (a multi-valued `aud`) are joined, objects are shown as JSON. */
    private fun JsonObject.text(name: String): String? {
        val value: JsonElement = get(name)?.takeUnless { it.isJsonNull } ?: return null
        return when {
            value.isJsonPrimitive -> value.asString
            value.isJsonArray -> value.asJsonArray.joinToString(", ") { if (it.isJsonPrimitive) it.asString else it.toString() }
            else -> value.toString()
        }.takeIf { it.isNotBlank() }
    }

    /** NumericDate claims may arrive as integers, decimals or numeric strings. */
    private fun JsonObject.epochSeconds(name: String): Long? {
        val value = get(name)?.takeIf { it.isJsonPrimitive } ?: return null
        return runCatching { value.asDouble.toLong() }.getOrNull()?.takeIf { it in 0..253_402_300_799L }
    }
}
