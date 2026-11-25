package com.fearmikey.rf_reapr.data.repository

import com.fearmikey.rf_reapr.domain.repository.SubdomainFinderRepository
import com.fearmikey.rf_reapr.domain.repository.SubdomainFinderRepository.DiscoverySource
import com.fearmikey.rf_reapr.domain.repository.SubdomainFinderRepository.SubdomainItem
import com.fearmikey.rf_reapr.domain.repository.SubdomainFinderRepository.SubdomainResult
import com.fearmikey.rf_reapr.domain.service.SubdomainTakeoverAnalyzer
import com.google.gson.JsonParser
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.util.Collections


class SubdomainFinderRepositoryImpl(
    private val client: OkHttpClient,
) : SubdomainFinderRepository {

    private val commonSubdomains = listOf(
        "www", "mail", "remote", "blog", "webmail", "server", "ns1", "ns2", "smtp",
        "vpn", "m", "shop", "ftp", "dev", "staging", "api", "test", "portal", "admin",
        "support", "cloud", "app", "secure", "direct", "cpanel", "autodiscover",
        "mx", "dns1", "dns2", "git", "gitlab", "jenkins", "docker", "registry",
        "status", "internal", "corp", "svn", "private", "beta", "alpha",
        "search", "media", "images", "video", "news", "docs", "store", "login",
        "sso", "oauth", "sip", "voip", "static", "assets", "cdn", "download",
        "update", "ws", "mqtt", "monitoring", "zabbix", "grafana", "prometheus",
        "kibana", "elastic", "sonar", "bitbucket", "confluence", "jira", "wiki",
        "forum", "community", "prod", "uat", "qa", "demo", "training", "partner",
        "resellers", "affiliates", "ads", "tracking", "analytics", "telemetry", "metrics",
        "photos", "photo", "pic", "pics", "img", "gallery", "drive", "storage",
        "maps", "map", "location", "calendar", "meet", "chat", "keep", "contacts",
        "translate", "sites", "finance", "shopping", "flights", "hotels", "travel",
        "console", "developer", "developers", "sdk", "apis", "auth", "identity",
        "accounts", "account", "signup", "register", "billing", "payment",
        "help", "press", "privacy", "terms", "legal", "security", "jobs", "careers",
        "sales", "marketing", "affiliate", "partners", "root", "manager",
        "sys", "system", "local", "backup", "archive", "old", "live", "web", "mobile",
        "apps", "v1", "v2", "music", "audio", "stream", "files", "downloads", "upload",
        "share", "code", "repo", "k8s", "kube", "kubernetes", "node", "cluster", "host",
        "ns", "dns", "pop", "imap", "relay", "gateway", "proxy", "firewall", "lb",
        "sql", "db", "database", "redis", "query", "spider", "bot", "crawler", "gpt"
    ).distinct()

    override fun findSubdomains(domain: String): Flow<SubdomainResult> = channelFlow {
        val foundSubdomains = Collections.synchronizedList(mutableListOf<SubdomainItem>())
        var processedCount = 0
        val total = commonSubdomains.size
        // Certificate logs can list thousands of dead names; bound the CNAME checks on them.
        val danglingChecks = AtomicInteger(0)

        // Add the naked domain first
        try {
            val addr = withContext(Dispatchers.IO) { InetAddress.getByName(domain) }
            foundSubdomains.add(SubdomainItem(domain, addr.hostAddress, DiscoverySource.PASSIVE))
            send(SubdomainResult(foundSubdomains.toList(), 0f))
        } catch (_: Exception) {}

        coroutineScope {
            // Launch Passive Discovery
            launch(Dispatchers.IO) {
                val passiveResults = findSubdomainsPassively(domain)
                passiveResults.forEach { hostname ->
                    try {
                        val addr = InetAddress.getByName(hostname)
                        val item = SubdomainItem(hostname, addr.hostAddress, DiscoverySource.PASSIVE)
                        synchronized(foundSubdomains) {
                            if (foundSubdomains.none { it.hostname == hostname }) {
                                foundSubdomains.add(item)
                            }
                        }
                        // Emit updates for passive findings immediately
                        val currentResults = synchronized(foundSubdomains) { foundSubdomains.toList() }
                        send(SubdomainResult(currentResults, processedCount.toFloat() / total))
                    } catch (_: Exception) {
                        // A name that no longer resolves is only interesting if it is a dangling
                        // CNAME to a hosting platform (a subdomain-takeover candidate).
                        if (danglingChecks.incrementAndGet() <= MAX_DANGLING_CHECKS) {
                            val cname = lookupCname(hostname)
                            val hint = SubdomainTakeoverAnalyzer.assess(cname, resolves = false)
                            if (hint != null) {
                                synchronized(foundSubdomains) {
                                    if (foundSubdomains.none { it.hostname == hostname }) {
                                        foundSubdomains.add(SubdomainItem(hostname, null, DiscoverySource.PASSIVE, cname, hint))
                                    }
                                }
                                val currentResults = synchronized(foundSubdomains) { foundSubdomains.toList() }
                                send(SubdomainResult(currentResults, processedCount.toFloat() / total))
                            }
                        }
                    }
                }
            }

            // Launch Brute Force
            commonSubdomains.forEach { sub ->
                launch(Dispatchers.IO) {
                    val hostname = "$sub.$domain"
                    if (hostname == domain) {
                        synchronized(this@channelFlow) { processedCount++ }
                        return@launch
                    }
                    try {
                        val addr = InetAddress.getByName(hostname)
                        val item = SubdomainItem(hostname, addr.hostAddress, DiscoverySource.BRUTE_FORCE)
                        synchronized(foundSubdomains) {
                            if (foundSubdomains.none { it.hostname == hostname }) {
                                foundSubdomains.add(item)
                            }
                        }
                    } catch (_: Exception) {
                    } finally {
                        synchronized(this@channelFlow) {
                            processedCount++
                            val progress = processedCount.toFloat() / total
                            if ((processedCount % 100 == 0) || (processedCount == total)) {
                                launch {
                                    val currentResults = synchronized(foundSubdomains) { foundSubdomains.toList() }
                                    send(SubdomainResult(currentResults, progress))
                                }
                            }
                        }
                    }
                }
            }
        }
        // Tag live names that are hosted on third-party platforms (context, not a finding).
        val resolved = synchronized(foundSubdomains) { foundSubdomains.toList() }
        val semaphore = Semaphore(8)
        val annotated = coroutineScope {
            resolved.mapIndexed { index, item ->
                async(Dispatchers.IO) {
                    if (item.cname != null || index >= MAX_CNAME_LOOKUPS) return@async item
                    val cname = semaphore.withPermit { lookupCname(item.hostname) }
                    item.copy(cname = cname, takeover = SubdomainTakeoverAnalyzer.assess(cname, resolves = true))
                }
            }.awaitAll()
        }
        send(SubdomainResult(annotated, 1f, isFinished = true))
    }

    /** The CNAME target of [hostname] via DNS-over-HTTPS, or null if it has none / the lookup fails. */
    private fun lookupCname(hostname: String): String? = try {
        val url = "https://cloudflare-dns.com/dns-query".toHttpUrl().newBuilder()
            .addQueryParameter("name", hostname)
            .addQueryParameter("type", "CNAME")
            .build()
        val request = Request.Builder().url(url).header("Accept", "application/dns-json").build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string()
            if (!response.isSuccessful || body == null) return@use null
            JsonParser.parseString(body).asJsonObject.getAsJsonArray("Answer")
                ?.map { it.asJsonObject }
                ?.firstOrNull { it.get("type")?.asInt == 5 }
                ?.get("data")?.asString?.trimEnd('.')
        }
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val MAX_DANGLING_CHECKS = 150
        const val MAX_CNAME_LOOKUPS = 200
    }

    private suspend fun findSubdomainsPassively(domain: String): List<String> = withContext(Dispatchers.IO) {
        val results = mutableSetOf<String>()
        try {
            val url = "https://crt.sh/?q=%25.$domain&output=json"
            val request = Request.Builder()
                .url(url)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                
                val jsonArray = JsonParser.parseString(body).asJsonArray
                jsonArray.forEach { element ->
                    val obj = element.asJsonObject
                    // Extraction from name_value and common_name
                    val names = mutableListOf<String>()
                    obj["common_name"]?.asString?.let { names.add(it) }
                    obj.get("name_value")?.asString?.let { valList ->
                        names.addAll(valList.split("\n"))
                    }

                    names.forEach { name ->
                        val cleanName = name.trim().lowercase()
                        if (cleanName.endsWith(".$domain") && !cleanName.contains("*")) {
                            results.add(cleanName)
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Silently fail, fallback to brute force
        }
        results.toList()
    }
}
