package com.fearmikey.rf_reapr.data.repository

import com.fearmikey.rf_reapr.domain.repository.SubdomainFinderRepository
import com.fearmikey.rf_reapr.domain.repository.SubdomainFinderRepository.DiscoverySource
import com.fearmikey.rf_reapr.domain.repository.SubdomainFinderRepository.SubdomainItem
import com.fearmikey.rf_reapr.domain.repository.SubdomainFinderRepository.SubdomainResult
import com.google.gson.JsonParser
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
                    } catch (_: Exception) {}
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
        val finalResults = synchronized(foundSubdomains) { foundSubdomains.toList() }
        send(SubdomainResult(finalResults, 1f, isFinished = true))
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
