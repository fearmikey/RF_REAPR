package com.fearmikey.rf_reapr.ui.web

import com.fearmikey.rf_reapr.domain.model.PayloadFormat
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PayloadEncoderViewModelTest {

    private lateinit var viewModel: PayloadEncoderViewModel

    @Before
    fun setup() {
        viewModel = PayloadEncoderViewModel()
    }

    @Test
    fun testBase64EncodingAndDecoding() {
        viewModel.setFormat(PayloadFormat.BASE64)
        viewModel.setMode(ConverterMode.ENCODE)
        viewModel.setInputText("Hello, World!")
        assertEquals("SGVsbG8sIFdvcmxkIQ==", viewModel.uiState.value.outputText)

        // Swap to decode
        viewModel.swapInputOutput()
        assertEquals(ConverterMode.DECODE, viewModel.uiState.value.mode)
        assertEquals("Hello, World!", viewModel.uiState.value.outputText)
    }

    @Test
    fun testHexEncodingAndDecoding() {
        viewModel.setFormat(PayloadFormat.HEX)
        viewModel.setMode(ConverterMode.ENCODE)
        viewModel.setInputText("RF-REAPR")
        assertEquals("52462d5245415052", viewModel.uiState.value.outputText)

        viewModel.setMode(ConverterMode.DECODE)
        viewModel.setInputText("52462d5245415052")
        assertEquals("RF-REAPR", viewModel.uiState.value.outputText)
    }

    @Test
    fun testRot13Encoding() {
        viewModel.setFormat(PayloadFormat.ROT13)
        viewModel.setMode(ConverterMode.ENCODE)
        viewModel.setInputText("Hello World!")
        assertEquals("Uryyb Jbeyq!", viewModel.uiState.value.outputText)

        viewModel.setInputText("Uryyb Jbeyq!")
        assertEquals("Hello World!", viewModel.uiState.value.outputText)
    }

    @Test
    fun testUrlEncodingAndDecoding() {
        viewModel.setFormat(PayloadFormat.URL_ENCODE)
        viewModel.setMode(ConverterMode.ENCODE)
        viewModel.setInputText("test space&param=value")
        assertTrue(viewModel.uiState.value.outputText.contains("%26") || viewModel.uiState.value.outputText.contains("param%3Dvalue"))

        viewModel.setMode(ConverterMode.DECODE)
        viewModel.setInputText("test+space%26param%3Dvalue")
        assertEquals("test space&param=value", viewModel.uiState.value.outputText)
    }

    @Test
    fun testBinaryEncodingAndDecoding() {
        viewModel.setFormat(PayloadFormat.BINARY)
        viewModel.setMode(ConverterMode.ENCODE)
        viewModel.setInputText("A")
        assertEquals("01000001", viewModel.uiState.value.outputText)

        viewModel.setMode(ConverterMode.DECODE)
        viewModel.setInputText("01000001")
        assertEquals("A", viewModel.uiState.value.outputText)
    }

    @Test
    fun testJwtInspectionValid() {
        // Sample token with alg=HS256, sub=1234567890, exp in 2030
        val sampleToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkFsaWNlIFNlY3VyaXR5IiwiaWF0IjoxNTE2MjM5MDIyLCJleHAiOjE5MTYyMzkwMjIsImlzcyI6Imh0dHBzOi8vcmYtcmVhcHIubG9jYWwifQ.4pzaw4xkwB-y73eB3770sL4hC67FvO6_Jv82fL6a_8g"
        viewModel.setJwtInput(sampleToken)

        val result = viewModel.uiState.value.jwtResult
        assertNotNull(result)
        assertTrue(result!!.isValidJwt)
        assertEquals("HS256", result.algorithm)
        assertEquals("JWT", result.type)
        assertEquals("1234567890", result.subject)
        assertEquals("https://rf-reapr.local", result.issuer)
        assertFalse(result.isExpired == true)
        assertTrue(result.payloadJson.contains("Alice Security"))
    }

    @Test
    fun testJwtInspectionMalformed() {
        viewModel.setJwtInput("not.a.valid.jwt.token.too.many.parts")
        val result = viewModel.uiState.value.jwtResult
        assertNotNull(result)
        assertFalse(result!!.isValidJwt)
        assertNotNull(result.errorMessage)
    }
}
