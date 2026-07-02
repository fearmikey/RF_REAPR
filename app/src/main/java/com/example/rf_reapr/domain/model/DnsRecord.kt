package com.example.rf_reapr.domain.model

data class DnsRecord(
    val type: DnsRecordType,
    val value: String,
    val ttl: Long? = null,
    val priority: Int? = null
)

enum class DnsRecordType {
    A, MX, TXT, CNAME, NS
}

data class DnsEnumerationResult(
    val domain: String,
    val records: List<DnsRecord>,
    val timestamp: Long = System.currentTimeMillis()
)
