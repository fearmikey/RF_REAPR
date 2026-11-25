package com.fearmikey.rf_reapr.ui.web

import androidx.lifecycle.ViewModel
import com.fearmikey.rf_reapr.domain.model.PhoneOsintResult
import com.fearmikey.rf_reapr.domain.service.PhoneCountry
import com.fearmikey.rf_reapr.domain.service.PhoneNumberAnalyzer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PhoneOsintUiState(
    val inputPhone: String = "",
    /** Calling code assumed for numbers typed without "+" or "00", e.g. "+1". */
    val defaultCountryCode: String = "+1",
    val result: PhoneOsintResult? = null,
    val errorMessage: String? = null
)

/** Parsing is entirely offline (see [PhoneNumberAnalyzer]); nothing is looked up until the user opens a link. */
class PhoneOsintViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PhoneOsintUiState())
    val uiState: StateFlow<PhoneOsintUiState> = _uiState.asStateFlow()

    val countries: List<PhoneCountry> get() = PhoneNumberAnalyzer.countries

    fun updateInputPhone(input: String) = analyze(input, _uiState.value.defaultCountryCode)

    fun setDefaultCountryCode(code: String) = analyze(_uiState.value.inputPhone, code)

    fun clear() {
        _uiState.update { it.copy(inputPhone = "", result = null, errorMessage = null) }
    }

    fun report(): String? = _uiState.value.result?.let(PhoneNumberAnalyzer::report)

    private fun analyze(input: String, defaultCode: String) {
        val parsed = PhoneNumberAnalyzer.analyze(input, defaultCode)
        _uiState.value = PhoneOsintUiState(input, defaultCode, parsed.result, parsed.error)
    }
}
