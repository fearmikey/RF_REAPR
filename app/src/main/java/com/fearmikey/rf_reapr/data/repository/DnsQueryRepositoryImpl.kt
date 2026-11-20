package com.fearmikey.rf_reapr.data.repository

import com.fearmikey.rf_reapr.domain.repository.DnsQueryRepository
import com.fearmikey.rf_reapr.domain.repository.DnsResolver
import com.fearmikey.rf_reapr.domain.service.DnsQueryResult
import com.fearmikey.rf_reapr.domain.service.DnsRecord
import com.fearmikey.rf_reapr.domain.service.DnsRecordTypes
import com.fearmikey.rf_reapr.domain.service.DohResponseParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * DNS lookups via DNS-over-HTTPS (JSON API) or the device resolver. Reuses the app's shared
 * OkHttpClient instead of creating one per query.
 */
class DnsQueryRepositoryImpl(private val okHttpClient: OkHttpClient) : DnsQueryRepository {

    override suspend fun query(name: String, type: Int, resolver: DnsResolver): DnsQueryResult = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val dohUrl = resolver.dohUrl
            ?: return@withContext queryDevice(name, type, started)
        try {
            // Built with HttpUrl so the name is properly encoded (it used to be concatenated
            // into the URL, allowing extra query parameters to be injected).
            val url = dohUrl.toHttpUrl().newBuilder()
                .addQueryParameter("name", name)
                .addQueryParameter("type", type.toString())
                .build()
            val request = Request.Builder().url(url).header("Accept", "application/dns-json").build()
            okHttpClient.newCall(request).execute().use { response ->
                val elapsed = System.currentTimeMillis() - started
                if (!response.isSuccessful) {
                    return@withContext DnsQueryResult(name, type, resolver.label, elapsedMs = elapsed, error = "HTTP ${response.code}")
                }
                DohResponseParser.parse(response.body?.string().orEmpty(), name, type, resolver.label, elapsed)
            }
        } catch (e: Exception) {
            DnsQueryResult(
                name, type, resolver.label,
                elapsedMs = System.currentTimeMillis() - started,
                error = e.message ?: "Query failed"
            )
        }
    }

    private fun queryDevice(name: String, type: Int, started: Long): DnsQueryResult {
        val label = DnsResolver.DEVICE.label
        if (type != DnsRecordTypes.A && type != DnsRecordTypes.AAAA) {
            return DnsQueryResult(name, type, label, error = "The device resolver only supports A and AAAA lookups")
        }
        return try {
            val addresses = InetAddress.getAllByName(name).filter {
                if (type == DnsRecordTypes.A) it is Inet4Address else it is Inet6Address
            }
            DnsQueryResult(
                query = name,
                type = type,
                resolver = label,
                status = 0,
                answers = addresses.map { DnsRecord(name, type, 0, it.hostAddress.orEmpty()) },
                elapsedMs = System.currentTimeMillis() - started
            )
        } catch (_: UnknownHostException) {
            DnsQueryResult(name, type, label, status = 3, elapsedMs = System.currentTimeMillis() - started)
        } catch (e: Exception) {
            DnsQueryResult(name, type, label, elapsedMs = System.currentTimeMillis() - started, error = e.message)
        }
    }
}
