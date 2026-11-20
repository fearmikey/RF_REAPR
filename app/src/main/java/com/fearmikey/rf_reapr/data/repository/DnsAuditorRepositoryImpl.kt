package com.fearmikey.rf_reapr.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import com.fearmikey.rf_reapr.domain.repository.DnsAuditResult
import com.fearmikey.rf_reapr.domain.repository.DnsAuditorRepository
import com.fearmikey.rf_reapr.domain.service.DnsAuditEvaluator
import com.fearmikey.rf_reapr.domain.service.DnsObservations
import com.fearmikey.rf_reapr.domain.service.DnsRecordTypes
import com.fearmikey.rf_reapr.domain.service.DohResponseParser
import com.fearmikey.rf_reapr.domain.service.DomainComparison
import com.fearmikey.rf_reapr.domain.service.LookupOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Gathers DNS observations (system resolver vs DNS-over-HTTPS) and hands them to
 * [DnsAuditEvaluator] for the verdict.
 */
class DnsAuditorRepositoryImpl(
    private val context: Context,
    client: OkHttpClient
) : DnsAuditorRepository {

    private val http = client.newBuilder()
        .callTimeout(6, TimeUnit.SECONDS)
        .build()

    /** Hostname endpoints first; IP endpoints as fallback when DoH hostnames are blocked. */
    private val dohEndpoints = listOf(
        "https://cloudflare-dns.com/dns-query",
        "https://dns.google/resolve",
        "https://1.1.1.1/dns-query",
        "https://8.8.8.8/resolve"
    )

    override suspend fun auditDns(): DnsAuditResult = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val link = cm.getLinkProperties(cm.activeNetwork)
        val dnsServers = link?.dnsServers?.mapNotNull { it.hostAddress } ?: emptyList()
        val privateDnsActive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) link?.isPrivateDnsActive == true else false
        val privateDnsName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) link?.privateDnsServerName else null

        // Random label so no cache or legitimate record can answer it.
        val nxName = "rfreapr-${UUID.randomUUID().toString().replace("-", "").take(20)}.com"

        coroutineScope {
            val sentinels = DnsAuditEvaluator.SENTINELS.keys.map { name -> async { name to system(name) } }
            val nx = async { system(nxName) }
            val bogus = async { system(DnsAuditEvaluator.DNSSEC_BOGUS_DOMAIN) }
            val rebinding = async { system(DnsAuditEvaluator.REBIND_DOMAIN) }
            val rebindingTrusted = async { trusted(DnsAuditEvaluator.REBIND_DOMAIN) }
            val comparisons = DnsAuditEvaluator.COMPARISON_DOMAINS.map { d ->
                async { DomainComparison(d, system(d), trusted(d)) }
            }
            val compared = comparisons.awaitAll()
            val rebindTrusted = rebindingTrusted.await()

            val observations = DnsObservations(
                dnsServers = dnsServers,
                privateDnsActive = privateDnsActive,
                privateDnsServerName = privateDnsName,
                sentinels = sentinels.awaitAll().toMap(),
                nxdomainName = nxName,
                nxdomain = nx.await(),
                dnssecBogus = bogus.await(),
                rebinding = rebinding.await(),
                rebindingTrusted = rebindTrusted,
                comparisons = compared,
                trustedReachable = compared.any { it.trusted.error == null } || rebindTrusted.error == null
            )
            DnsAuditResult(observations, DnsAuditEvaluator.evaluate(observations), System.currentTimeMillis() - started)
        }
    }

    private suspend fun system(name: String): LookupOutcome = try {
        val addresses = runInterruptible { InetAddress.getAllByName(name) }
        LookupOutcome(ips = addresses.mapNotNull { it.hostAddress }.distinct())
    } catch (_: UnknownHostException) {
        LookupOutcome(nxdomain = true)
    } catch (e: Exception) {
        LookupOutcome(error = e.message ?: e.javaClass.simpleName)
    }

    private fun trusted(name: String): LookupOutcome {
        var lastError: String? = null
        for (endpoint in dohEndpoints) {
            try {
                val url = endpoint.toHttpUrl().newBuilder()
                    .addQueryParameter("name", name)
                    .addQueryParameter("type", DnsRecordTypes.A.toString())
                    .build()
                val request = Request.Builder().url(url).header("Accept", "application/dns-json").build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        lastError = "HTTP ${response.code}"
                        return@use
                    }
                    val result = DohResponseParser.parse(response.body?.string().orEmpty(), name, DnsRecordTypes.A, endpoint, 0)
                    if (result.error != null) {
                        lastError = result.error
                        return@use
                    }
                    return LookupOutcome(
                        ips = result.answers.filter { it.type == DnsRecordTypes.A }.map { it.data },
                        nxdomain = result.status == 3
                    )
                }
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
            }
        }
        return LookupOutcome(error = lastError ?: "DoH unreachable")
    }
}
