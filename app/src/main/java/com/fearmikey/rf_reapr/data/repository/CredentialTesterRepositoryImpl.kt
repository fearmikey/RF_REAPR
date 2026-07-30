package com.fearmikey.rf_reapr.data.repository

import com.fearmikey.rf_reapr.domain.repository.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import java.security.cert.X509Certificate
import java.util.regex.Pattern
import javax.net.ssl.*

class CredentialTesterRepositoryImpl(
    private val okHttpClient: OkHttpClient
) : CredentialTesterRepository {

    private val cookieStore = mutableMapOf<String, List<Cookie>>()

    private val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    })

    private val sslContext = SSLContext.getInstance("SSL").apply {
        init(null, trustAllCerts, java.security.SecureRandom())
    }

    private val sslSocketFactory = sslContext.socketFactory

    // A simple client that handles cookies manually
    private val sessionClient = okHttpClient.newBuilder()
        .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
        .hostnameVerifier { _, _ -> true }
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                cookieStore[url.host] = cookies
            }
            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return cookieStore[url.host] ?: emptyList()
            }
        })
        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    override fun testCredentials(
        target: String,
        protocol: AuthProtocol,
        credentials: List<CredentialPair>
    ): Flow<TestResult> = flow {
        
        val total = credentials.size
        val url = if (target.startsWith("http")) target else "http://$target"
        
        // Reset cookies for a new test
        cookieStore.clear()

        // For WEB_AUTO, we probe once to see what we're dealing with
        val probeResult = if (protocol == AuthProtocol.WEB_AUTO) {
            detectProtocol(url, this)
        } else {
            ProbeResult(protocol)
        }

        credentials.forEachIndexed { index, pair ->
            emit(TestResult.Progress(pair, total, index))
            
            val success = when (probeResult.protocol) {
                AuthProtocol.HTTP_BASIC -> testHttpBasic(url, pair)
                AuthProtocol.HTTP_FORM -> testHttpForm(
                    probeResult.actionUrl ?: url,
                    pair,
                    probeResult.userField,
                    probeResult.passField,
                    probeResult.extraFields,
                    this
                )
                else -> {
                    delay(500) // Simulate work for other protocols
                    false
                }
            }
            
            if (success) {
                emit(TestResult.Success(pair))
                return@flow
            } else {
                emit(TestResult.PairFailed(pair))
            }
            
            delay(200) // Rate limiting
        }
        
        emit(TestResult.Finished)
    }.flowOn(Dispatchers.IO)

    data class ProbeResult(
        val protocol: AuthProtocol,
        val actionUrl: String? = null,
        val userField: String? = null,
        val passField: String? = null,
        val extraFields: Map<String, String> = emptyMap()
    )

    private suspend fun detectProtocol(url: String, collector: kotlinx.coroutines.flow.FlowCollector<TestResult>): ProbeResult {
        // Common login paths to check
        val paths = listOf("/", "/login.cgi", "/logon.cgi", "/login.html", "/stok=/login", "/cgi-bin/login.cgi", "/admin", "/index.html", "/logon.htm")
        
        for (path in paths) {
            val fullUrl = if (url.endsWith("/") && path.startsWith("/")) url + path.substring(1) else url + path
            collector.emit(TestResult.Debug("Probing: $fullUrl"))
            
            val request = Request.Builder()
                .url(fullUrl)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:128.0) Gecko/128.0 Firefox/128.0")
                .build()
            
            try {
                val response = sessionClient.newCall(request).execute()
                collector.emit(TestResult.Debug("Response: ${response.code}"))
                
                if (response.code == 401) {
                    return ProbeResult(AuthProtocol.HTTP_BASIC)
                }
                
                val body = response.body?.string() ?: ""
                val formAction = extractFormAction(body, fullUrl)
                if (formAction != null || body.contains("type=\"password\"")) {
                    val userField = extractInputField(body, listOf("user", "login", "uname", "username"))
                    val passField = extractInputField(body, listOf("pass", "pw", "password", "pcPassword", "logPassword"))
                    val hiddenFields = extractHiddenFields(body)
                    
                    collector.emit(TestResult.Debug("Form Found: $formAction"))
                    collector.emit(TestResult.Debug("Fields: u=$userField, p=$passField, hidden=${hiddenFields.keys}"))
                    
                    return ProbeResult(AuthProtocol.HTTP_FORM, formAction ?: fullUrl, userField, passField, hiddenFields)
                } else if (response.code == 200) {
                    val snippet = if (body.length > 200) body.substring(0, 200) else body
                    collector.emit(TestResult.Debug("[DEBUG] Body Snippet: $snippet"))
                }
            } catch (e: IOException) {
                collector.emit(TestResult.Debug("Probe Error: ${e.message}"))
                continue
            }
        }
        
        return ProbeResult(AuthProtocol.HTTP_BASIC) // Fallback
    }

    private fun extractHiddenFields(html: String): Map<String, String> {
        val fields = mutableMapOf<String, String>()
        val pattern = Pattern.compile("<input[^>]*type=[\"']hidden[\"'][^>]*>", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(html)
        while (matcher.find()) {
            val tag = matcher.group()
            val name = extractAttribute(tag, "name") ?: extractAttribute(tag, "id")
            val value = extractAttribute(tag, "value") ?: ""
            if (name != null) fields[name] = value
        }
        return fields
    }

    private fun extractAttribute(tag: String, attr: String): String? {
        val pattern = Pattern.compile("$attr=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(tag)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun extractFormAction(html: String, baseUrl: String): String? {
        // Support both double and single quotes
        val pattern = Pattern.compile("<form[^>]*action=[\"']([^\"']*)[\"'][^>]*>", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(html)
        if (matcher.find()) {
            val action = matcher.group(1) ?: return baseUrl
            val baseHttpUrl = baseUrl.toHttpUrlOrNull() ?: return baseUrl
            return baseHttpUrl.resolve(action)?.toString() ?: baseUrl
        }
        return if (html.contains("type=\"password\"")) baseUrl else null
    }

    private fun extractInputField(html: String, keywords: List<String>): String? {
        // Look for input names or ids that match keywords
        for (keyword in keywords) {
            // Check name="..." or name='...'
            val namePattern = Pattern.compile("name=[\"']([^\"']*$keyword[^\"']*)[\"']", Pattern.CASE_INSENSITIVE)
            var matcher = namePattern.matcher(html)
            if (matcher.find()) return matcher.group(1)

            // Fallback to id="..." or id='...'
            val idPattern = Pattern.compile("id=[\"']([^\"']*$keyword[^\"']*)[\"']", Pattern.CASE_INSENSITIVE)
            matcher = idPattern.matcher(html)
            if (matcher.find()) return matcher.group(1)
        }
        return null
    }

    private fun testHttpBasic(url: String, pair: CredentialPair): Boolean {
        val credential = Credentials.basic(pair.username, pair.password)
        
        val request = Request.Builder()
            .url(url)
            .header("Authorization", credential)
            .build()
            
        return try {
            val response = sessionClient.newCall(request).execute()
            response.code != 401
        } catch (e: IOException) {
            false
        }
    }

    private suspend fun testHttpForm(
        actionUrl: String,
        pair: CredentialPair,
        userField: String?,
        passField: String?,
        extraFields: Map<String, String>,
        collector: kotlinx.coroutines.flow.FlowCollector<TestResult>
    ): Boolean {
        // Optimize: Prioritize exact fields or Password Only if username is missing
        val configurations = mutableListOf<Map<String, String>>()
        
        if (userField != null && passField != null) {
            configurations.add(mapOf(userField to pair.username, passField to pair.password))
        } else if (userField == null && passField != null) {
            // Priority 1: Password Only attempt (common on TP-Link)
            configurations.add(mapOf(passField to pair.password))
            // Priority 2: Common username field guesses
            listOf("username", "login_user", "userName").forEach { uf ->
                configurations.add(mapOf(uf to pair.username, passField to pair.password))
            }
        } else {
            // Fallback: Nested loop if detection failed completely
            val uFields = listOf("username", "userName", "login_user", "uname")
            val pFields = listOf("password", "loginPassword", "logPassword", "pcPassword")
            for (uf in uFields) {
                for (pf in pFields) {
                    configurations.add(mapOf(uf to pair.username, pf to pair.password))
                }
            }
        }

        val httpUrl = actionUrl.toHttpUrlOrNull() ?: "http://localhost".toHttpUrlOrNull()!!
        val origin = "${httpUrl.scheme}://${httpUrl.host}${if (httpUrl.port != HttpUrl.defaultPort(httpUrl.scheme)) ":${httpUrl.port}" else ""}"

        for (config in configurations) {
            val formBodyBuilder = FormBody.Builder()
            config.forEach { (k, v) -> formBodyBuilder.add(k, v) }
            
            // Add all extracted hidden fields
            extraFields.forEach { (k, v) -> formBodyBuilder.add(k, v) }

            // Some switches require an explicit "login" or "submit" button value
            if (!extraFields.containsKey("login") && !config.containsKey("login")) formBodyBuilder.add("login", "Login")
            if (!extraFields.containsKey("submit") && !config.containsKey("submit")) formBodyBuilder.add("submit", "Login")
            
            val formBody = formBodyBuilder.build()
            
            collector.emit(TestResult.Debug("[DEBUG] URL: $actionUrl"))
            // [DEBUG] Log the exact key-value pairs
            val bodyLog = StringBuilder()
            for (i in 0 until formBody.size) {
                bodyLog.append("${formBody.name(i)}=${formBody.value(i)}")
                if (i < formBody.size - 1) bodyLog.append(", ")
            }
            collector.emit(TestResult.Debug("POST body: $bodyLog"))

            val request = Request.Builder()
                .url(actionUrl)
                .header("Referer", actionUrl)
                .header("Origin", origin)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:128.0) Gecko/128.0 Firefox/128.0")
                .post(formBody)
                .build()

            try {
                val response = sessionClient.newCall(request).execute()
                
                // Success Detection Heuristics:
                val hasSetCookie = response.header("Set-Cookie") != null
                if (hasSetCookie) {
                    collector.emit(TestResult.Debug("Success marker: Set-Cookie detected"))
                }

                if (response.code in 300..399) {
                    collector.emit(TestResult.Debug("Success marker: Redirect (${response.code})"))
                    return true
                }
                
                val body = response.body?.string() ?: ""
                
                val isSuccess = hasSetCookie || (response.code == 200 && (
                    !body.contains("type=\"password\"", ignoreCase = true) || 
                    body.contains("location.href", ignoreCase = true) || 
                    body.contains("location.replace", ignoreCase = true) ||
                    body.contains("Home") || body.contains("Status") || body.contains("Logout")
                ))
                
                if (isSuccess) {
                    if (!hasSetCookie) collector.emit(TestResult.Debug("Success marker: Content heuristic"))
                    return true
                }
            } catch (e: IOException) {
                collector.emit(TestResult.Debug("POST Error: ${e.message}"))
                continue
            }
        }
        return false
    }
}
