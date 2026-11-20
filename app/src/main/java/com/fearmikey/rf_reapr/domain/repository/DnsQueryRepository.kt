package com.fearmikey.rf_reapr.domain.repository

import com.fearmikey.rf_reapr.domain.service.DnsQueryResult

enum class DnsResolver(val label: String, val dohUrl: String?) {
    CLOUDFLARE("Cloudflare", "https://cloudflare-dns.com/dns-query"),
    GOOGLE("Google", "https://dns.google/resolve"),
    /** The device's own resolver (works offline on a LAN, A/AAAA only). */
    DEVICE("Device", null)
}

interface DnsQueryRepository {
    suspend fun query(name: String, type: Int, resolver: DnsResolver): DnsQueryResult
}
