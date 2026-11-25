package com.fearmikey.rf_reapr.ui.web

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fearmikey.rf_reapr.domain.model.InternetDbResponse
import com.fearmikey.rf_reapr.domain.model.ShodanHostReport
import com.fearmikey.rf_reapr.domain.model.ShodanMatch
import com.fearmikey.rf_reapr.domain.model.ShodanService
import com.fearmikey.rf_reapr.domain.service.InternetExposure
import com.fearmikey.rf_reapr.domain.service.ShodanAnalyzer
import com.fearmikey.rf_reapr.ui.components.ScopeWarning
import com.fearmikey.rf_reapr.ui.scanner.SeverityBadge
import com.fearmikey.rf_reapr.ui.theme.PhysicalRed
import com.fearmikey.rf_reapr.ui.theme.WebGold

private const val MODE_QUICK = 0
private const val MODE_HOST = 1
private const val MODE_SEARCH = 2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShodanScreen(
    viewModel: ShodanViewModel,
    onNavigateToApiKeys: (String?) -> Unit,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isApiKeyMissing by viewModel.isApiKeyMissing.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current

    var query by rememberSaveable { mutableStateOf("") }
    var searchMode by rememberSaveable { mutableIntStateOf(MODE_QUICK) }
    val isLoading = uiState is ShodanUiState.Loading

    val submit = {
        if (query.isNotBlank() && !isLoading) {
            keyboardController?.hide()
            when (searchMode) {
                MODE_HOST -> viewModel.getHostInfo(query)
                MODE_SEARCH -> viewModel.search(query)
                else -> viewModel.getInternetDbInfo(query)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cloud Recon (Shodan)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = searchMode) {
                ModeTab("Quick", "Free / No Key", Icons.Default.Bolt, searchMode == MODE_QUICK, locked = false) {
                    searchMode = MODE_QUICK
                    viewModel.reset()
                }
                ModeTab("Host", "Key / Deep", Icons.Default.Dns, searchMode == MODE_HOST, locked = isApiKeyMissing) {
                    if (isApiKeyMissing) onNavigateToApiKeys("shodan") else {
                        searchMode = MODE_HOST
                        viewModel.reset()
                    }
                }
                ModeTab("Search", "Key / Query", Icons.Default.Search, searchMode == MODE_SEARCH, locked = isApiKeyMissing) {
                    if (isApiKeyMissing) onNavigateToApiKeys("shodan") else {
                        searchMode = MODE_SEARCH
                        viewModel.reset()
                    }
                }
            }

            if (isApiKeyMissing && searchMode != MODE_QUICK) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Shodan API key is missing. Configure it under Settings → API Keys.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { onNavigateToApiKeys("shodan") }) { Text("Set Key") }
                    }
                }
                return@Column
            }

            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = {
                            Text(
                                when (searchMode) {
                                    MODE_HOST -> "IP address or domain"
                                    MODE_SEARCH -> "Search query (e.g. apache country:DE)"
                                    else -> "IP address or domain"
                                }
                            )
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (searchMode == MODE_SEARCH) KeyboardType.Text else KeyboardType.Uri,
                            imeAction = ImeAction.Search,
                            autoCorrectEnabled = false
                        ),
                        keyboardActions = KeyboardActions(onSearch = { submit() })
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = submit, enabled = query.isNotBlank() && !isLoading) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                }
                if (searchMode == MODE_SEARCH) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ShodanAnalyzer.searchFilters.forEach { (label, filter) ->
                            SuggestionChip(
                                onClick = { query = (query.trimEnd() + " " + filter).trimStart() },
                                label = { Text(label) }
                            )
                        }
                    }
                } else {
                    ScopeWarning(target = query.trim())
                }
            }

            when (val state = uiState) {
                is ShodanUiState.Idle -> {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            when (searchMode) {
                                MODE_HOST -> "Full Shodan record for a public host: services, banners, software versions and associated CVEs."
                                MODE_SEARCH -> "Search Shodan's index. Filters need a paid plan, and each page of results uses a query credit."
                                else -> "Free InternetDB summary of a public host: open ports, hostnames and known CVEs. Private addresses are never sent."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is ShodanUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = WebGold)
                    }
                }
                is ShodanUiState.Error -> OsintNotice(state.message, Modifier.padding(16.dp), NoticeKind.ERROR)
                is ShodanUiState.HostSuccess -> HostReportView(state.report, state.recordedFindings)
                is ShodanUiState.SearchSuccess -> SearchResultView(state, onLoadMore = viewModel::loadMore) { ip ->
                    query = ip
                    searchMode = MODE_HOST
                    viewModel.getHostInfo(ip)
                }
                is ShodanUiState.InternetDbSuccess -> InternetDbView(state.data, state.recordedFindings)
            }
        }
    }
}

@Composable
private fun ModeTab(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    locked: Boolean,
    onClick: () -> Unit
) {
    val tint = if (locked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else Color.Unspecified
    Tab(
        selected = selected,
        onClick = onClick,
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = if (locked) tint else LocalContentColor.current)
                Text(title, style = MaterialTheme.typography.labelLarge, color = tint)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = tint)
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PortChips(ports: List<Int>) {
    val risky = remember(ports) { ShodanAnalyzer.exposures(ports).associateBy { it.port } }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ports.forEach { port ->
            val exposed = port in risky
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (exposed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = ShodanAnalyzer.serviceName(port)?.let { "$port $it" } ?: port.toString(),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (exposed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, color: Color = WebGold) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun ExposureCard(exposure: InternetExposure) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${exposure.service} (${exposure.port}/tcp)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                SeverityBadge(exposure.risk)
            }
            Text(exposure.note, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CveSection(vulns: List<String>) {
    SectionTitle("Associated CVEs (${vulns.size})", PhysicalRed)
    Text(
        "Inferred by Shodan from banners and version strings. Verify before reporting.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        vulns.take(60).forEach { cve ->
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)) {
                Text(
                    cve,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
    if (vulns.size > 60) {
        Text("+${vulns.size - 60} more (included in the copied summary)", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun InternetDbView(data: InternetDbResponse, recordedFindings: Int = 0) {
    val copy = rememberCopyAction()
    val exposures = remember(data) { ShodanAnalyzer.exposures(data.ports) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = WebGold.copy(alpha = 0.1f))
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(data.ip, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = WebGold)
                        Text(
                            "InternetDB · ${data.ports.size} open ports · ${data.vulns.size} CVEs",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { copy("Report", ShodanAnalyzer.summary(data)) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy report")
                    }
                }
            }
        }
        if (recordedFindings > 0) item { FindingsRecordedNotice(recordedFindings) }

        if (data.ports.isEmpty() && data.vulns.isEmpty()) {
            item { OsintNotice("Shodan has this address on record but lists no open ports or CVEs.") }
        }
        if (data.hostnames.isNotEmpty()) {
            item {
                SectionTitle("Hostnames")
                Text(data.hostnames.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (data.ports.isNotEmpty()) {
            item {
                SectionTitle("Open Ports")
                Spacer(modifier = Modifier.height(6.dp))
                PortChips(data.ports)
            }
        }
        if (exposures.isNotEmpty()) {
            item { SectionTitle("Risky Exposure", PhysicalRed) }
            items(exposures, key = { it.port }) { ExposureCard(it) }
        }
        if (data.vulns.isNotEmpty()) item { CveSection(data.vulns) }
        if (data.cpes.isNotEmpty()) {
            item {
                SectionTitle("Software (CPE)")
                Text(data.cpes.joinToString("\n"), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
        if (data.tags.isNotEmpty()) {
            item {
                SectionTitle("Tags")
                Text(data.tags.joinToString(", "), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SearchResultView(state: ShodanUiState.SearchSuccess, onLoadMore: () -> Unit, onOpenHost: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "${state.total} matches · showing ${state.matches.size}",
                style = MaterialTheme.typography.titleMedium,
                color = WebGold
            )
        }
        if (state.matches.isEmpty()) {
            item { OsintNotice("No hosts match \"${state.query}\".") }
        }
        items(state.matches) { match -> MatchCard(match, onOpenHost) }
        item {
            state.loadMoreError?.let { OsintNotice(it, kind = NoticeKind.ERROR) }
            if (state.hasMore) {
                OutlinedButton(onClick = onLoadMore, enabled = !state.isLoadingMore, modifier = Modifier.fillMaxWidth()) {
                    if (state.isLoadingMore) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Load page ${state.page + 1} (uses a query credit)")
                }
            }
        }
    }
}

@Composable
private fun MatchCard(match: ShodanMatch, onOpenHost: (String) -> Unit) {
    Card(
        onClick = { onOpenHost(match.ip) },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = match.ip,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(
                        text = "${match.port}/${match.transport ?: "tcp"}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            listOfNotNull(match.product, match.org, match.location, match.hostnames.take(3).joinToString(", ").ifBlank { null })
                .forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            match.banner?.takeIf { it.isNotBlank() }?.let { BannerBlock(it, maxLines = 5) }
        }
    }
}

@Composable
private fun BannerBlock(text: String, maxLines: Int) {
    Spacer(modifier = Modifier.height(8.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp)
    ) {
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun HostReportView(report: ShodanHostReport, recordedFindings: Int = 0) {
    val copy = rememberCopyAction()
    val exposures = remember(report) { ShodanAnalyzer.exposures(report.ports) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = report.ip,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        val faded = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        listOfNotNull(
                            listOfNotNull(report.org, report.asn).joinToString(" · ").ifBlank { null },
                            report.isp?.takeIf { it != report.org }?.let { "ISP: $it" },
                            listOfNotNull(report.city, report.country).joinToString(", ").ifBlank { null },
                            report.os?.let { "OS: $it" },
                            report.lastUpdate?.let { "Last seen by Shodan: ${it.take(10)}" }
                        ).forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = faded) }
                    }
                    IconButton(onClick = { copy("Report", ShodanAnalyzer.summary(report)) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy report", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }
        if (recordedFindings > 0) item { FindingsRecordedNotice(recordedFindings) }

        if (report.hostnames.isNotEmpty() || report.domains.isNotEmpty()) {
            item {
                SectionTitle("Hostnames")
                Text((report.hostnames + report.domains).distinct().joinToString(", "), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (report.tags.isNotEmpty()) {
            item {
                SectionTitle("Tags")
                Text(report.tags.joinToString(", "), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (report.ports.isNotEmpty()) {
            item {
                SectionTitle("Open Ports (${report.ports.size})")
                Spacer(modifier = Modifier.height(6.dp))
                PortChips(report.ports)
            }
        }
        if (exposures.isNotEmpty()) {
            item { SectionTitle("Risky Exposure", PhysicalRed) }
            items(exposures, key = { "exposure-${it.port}" }) { ExposureCard(it) }
        }
        if (report.vulns.isNotEmpty()) item { CveSection(report.vulns) }

        if (report.services.isNotEmpty()) {
            item { SectionTitle("Services (${report.services.size})") }
            items(report.services) { service -> ServiceCard(service) }
        }
    }
}

@Composable
private fun ServiceCard(service: ShodanService) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${service.port}/${service.transport ?: "tcp"}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = service.productLabel ?: ShodanAnalyzer.serviceName(service.port).orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                service.module?.let { module ->
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(module, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (service.vulns.isNotEmpty()) {
                Text(
                    "${service.vulns.size} CVEs: ${service.vulns.take(4).joinToString(", ")}${if (service.vulns.size > 4) ", …" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = PhysicalRed
                )
            }
            service.timestamp?.let {
                Text("Scanned ${it.take(10)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            service.banner?.takeIf { it.isNotBlank() }?.let { BannerBlock(it, maxLines = 10) }
        }
    }
}
