package com.fearmikey.rf_reapr.data.repository

import com.fearmikey.rf_reapr.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*

class HibpRepositoryImplTest {

    private val fakeSettingsRepository = object : SettingsRepository {
        override val hibpApiKey: Flow<String> = flowOf("fake-api-key")
    }

    @Test
    fun testGetBreachedAccounts_success() = runBlocking {
        // Arrange
        val mockHttpClient = mock(OkHttpClient::class.java)
        val mockCall = mock(Call::class.java)
        
        val fakeJsonResponse = """
            [
              {
                "name": "Adobe",
                "title": "Adobe",
                "domain": "adobe.com",
                "breachDate": "2013-10-04"
              }
            ]
        """.trimIndent()
        
        val fakeResponse = Response.Builder()
            .request(Request.Builder().url("https://haveibeenpwned.com/api/v3/breachedaccount/test@example.com").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(fakeJsonResponse.toResponseBody("application/json".toMediaTypeOrNull()))
            .build()

        `when`(mockHttpClient.newCall(any(Request::class.java) ?: Request.Builder().url("https://localhost").build())).thenReturn(mockCall)
        `when`(mockCall.execute()).thenReturn(fakeResponse)

        val repository = HibpRepositoryImpl(mockHttpClient, fakeSettingsRepository)

        // Act
        val result = repository.getBreachedAccounts("test@example.com")

        // Assert
        assertTrue(result.isSuccess)
        val breaches = result.getOrNull()
        assertNotNull(breaches)
        assertEquals(1, breaches!!.size)
        assertEquals("Adobe", breaches[0].name)
    }

    @Test
    fun testGetBreachedAccounts_notFound() = runBlocking {
        // Arrange
        val mockHttpClient = mock(OkHttpClient::class.java)
        val mockCall = mock(Call::class.java)
        
        val fakeResponse = Response.Builder()
            .request(Request.Builder().url("https://haveibeenpwned.com/api/v3/breachedaccount/test@example.com").build())
            .protocol(Protocol.HTTP_1_1)
            .code(404)
            .message("Not Found")
            .build()

        `when`(mockHttpClient.newCall(any(Request::class.java) ?: Request.Builder().url("https://localhost").build())).thenReturn(mockCall)
        `when`(mockCall.execute()).thenReturn(fakeResponse)

        val repository = HibpRepositoryImpl(mockHttpClient, fakeSettingsRepository)

        // Act
        val result = repository.getBreachedAccounts("test@example.com")

        // Assert
        assertTrue(result.isSuccess)
        val breaches = result.getOrNull()
        assertNotNull(breaches)
        assertTrue(breaches!!.isEmpty())
    }
}
