package com.fearmikey.rf_reapr.ui.web

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PhoneOsintViewModelTest {

    private lateinit var viewModel: PhoneOsintViewModel

    @Before
    fun setup() {
        viewModel = PhoneOsintViewModel()
    }

    @Test
    fun testParseUsPhoneNumber() {
        viewModel.updateInputPhone("+1 (415) 555-2671")
        val result = viewModel.uiState.value.result

        assertNotNull(result)
        assertEquals("+14155552671", result!!.e164)
        assertEquals("14155552671", result.digitsOnly)
        assertEquals("+1", result.countryCallingCode)
        assertEquals("US", result.countryIso)
        assertEquals("4155552671", result.nationalNumber)
        assertTrue(result.isValidFormat)

        val whatsappLink = result.osintLinks.find { it.title.contains("WhatsApp") }
        assertNotNull(whatsappLink)
        assertEquals("https://wa.me/14155552671", whatsappLink!!.url)
    }

    @Test
    fun testParseUkMobileNumber() {
        viewModel.updateInputPhone("+44 7911 123456")
        val result = viewModel.uiState.value.result

        assertNotNull(result)
        assertEquals("+447911123456", result!!.e164)
        assertEquals("+44", result.countryCallingCode)
        assertEquals("GB", result.countryIso)
        assertEquals("Mobile", result.estimatedLineType)
        assertTrue(result.isValidFormat)
    }

    @Test
    fun testParseGermanNumber() {
        viewModel.updateInputPhone("+49 151 23456789")
        val result = viewModel.uiState.value.result

        assertNotNull(result)
        assertEquals("+4915123456789", result!!.e164)
        assertEquals("+49", result.countryCallingCode)
        assertEquals("DE", result.countryIso)
        assertEquals("Mobile", result.estimatedLineType)
    }

    @Test
    fun testParseTollFreeNumber() {
        viewModel.updateInputPhone("+1 (800) 555-0199")
        val result = viewModel.uiState.value.result

        assertNotNull(result)
        assertTrue(result!!.estimatedLineType.contains("Toll-Free"))
    }

    @Test
    fun testParseInternationalExitCode00() {
        viewModel.updateInputPhone("0014155552671")
        val result = viewModel.uiState.value.result

        assertNotNull(result)
        assertEquals("+14155552671", result!!.e164)
        assertEquals("+1", result.countryCallingCode)
    }

    @Test
    fun testNumberTooShortError() {
        viewModel.updateInputPhone("123")
        assertNull(viewModel.uiState.value.result)
        assertNotNull(viewModel.uiState.value.errorMessage)
    }
}
