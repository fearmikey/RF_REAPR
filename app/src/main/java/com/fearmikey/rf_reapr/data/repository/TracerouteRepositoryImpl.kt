package com.fearmikey.rf_reapr.data.repository

import com.fearmikey.rf_reapr.domain.repository.TracerouteHop
import com.fearmikey.rf_reapr.domain.repository.TracerouteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedReader
import java.io.InputStreamReader

class TracerouteRepositoryImpl : TracerouteRepository {

    override fun traceroute(host: String): Flow<List<TracerouteHop>> = flow {
        val hops = mutableListOf<TracerouteHop>()
        var reachedDestination = false
        var ttl = 1
        val maxHops = 30

        while (ttl <= maxHops && !reachedDestination) {
            val startTime = System.currentTimeMillis()
            val hopIp = runPingWithTtl(host, ttl)
            val latency = System.currentTimeMillis() - startTime
            
            val hop = TracerouteHop(
                hopNumber = ttl,
                ip = hopIp,
                hostname = null, // Hostname resolution is slow, could add later
                latencyMs = if (hopIp != null) latency else null,
                isFinal = hopIp == host || isDestination(hopIp, host)
            )
            
            hops.add(hop)
            emit(hops.toList())
            
            if (hop.isFinal) {
                reachedDestination = true
            }
            ttl++
        }
    }.flowOn(Dispatchers.IO)

    private fun runPingWithTtl(host: String, ttl: Int): String? {
        return try {
            // -t sets the TTL
            val process = Runtime.getRuntime().exec("/system/bin/ping -c 1 -t $ttl -W 2 $host")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                // Look for "From [IP]" or "[IP]: icmp_seq"
                if (line!!.contains("From", ignoreCase = true)) {
                    val parts = line!!.split(" ")
                    val fromIndex = parts.indexOfFirst { it.equals("From", ignoreCase = true) }
                    if (fromIndex != -1 && fromIndex + 1 < parts.size) {
                        return parts[fromIndex + 1].trim(':')
                    }
                } else if (line!!.contains("bytes from", ignoreCase = true)) {
                    val parts = line!!.split(" ")
                    val fromIndex = parts.indexOfFirst { it.equals("from", ignoreCase = true) }
                    if (fromIndex != -1 && fromIndex + 1 < parts.size) {
                        return parts[fromIndex + 1].trim(':')
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun isDestination(hopIp: String?, target: String): Boolean {
        if (hopIp == null) return false
        // Target might be hostname, so we might need to resolve it
        return try {
            val targetIp = java.net.InetAddress.getByName(target).hostAddress
            hopIp == targetIp
        } catch (e: Exception) {
            hopIp == target
        }
    }
}
