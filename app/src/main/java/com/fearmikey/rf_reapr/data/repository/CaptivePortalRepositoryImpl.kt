package com.fearmikey.rf_reapr.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.fearmikey.rf_reapr.domain.repository.CaptivePortalCheck
import com.fearmikey.rf_reapr.domain.repository.CaptivePortalRepository
import com.fearmikey.rf_reapr.domain.service.CaptivePortalAnalyzer
import com.fearmikey.rf_reapr.domain.service.PortalProbe
import com.fearmikey.rf_reapr.domain.service.PortalProbeResult
import com.fearmikey.rf_reapr.domain.service.SystemNetworkState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

class CaptivePortalRepositoryImpl(
    context: Context,
    okHttpClient: OkHttpClient
) : CaptivePortalRepository {

    private val appContext = context.applicationContext

    private val client = okHttpClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        // Fresh connections so a pooled connection from before the portal doesn't mask it.
        .connectionPool(okhttp3.ConnectionPool(0, 1, TimeUnit.SECONDS))
        .build()

    override suspend fun check(): CaptivePortalCheck {
        val system = systemState()
        val results = withContext(Dispatchers.IO) {
            coroutineScope { CaptivePortalAnalyzer.PROBES.map { async { probe(it) } }.awaitAll() }
        }
        return CaptivePortalCheck(system, results, CaptivePortalAnalyzer.evaluate(results, system))
    }

    private fun probe(probe: PortalProbe): PortalProbeResult {
        val started = System.currentTimeMillis()
        return try {
            val request = Request.Builder()
                .url(probe.url)
                .header("Cache-Control", "no-cache")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android) RF-REAPR")
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.let { b ->
                    val source = b.source()
                    source.request(2048)
                    source.buffer.readUtf8(minOf(source.buffer.size, 2048))
                }
                PortalProbeResult(
                    probe = probe,
                    code = response.code,
                    location = response.header("Location"),
                    bodySnippet = body?.take(300),
                    proxyHeaders = CaptivePortalAnalyzer.proxyHeaders(response.headers.toMap()),
                    elapsedMs = System.currentTimeMillis() - started
                )
            }
        } catch (e: SSLPeerUnverifiedException) {
            PortalProbeResult(probe, error = "Certificate mismatch: ${e.message}", tlsError = true, elapsedMs = System.currentTimeMillis() - started)
        } catch (e: SSLHandshakeException) {
            PortalProbeResult(probe, error = "TLS handshake failed: ${e.message}", tlsError = true, elapsedMs = System.currentTimeMillis() - started)
        } catch (e: Exception) {
            PortalProbeResult(probe, error = e.message ?: e.javaClass.simpleName, elapsedMs = System.currentTimeMillis() - started)
        }
    }

    override fun systemState(): SystemNetworkState? = runCatching {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return null
        val caps = cm.getNetworkCapabilities(network) ?: return null
        val transport = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Other"
        }
        SystemNetworkState(
            transport = transport,
            validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            captivePortalFlag = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
        )
    }.getOrNull()
}
