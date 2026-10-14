package com.fearmikey.rf_reapr.ui.web

import android.util.Base64
import androidx.lifecycle.ViewModel
import com.fearmikey.rf_reapr.domain.model.JwtInspectionResult
import com.fearmikey.rf_reapr.domain.model.PayloadFormat
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class ConverterMode {
    ENCODE,
    DECODE
}

data class PayloadEncoderUiState(
    val selectedTab: Int = 0, // 0 = Encoder/Decoder, 1 = JWT Inspector
    val format: PayloadFormat = PayloadFormat.BASE64,
    val mode: ConverterMode = ConverterMode.ENCODE,
    val inputText: String = "",
    val outputText: String = "",
    val conversionError: String? = null,
    val jwtInput: String = "",
    val jwtResult: JwtInspectionResult? = null
)

class PayloadEncoderViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PayloadEncoderUiState())
    val uiState: StateFlow<PayloadEncoderUiState> = _uiState.asStateFlow()

    fun selectTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }

    fun setFormat(format: PayloadFormat) {
        _uiState.update { it.copy(format = format) }
        recalculateConversion()
    }

    fun setMode(mode: ConverterMode) {
        _uiState.update { it.copy(mode = mode) }
        recalculateConversion()
    }

    fun setInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
        recalculateConversion()
    }

    fun swapInputOutput() {
        val currentOutput = _uiState.value.outputText
        val newMode = if (_uiState.value.mode == ConverterMode.ENCODE) ConverterMode.DECODE else ConverterMode.ENCODE
        _uiState.update {
            it.copy(
                inputText = currentOutput,
                mode = newMode
            )
        }
        recalculateConversion()
    }

    fun clearInput() {
        _uiState.update { it.copy(inputText = "", outputText = "", conversionError = null) }
    }

    private fun recalculateConversion() {
        val state = _uiState.value
        val input = state.inputText
        if (input.isEmpty()) {
            _uiState.update { it.copy(outputText = "", conversionError = null) }
            return
        }

        try {
            val result = when (state.mode) {
                ConverterMode.ENCODE -> encodePayload(input, state.format)
                ConverterMode.DECODE -> decodePayload(input, state.format)
            }
            _uiState.update { it.copy(outputText = result, conversionError = null) }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    outputText = "",
                    conversionError = e.localizedMessage ?: "Conversion error"
                )
            }
        }
    }

    private fun encodePayload(input: String, format: PayloadFormat): String {
        val bytes = input.toByteArray(StandardCharsets.UTF_8)
        return when (format) {
            PayloadFormat.BASE64 -> encodeBase64(bytes, urlSafe = false)
            PayloadFormat.BASE64_URL -> encodeBase64(bytes, urlSafe = true)
            PayloadFormat.HEX -> bytes.joinToString("") { "%02x".format(it) }
            PayloadFormat.URL_ENCODE -> URLEncoder.encode(input, StandardCharsets.UTF_8.toString())
            PayloadFormat.HTML_ENTITIES -> encodeHtmlEntities(input)
            PayloadFormat.BINARY -> bytes.joinToString(" ") {
                String.format("%8s", Integer.toBinaryString(it.toInt() and 0xFF)).replace(' ', '0')
            }
            PayloadFormat.ROT13 -> rot13(input)
        }
    }

    private fun decodePayload(input: String, format: PayloadFormat): String {
        return when (format) {
            PayloadFormat.BASE64 -> {
                val decoded = decodeBase64(input, urlSafe = false)
                String(decoded, StandardCharsets.UTF_8)
            }
            PayloadFormat.BASE64_URL -> {
                val decoded = decodeBase64(input, urlSafe = true)
                String(decoded, StandardCharsets.UTF_8)
            }
            PayloadFormat.HEX -> {
                val clean = input.replace("\\s+".toRegex(), "").replace("0x", "")
                if (clean.length % 2 != 0) throw IllegalArgumentException("Hex string length must be even")
                val bytes = ByteArray(clean.length / 2)
                for (i in clean.indices step 2) {
                    bytes[i / 2] = clean.substring(i, i + 2).toInt(16).toByte()
                }
                String(bytes, StandardCharsets.UTF_8)
            }
            PayloadFormat.URL_ENCODE -> URLDecoder.decode(input, StandardCharsets.UTF_8.toString())
            PayloadFormat.HTML_ENTITIES -> decodeHtmlEntities(input)
            PayloadFormat.BINARY -> {
                val clean = input.trim()
                val chunks = if (clean.contains(" ")) {
                    clean.split("\\s+".toRegex())
                } else {
                    clean.chunked(8)
                }
                val bytes = chunks.map { chunk ->
                    if (!chunk.matches("[01]+".toRegex())) throw IllegalArgumentException("Binary string must only contain 0 and 1")
                    chunk.toInt(2).toByte()
                }.toByteArray()
                String(bytes, StandardCharsets.UTF_8)
            }
            PayloadFormat.ROT13 -> rot13(input)
        }
    }

    private fun encodeBase64(bytes: ByteArray, urlSafe: Boolean): String {
        return try {
            if (urlSafe) {
                java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            } else {
                java.util.Base64.getEncoder().encodeToString(bytes)
            }
        } catch (_: Throwable) {
            if (urlSafe) {
                Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            } else {
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
        }
    }

    private fun decodeBase64(input: String, urlSafe: Boolean): ByteArray {
        val clean = input.trim()
        return try {
            if (urlSafe) {
                var s = clean.replace('-', '+').replace('_', '/')
                while (s.length % 4 != 0) s += "="
                java.util.Base64.getDecoder().decode(s)
            } else {
                java.util.Base64.getDecoder().decode(clean)
            }
        } catch (_: Throwable) {
            Base64.decode(clean, if (urlSafe) Base64.URL_SAFE else Base64.DEFAULT)
        }
    }

    private fun rot13(input: String): String {
        val sb = StringBuilder(input.length)
        for (c in input) {
            when (c) {
                in 'a'..'z' -> sb.append(((c - 'a' + 13) % 26 + 'a'.code).toChar())
                in 'A'..'Z' -> sb.append(((c - 'A' + 13) % 26 + 'A'.code).toChar())
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun encodeHtmlEntities(input: String): String {
        val sb = StringBuilder()
        for (c in input) {
            when (c) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&#39;")
                else -> {
                    if (c.code > 127) {
                        sb.append("&#x").append(Integer.toHexString(c.code)).append(";")
                    } else {
                        sb.append(c)
                    }
                }
            }
        }
        return sb.toString()
    }

    private fun decodeHtmlEntities(input: String): String {
        return input
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&#x([0-9a-fA-F]+);".toRegex()) { match ->
                val code = match.groupValues[1].toInt(16)
                code.toChar().toString()
            }
            .replace("&#([0-9]+);".toRegex()) { match ->
                val code = match.groupValues[1].toInt(10)
                code.toChar().toString()
            }
    }

    fun setJwtInput(token: String) {
        _uiState.update { it.copy(jwtInput = token) }
        inspectJwt(token)
    }

    fun clearJwt() {
        _uiState.update { it.copy(jwtInput = "", jwtResult = null) }
    }

    private fun inspectJwt(rawJwt: String) {
        val token = rawJwt.trim()
        if (token.isEmpty()) {
            _uiState.update { it.copy(jwtResult = null) }
            return
        }

        val parts = token.split(".")
        if (parts.size < 2 || parts.size > 3) {
            _uiState.update {
                it.copy(
                    jwtResult = JwtInspectionResult(
                        isValidJwt = false,
                        errorMessage = "Malformed JWT structure. Expected 2 or 3 dot-separated segments (header.payload.signature), but found ${parts.size}."
                    )
                )
            }
            return
        }

        try {
            val gson = GsonBuilder().setPrettyPrinting().create()

            val headerBytes = decodeBase64(parts[0], urlSafe = true)
            val headerString = String(headerBytes, StandardCharsets.UTF_8)
            val headerElement = JsonParser.parseString(headerString)
            val headerJsonFormatted = gson.toJson(headerElement)
            val headerJsonObj = headerElement.asJsonObject

            val payloadBytes = decodeBase64(parts[1], urlSafe = true)
            val payloadString = String(payloadBytes, StandardCharsets.UTF_8)
            val payloadElement = JsonParser.parseString(payloadString)
            val payloadJsonFormatted = gson.toJson(payloadElement)
            val payloadJsonObj = payloadElement.asJsonObject

            val signatureHex = if (parts.size == 3 && parts[2].isNotEmpty()) {
                val sigBytes = decodeBase64(parts[2], urlSafe = true)
                sigBytes.joinToString("") { "%02x".format(it) }
            } else {
                "None / Unsigned"
            }

            val alg = headerJsonObj.get("alg")?.asString
            val typ = headerJsonObj.get("typ")?.asString

            val iss = payloadJsonObj.get("iss")?.asString
            val sub = payloadJsonObj.get("sub")?.asString
            val aud = payloadJsonObj.get("aud")?.asString
            val jti = payloadJsonObj.get("jti")?.asString

            val expSeconds = payloadJsonObj.get("exp")?.asLong
            val iatSeconds = payloadJsonObj.get("iat")?.asLong
            val nbfSeconds = payloadJsonObj.get("nbf")?.asLong

            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }

            val issuedAtFormatted = iatSeconds?.let { dateFormat.format(Date(it * 1000L)) }
            val expiresAtFormatted = expSeconds?.let { dateFormat.format(Date(it * 1000L)) }
            val notBeforeFormatted = nbfSeconds?.let { dateFormat.format(Date(it * 1000L)) }

            var isExpired: Boolean? = null
            var relativeExp: String? = null

            if (expSeconds != null) {
                val expMillis = expSeconds * 1000L
                val now = System.currentTimeMillis()
                isExpired = now > expMillis
                val diffSeconds = Math.abs(now - expMillis) / 1000
                val hours = diffSeconds / 3600
                val minutes = (diffSeconds % 3600) / 60

                relativeExp = if (isExpired) {
                    if (hours > 24) "Expired ${hours / 24}d ${hours % 24}h ago"
                    else "Expired ${hours}h ${minutes}m ago"
                } else {
                    if (hours > 24) "Valid (expires in ${hours / 24}d ${hours % 24}h)"
                    else "Valid (expires in ${hours}h ${minutes}m)"
                }
            }

            val customClaims = mutableMapOf<String, String>()
            val standardKeys = setOf("iss", "sub", "aud", "exp", "nbf", "iat", "jti")
            for (entry in payloadJsonObj.entrySet()) {
                if (entry.key !in standardKeys) {
                    val element = entry.value
                    customClaims[entry.key] = if (element.isJsonPrimitive) element.asString else element.toString()
                }
            }

            _uiState.update {
                it.copy(
                    jwtResult = JwtInspectionResult(
                        isValidJwt = true,
                        headerJson = headerJsonFormatted,
                        payloadJson = payloadJsonFormatted,
                        signatureHex = signatureHex,
                        algorithm = alg,
                        type = typ,
                        issuer = iss,
                        subject = sub,
                        audience = aud,
                        jwtId = jti,
                        issuedAtFormatted = issuedAtFormatted,
                        expiresAtFormatted = expiresAtFormatted,
                        notBeforeFormatted = notBeforeFormatted,
                        isExpired = isExpired,
                        expirationRelative = relativeExp,
                        customClaims = customClaims
                    )
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    jwtResult = JwtInspectionResult(
                        isValidJwt = false,
                        errorMessage = "Failed to parse JWT: ${e.localizedMessage}"
                    )
                )
            }
        }
    }
}
