package com.fearmikey.rf_reapr.ui.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.repository.DnsQueryRepository
import com.fearmikey.rf_reapr.domain.repository.DnsResolver
import com.fearmikey.rf_reapr.domain.service.DnsInput
import com.fearmikey.rf_reapr.domain.service.DnsQueryResult
import com.fearmikey.rf_reapr.domain.service.DnsRecordTypes
import com.fearmikey.rf_reapr.domain.service.EmailSecurityAnalyzer
import com.fearmikey.rf_reapr.domain.service.EmailSecurityReport
import com.fearmikey.rf_reapr.domain.service.HostValidator
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Record-type choices in the UI; [ALL] runs an overview of common types plus SPF/DMARC. */
enum class DnsQueryType(val label: String, val code: Int?) {
    ALL("All", null),
    A("A", DnsRecordTypes.A),
    AAAA("AAAA", DnsRecordTypes.AAAA),
    MX("MX", DnsRecordTypes.MX),
    TXT("TXT", DnsRecordTypes.TXT),
    NS("NS", DnsRecordTypes.NS),
    CNAME("CNAME", DnsRecordTypes.CNAME),
    SOA("SOA", DnsRecordTypes.SOA),
    CAA("CAA", DnsRecordTypes.CAA),
    SRV("SRV", DnsRecordTypes.SRV),
    HTTPS("HTTPS", DnsRecordTypes.HTTPS),
    PTR("PTR", DnsRecordTypes.PTR)
}

class DnsQueryViewModel(
    private val repository: DnsQueryRepository
) : ViewModel() {

    private val _domain = MutableStateFlow("google.com")
    val domain: StateFlow<String> = _domain.asStateFlow()

    private val _type = MutableStateFlow(DnsQueryType.A)
    val type: StateFlow<DnsQueryType> = _type.asStateFlow()

    private val _resolver = MutableStateFlow(DnsResolver.CLOUDFLARE)
    val resolver: StateFlow<DnsResolver> = _resolver.asStateFlow()

    private val _results = MutableStateFlow<List<DnsQueryResult>>(emptyList())
    val results: StateFlow<List<DnsQueryResult>> = _results.asStateFlow()

    private val _emailSecurity = MutableStateFlow<EmailSecurityReport?>(null)
    val emailSecurity: StateFlow<EmailSecurityReport?> = _emailSecurity.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _history = MutableStateFlow<List<String>>(emptyList())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    private var queryJob: Job? = null

    fun setDomain(value: String) {
        _domain.value = value
        // Entering an IP almost always means a reverse lookup.
        val normalized = DnsInput.normalize(value)
        if (normalized != null && (HostValidator.isIpv4(normalized) || HostValidator.isIpv6(normalized))) {
            _type.value = DnsQueryType.PTR
        }
    }

    fun setType(value: DnsQueryType) {
        _type.value = value
    }

    fun setResolver(value: DnsResolver) {
        _resolver.value = value
    }

    fun query() {
        val name = DnsInput.normalize(_domain.value)
        if (name == null) {
            _error.value = "Enter a valid domain name or IP address"
            return
        }
        val type = _type.value
        val resolver = _resolver.value
        _error.value = null
        _emailSecurity.value = null
        _results.value = emptyList()
        _isLoading.value = true
        _history.update { (listOf(name) + it.filterNot { h -> h == name }).take(10) }

        queryJob?.cancel()
        queryJob = viewModelScope.launch {
            try {
                when {
                    type == DnsQueryType.PTR -> {
                        val reverse = DnsInput.reverseName(name) ?: name // allow querying *.arpa directly
                        _results.value = listOf(repository.query(reverse, DnsRecordTypes.PTR, resolver))
                    }
                    type == DnsQueryType.ALL -> runOverview(name, resolver)
                    else -> _results.value = listOf(repository.query(name, type.code!!, resolver))
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    // coroutineScope so the parallel lookups are children of queryJob and get cancelled with it.
    private suspend fun runOverview(name: String, resolver: DnsResolver) = coroutineScope {
        val types = if (resolver == DnsResolver.DEVICE) listOf(DnsRecordTypes.A, DnsRecordTypes.AAAA)
        else DnsRecordTypes.overviewTypes
        val overview = types.map { t -> async { repository.query(name, t, resolver) } }
        val dmarc = if (resolver != DnsResolver.DEVICE) {
            async { repository.query("_dmarc.$name", DnsRecordTypes.TXT, resolver) }
        } else null

        val results = overview.awaitAll()
        _results.value = results
        dmarc?.await()?.let { dmarcResult ->
            val txt = results.firstOrNull { it.type == DnsRecordTypes.TXT }?.answers.orEmpty()
                .filter { it.type == DnsRecordTypes.TXT }.map { it.data }
            val dmarcTxt = dmarcResult.answers.filter { it.type == DnsRecordTypes.TXT }.map { it.data }
            _emailSecurity.value = EmailSecurityAnalyzer.analyze(txt, dmarcTxt)
        }
    }

    /** Plain-text dump of all results for copying. */
    fun resultsAsText(): String = buildString {
        _results.value.forEach { r ->
            appendLine(";; ${r.query} ${r.typeName} via ${r.resolver}: ${r.statusName}${if (r.authenticated) " (DNSSEC validated)" else ""}, ${r.elapsedMs} ms")
            r.error?.let { appendLine(";; $it") }
            r.answers.forEach { a -> appendLine("${a.name}\t${a.ttl}\t${a.typeName}\t${a.data}") }
            appendLine()
        }
        _emailSecurity.value?.let { report ->
            appendLine(";; Email security")
            appendLine("SPF: ${report.spfRecord ?: "none"}")
            appendLine("DMARC: ${report.dmarcRecord ?: "none"}")
            report.findings.forEach { appendLine("- $it") }
        }
    }.trimEnd()
}
