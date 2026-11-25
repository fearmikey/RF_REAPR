package com.fearmikey.rf_reapr.ui.web

import androidx.lifecycle.ViewModel
import com.fearmikey.rf_reapr.domain.model.JwtInspectionResult
import com.fearmikey.rf_reapr.domain.model.PayloadFormat
import com.fearmikey.rf_reapr.domain.service.JwtAnalyzer
import com.fearmikey.rf_reapr.domain.service.PayloadCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
    /** Encodings the input plausibly is, offered as one-tap decoders. */
    val detectedFormats: List<PayloadFormat> = emptyList(),
    /** The input is a JWT, so the inspector tab is the better tool for it. */
    val inputLooksLikeJwt: Boolean = false,
    val jwtInput: String = "",
    val jwtResult: JwtInspectionResult? = null,
    val jwtSecret: String = "",
    /** Result of testing [jwtSecret] against the token's HMAC signature; null = not tested. */
    val jwtSecretValid: Boolean? = null
)

/** All conversions are local (see [PayloadCodec] and [JwtAnalyzer]); nothing typed here leaves the device. */
class PayloadEncoderViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PayloadEncoderUiState())
    val uiState: StateFlow<PayloadEncoderUiState> = _uiState.asStateFlow()

    fun selectTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }

    fun setFormat(format: PayloadFormat) = convert { it.copy(format = format) }

    fun setMode(mode: ConverterMode) = convert { it.copy(mode = mode) }

    fun setInputText(text: String) = convert { it.copy(inputText = text) }

    /** Switches to decoding the current input as [format] (from a "looks like" suggestion). */
    fun decodeAs(format: PayloadFormat) = convert { it.copy(format = format, mode = ConverterMode.DECODE) }

    fun swapInputOutput() = convert {
        it.copy(
            inputText = it.outputText,
            mode = if (it.mode == ConverterMode.ENCODE) ConverterMode.DECODE else ConverterMode.ENCODE
        )
    }

    fun clearInput() = convert { it.copy(inputText = "") }

    /** Applies [change] and recomputes the output for the resulting input / format / mode. */
    private fun convert(change: (PayloadEncoderUiState) -> PayloadEncoderUiState) {
        _uiState.update { previous ->
            val state = change(previous)
            val input = state.inputText
            if (input.isEmpty()) {
                return@update state.copy(outputText = "", conversionError = null, detectedFormats = emptyList(), inputLooksLikeJwt = false)
            }
            val detected = PayloadCodec.detect(input)
            val looksLikeJwt = JwtAnalyzer.looksLikeJwt(input)
            try {
                val output = when (state.mode) {
                    ConverterMode.ENCODE -> PayloadCodec.encode(input, state.format)
                    ConverterMode.DECODE -> PayloadCodec.decode(input, state.format)
                }
                state.copy(outputText = output, conversionError = null, detectedFormats = detected, inputLooksLikeJwt = looksLikeJwt)
            } catch (e: Exception) {
                state.copy(
                    outputText = "",
                    conversionError = e.message ?: "This input can't be ${if (state.mode == ConverterMode.ENCODE) "encoded" else "decoded"} as ${state.format.displayName}",
                    detectedFormats = detected,
                    inputLooksLikeJwt = looksLikeJwt
                )
            }
        }
    }

    /** Hands the encoder's input over to the JWT inspector tab. */
    fun inspectInputAsJwt() {
        val token = _uiState.value.inputText
        setJwtInput(token)
        selectTab(1)
    }

    fun setJwtInput(token: String) {
        _uiState.update {
            it.copy(jwtInput = token, jwtResult = JwtAnalyzer.inspect(token), jwtSecretValid = null)
        }
    }

    fun clearJwt() {
        _uiState.update { it.copy(jwtInput = "", jwtResult = null, jwtSecret = "", jwtSecretValid = null) }
    }

    fun setJwtSecret(secret: String) {
        _uiState.update { it.copy(jwtSecret = secret, jwtSecretValid = null) }
    }

    /** Tests the entered secret against the token's HS256/384/512 signature. */
    fun verifyJwtSecret() {
        _uiState.update { it.copy(jwtSecretValid = JwtAnalyzer.verifyHmac(it.jwtInput, it.jwtSecret)) }
    }
}
