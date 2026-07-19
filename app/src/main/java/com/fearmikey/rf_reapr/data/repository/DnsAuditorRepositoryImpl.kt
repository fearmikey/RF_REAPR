package com.fearmikey.rf_reapr.data.repository

import android.content.Context
import android.net.ConnectivityManager
import com.fearmikey.rf_reapr.domain.repository.DnsAuditResult
import com.fearmikey.rf_reapr.domain.repository.DnsAuditorRepository
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress

class DnsAuditorRepositoryImpl(
    private val context: Context,
    private val client: OkHttpClient
) : DnsAuditorRepository {

    override suspend fun auditDns(): DnsAuditResult = withContext(Dispatchers.IO) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val linkProperties = connectivityManager.getLinkProperties(connectivityManager.activeNetwork)
        val dnsServers = linkProperties?.dnsServers?.mapNotNull { it.hostAddress } ?: emptyList()

        val privateDnsMode = try {
            android.provider.Settings.Global.getString(context.contentResolver, "private_dns_mode")
        } catch (e: Exception) {
            null
        }

        val targetDomain = "google.com" // Switch back to google.com as it's the ultimate test
        var systemIp = ""
        var systemError: String? = null
        
        // 1. System Resolution
        try {
            systemIp = InetAddress.getByName(targetDomain).hostAddress ?: ""
        } catch (e: Exception) {
            systemError = e.message ?: "Unknown system resolution error"
        }

        val resolvedHostname = if (systemIp.isNotEmpty()) {
            try {
                InetAddress.getByName(systemIp).canonicalHostName
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }

        val isPrivateIp = if (systemIp.isNotEmpty()) {
            isIpPrivate(systemIp)
        } else {
            false
        }

        // 2. Trusted Resolution (via DoH) with Bootstrapping
        var trustedIps = emptyList<String>()
        var trustedError: String? = null
        
        try {
            // Try Cloudflare first (Hostname then Bootstrap IP)
            trustedIps = fetchTrustedIps(targetDomain, "https://cloudflare-dns.com/dns-query", "1.1.1.1")
            
            if (trustedIps.isEmpty()) {
                // Try Google (Hostname then Bootstrap IP)
                trustedIps = fetchTrustedIps(targetDomain, "https://dns.google/resolve", "8.8.8.8")
            }
        } catch (e: Exception) {
            trustedError = e.message ?: "Unknown trusted resolution error"
        }

        if (trustedIps.isEmpty() && trustedError == null) {
            trustedError = "No trusted results found"
        }

        val resolutionMatch = if (systemIp.isEmpty() || trustedIps.isEmpty()) {
            false
        } else {
            trustedIps.contains(systemIp) || 
            trustedIps.any { isSameSubnet(systemIp, it) } ||
            isKnownProvider(resolvedHostname, targetDomain)
        }

        DnsAuditResult(
            systemDnsServers = dnsServers,
            canResolveKnownDomain = systemIp.isNotEmpty(),
            resolutionMatch = resolutionMatch,
            systemIp = systemIp,
            trustedIps = trustedIps,
            isPrivateIp = isPrivateIp,
            privateDnsMode = privateDnsMode,
            testDomain = targetDomain,
            systemError = systemError,
            trustedError = trustedError,
            resolvedHostname = resolvedHostname
        )
    }

    private fun isKnownProvider(hostname: String?, domain: String): Boolean {
        if (hostname == null) return false
        val h = hostname.lowercase()
        return when (domain) {
            "google.com" -> h.endsWith(".1e100.net") || h.endsWith(".google.com") || h == "google.com"
            "one.one.one.one" -> h.endsWith(".cloudflare.com") || h == "one.one.one.one"
            else -> h.contains(domain.substringBefore("."))
        }
    }

    private fun fetchTrustedIps(domain: String, baseUrl: String, bootstrapIp: String): List<String> {
        val host = baseUrl.substringAfter("https://").substringBefore("/")
        
        // Try Hostname resolution first
        val ipsByHost = tryFetch(domain, baseUrl, host)
        if (ipsByHost.isNotEmpty()) return ipsByHost
        
        // If hostname fails, use Bootstrap IP
        val ipUrl = baseUrl.replace(host, bootstrapIp)
        return tryFetch(domain, ipUrl, host)
    }

    private fun tryFetch(domain: String, url: String, host: String): List<String> {
        val fullUrl = "$url?name=$domain&type=A"
        val request = Request.Builder()
            .url(fullUrl)
            .header("Accept", "application/dns-json")
            .header("Host", host) // Crucial for SSL/SNI verification when using IP URL
            .build()
        
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: ""
                val json = JsonParser.parseString(body).asJsonObject
                val answerArray = json.getAsJsonArray("Answer")
                val ips = mutableListOf<String>()
                answerArray?.forEach { element ->
                    val obj = element.asJsonObject
                    val type = obj.get("type")
                    if (type != null && type.isJsonPrimitive && (type.asInt == 1 || type.asString == "1")) {
                        ips.add(obj.get("data").asString)
                    }
                }
                ips
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun isIpPrivate(ip: String): Boolean {
        return try {
            val addr = InetAddress.getByName(ip)
            if (addr.isSiteLocalAddress || addr.isLoopbackAddress || addr.isLinkLocalAddress) return true
            
            // Manual range checks for common private ranges
            val parts = ip.split(".")
            if (parts.size != 4) return false
            val p0 = parts[0].toIntOrNull() ?: return false
            val p1 = parts[1].toIntOrNull() ?: return false
            
            p0 == 10 || 
            (p0 == 172 && p1 in 16..31) || 
            (p0 == 192 && p1 == 168) ||
            (p0 == 100 && p1 in 64..127) // CGNAT
        } catch (e: Exception) {
            false
        }
    }

    private fun isSameSubnet(ip1: String, ip2: String): Boolean {
        // Simple check for large CDNs where IPs might vary slightly (e.g. 74.125.x.x)
        val p1 = ip1.split(".")
        val p2 = ip2.split(".")
        if (p1.size < 2 || p2.size < 2) return false
        return p1[0] == p2[0] && p1[1] == p2[1]
    }
}
