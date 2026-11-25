package com.fearmikey.rf_reapr.ui.web

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fearmikey.rf_reapr.domain.model.ThreatIntelReport
import com.fearmikey.rf_reapr.domain.model.ThreatIntelResult
import com.fearmikey.rf_reapr.domain.repository.ThreatIntelStatus
import com.fearmikey.rf_reapr.domain.service.ThreatAssessment
import com.fearmikey.rf_reapr.domain.service.ThreatIntelAnalyzer
import com.fearmikey.rf_reapr.domain.service.ThreatLevel
import com.fearmikey.rf_reapr.ui.components.ScopeWarning
import com.fearmikey.rf_reapr.ui.theme.NetworkGreen
import com.fearmikey.rf_reapr.ui.theme.WebGold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreatIntelScreen(
    viewModel: ThreatIntelViewModel,
    onNavigateToApiKeys: () -> Unit,
    onBack: () -> Unit
) {
    val ipAddress by viewModel.ipAddress.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val apiKey by viewModel.abuseIpDbApiKey.collectAsStateWithLifecycle()
    val assessment by viewModel.assessment.collectAsStateWithLifecycle()
    val recorded by viewModel.recordedFindings.collectAsStateWithLifecycle()
    val inputProblem by viewModel.inputProblem.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current
    val canSearch = ipAddress.isNotBlank() && inputProblem == null && apiKey.isNotBlank() && status !is ThreatIntelStatus.Loading

    val submit = {
        if (canSearch) {
            keyboardController?.hide()
            viewModel.checkThreat()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Threat Intel (AbuseIPDB)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToApiKeys) {
                        Icon(
                            Icons.Default.Key,
                            contentDescription = "Configure API Key",
                            tint = if (apiKey.isBlank()) MaterialTheme.colorScheme.error else WebGold
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            if (apiKey.isBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "AbuseIPDB API key missing",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                "A free AbuseIPDB API key is required to query threat scores.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        TextButton(onClick = onNavigateToApiKeys) {
                            Text("Set Key", color = WebGold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            OutlinedTextField(
                value = ipAddress,
                onValueChange = { viewModel.onIpAddressChanged(it) },
                label = { Text("IP Address") },
                placeholder = { Text("e.g. 1.1.1.1 or 2606:4700:4700::1111") },
                leadingIcon = { Icon(Icons.Default.Security, contentDescription = null, tint = WebGold) },
                trailingIcon = {
                    IconButton(onClick = submit, enabled = canSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Check Threat", tint = if (canSearch) WebGold else Color.Gray)
                    }
                },
                isError = inputProblem != null,
                supportingText = {
                    Text(inputProblem ?: "Sends this address to AbuseIPDB when you tap search.")
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search, autoCorrectEnabled = false
                ),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
                modifier = Modifier.fillMaxWidth()
            )
            ScopeWarning(target = ipAddress.trim())

            Spacer(modifier = Modifier.height(12.dp))

            when (val current = status) {
                is ThreatIntelStatus.Idle -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Look up the reputation and abuse reports of a public IPv4 or IPv6 address.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is ThreatIntelStatus.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = WebGold)
                    }
                }
                is ThreatIntelStatus.Error -> OsintNotice(current.message, kind = NoticeKind.ERROR)
                is ThreatIntelStatus.Success -> {
                    ThreatIntelResultView(result = current.result, assessment = assessment, recordedFindings = recorded)
                }
            }
        }
    }
}

@Composable
private fun levelColor(level: ThreatLevel): Color = when (level) {
    ThreatLevel.MALICIOUS -> MaterialTheme.colorScheme.error
    ThreatLevel.SUSPICIOUS -> Color(0xFFFF9800)
    ThreatLevel.LOW -> WebGold
    ThreatLevel.CLEAN, ThreatLevel.WHITELISTED -> NetworkGreen
}

@Composable
fun ThreatIntelResultView(result: ThreatIntelResult, assessment: ThreatAssessment?, recordedFindings: Int = 0) {
    val level = assessment?.level ?: ThreatIntelAnalyzer.level(result)
    val scoreColor = levelColor(level)
    val copy = rememberCopyAction()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = result.ipAddress,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${result.isp ?: "Unknown ISP"} • ${result.countryName ?: result.countryCode ?: "Unknown country"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .background(scoreColor.copy(alpha = 0.2f), shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${result.abuseConfidenceScore}%",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = scoreColor
                                )
                                Text(text = "Abuse", fontSize = 10.sp, color = scoreColor)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(level.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = scoreColor)
                    assessment?.let {
                        Text(it.headline, style = MaterialTheme.typography.bodySmall)
                        it.notes.forEach { note ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("• $note", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {
                            copy(
                                "Summary",
                                "${result.ipAddress}: AbuseIPDB ${result.abuseConfidenceScore}% (${level.label}), " +
                                    "${result.totalReports} reports from ${result.numDistinctUsers} reporters, " +
                                    "${result.isp ?: "unknown ISP"}, ${result.countryName ?: result.countryCode ?: "unknown country"}"
                            )
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy summary")
                        }
                    }
                }
            }
        }

        if (recordedFindings > 0) {
            item { FindingsRecordedNotice(recordedFindings) }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Host Information", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    ThreatDetailRow("Domain", result.domain ?: "N/A")
                    ThreatDetailRow("Usage type", result.usageType ?: "N/A")
                    ThreatDetailRow("IP version", "IPv${result.ipVersion}")
                    ThreatDetailRow("Whitelisted", if (result.isWhitelisted) "Yes" else "No")
                    ThreatDetailRow("Tor exit node", if (result.isTor) "Yes" else "No")
                    ThreatDetailRow("Reports (90 days)", "${result.totalReports} from ${result.numDistinctUsers} reporters")
                    assessment?.let {
                        ThreatDetailRow("Recent activity", "${it.reportsLast7Days} in 7 days, ${it.reportsLast30Days} in 30 days")
                    }
                    result.lastReportedAt?.let { ThreatDetailRow("Last reported", it.take(10)) }
                    if (result.hostnames.isNotEmpty()) {
                        ThreatDetailRow("Hostnames", result.hostnames.joinToString(", "))
                    }
                }
            }
        }

        assessment?.topCategories?.takeIf { it.isNotEmpty() }?.let { categories ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Reported For", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        val max = categories.first().second.coerceAtLeast(1)
                        categories.take(8).forEach { (name, count) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                LinearProgressIndicator(
                                    progress = { count.toFloat() / max },
                                    modifier = Modifier.weight(1f).height(6.dp),
                                    color = scoreColor
                                )
                                Text(
                                    count.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.width(36.dp).padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (result.reports.isNotEmpty()) {
            item {
                Text(
                    text = "Recent Abuse Reports (${result.reports.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            items(result.reports) { report ->
                AbuseReportCard(report = report)
            }
        }
    }
}

@Composable
private fun ThreatDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
fun AbuseReportCard(report: ThreatIntelReport) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = report.reportedAt.take(10),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (report.reporterCountryCode != null) {
                    Text(
                        text = "Reporter: ${report.reporterCountryCode}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val categories = ThreatIntelAnalyzer.categoryNames(report)
            if (categories.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = categories.joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = WebGold
                )
            }

            if (report.comment.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = report.comment.trim().take(400),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
        }
    }
}
