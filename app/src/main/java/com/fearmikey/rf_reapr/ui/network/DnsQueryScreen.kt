package com.fearmikey.rf_reapr.ui.network

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fearmikey.rf_reapr.domain.repository.DnsResolver
import com.fearmikey.rf_reapr.domain.service.DnsInput
import com.fearmikey.rf_reapr.domain.service.DnsQueryResult
import com.fearmikey.rf_reapr.domain.service.EmailSecurityReport

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsQueryScreen(
    viewModel: DnsQueryViewModel,
    onBack: () -> Unit
) {
    val domain by viewModel.domain.collectAsStateWithLifecycle()
    val type by viewModel.type.collectAsStateWithLifecycle()
    val resolver by viewModel.resolver.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val emailSecurity by viewModel.emailSecurity.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()

    val keyboard = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val copy: (String) -> Unit = {
        clipboard.setText(AnnotatedString(it))
        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
    }
    val runQuery: () -> Unit = {
        viewModel.query()
        keyboard?.hide()
    }
    val domainValid = DnsInput.normalize(domain) != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DNS Query Tool") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (results.isNotEmpty()) {
                        IconButton(onClick = { copy(viewModel.resultsAsText()) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy all results")
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "input") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = domain,
                        onValueChange = viewModel::setDomain,
                        label = { Text("Domain or IP (for reverse lookup)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = domain.isNotBlank() && !domainValid,
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Search,
                            autoCorrectEnabled = false
                        ),
                        keyboardActions = KeyboardActions(onSearch = { if (domainValid) runQuery() }),
                        trailingIcon = {
                            if (domain.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setDomain("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        }
                    )
                    if (history.size > 1) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            history.drop(1).forEach { h ->
                                SuggestionChip(onClick = { viewModel.setDomain(h) }, label = { Text(h) })
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DnsQueryType.entries.forEach { option ->
                            FilterChip(
                                selected = type == option,
                                onClick = { viewModel.setType(option) },
                                label = { Text(option.label) }
                            )
                        }
                    }
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        DnsResolver.entries.forEachIndexed { index, option ->
                            SegmentedButton(
                                selected = resolver == option,
                                onClick = { viewModel.setResolver(option) },
                                shape = SegmentedButtonDefaults.itemShape(index, DnsResolver.entries.size)
                            ) { Text(option.label) }
                        }
                    }
                    Text(
                        text = if (resolver == DnsResolver.DEVICE) {
                            "Uses this device's resolver (works for local hostnames; A/AAAA only)."
                        } else {
                            "Sends the query to ${resolver.label} over HTTPS (DNS-over-HTTPS)."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = runQuery,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading && domainValid
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Query")
                        }
                    }
                }
            }

            error?.let { item(key = "error") { Text(it, color = MaterialTheme.colorScheme.error) } }

            emailSecurity?.let { report -> item(key = "email") { EmailSecurityCard(report) } }

            items(results, key = { "${it.query}_${it.type}" }) { result ->
                DnsResultCard(result = result, onCopy = copy)
            }
        }
    }
}

@Composable
private fun DnsResultCard(result: DnsQueryResult, onCopy: (String) -> Unit) {
    val ok = result.error == null && result.status == 0
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(result.typeName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(
                    result.statusName,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                if (result.authenticated) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.VerifiedUser, contentDescription = "DNSSEC validated", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Text(" DNSSEC", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.weight(1f))
                Text("${result.elapsedMs} ms", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(result.query, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
            result.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (result.answers.isEmpty() && result.error == null) {
                Text(
                    if (result.status == 3) "Domain does not exist" else "No records of this type",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                result.authority.firstOrNull()?.let {
                    Text("Authority: ${it.data}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            }
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    result.answers.forEach { record ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClickLabel = "Copy record") { onCopy(record.data) },
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                record.typeName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(52.dp).padding(top = 2.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(record.data, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                                if (record.name != result.query || record.ttl > 0) {
                                    Text(
                                        buildString {
                                            if (record.name != result.query) append(record.name).append(" · ")
                                            if (record.ttl > 0) append("TTL ").append(DnsInput.formatTtl(record.ttl))
                                        }.trimEnd(' ', '·'),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmailSecurityCard(report: EmailSecurityReport) {
    val healthy = report.findings.isEmpty()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (healthy) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (healthy) Icons.Default.VerifiedUser else Icons.Default.Warning, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Email spoofing protection", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Text("SPF: ${report.spfAll ?: if (report.spfRecord == null) "missing" else "present"}", style = MaterialTheme.typography.bodySmall)
            Text("DMARC: ${report.dmarcPolicy?.let { "p=$it" } ?: if (report.dmarcRecord == null) "missing" else "present"}", style = MaterialTheme.typography.bodySmall)
            if (healthy) {
                Text("SPF hard-fails and DMARC is enforced.", style = MaterialTheme.typography.bodySmall)
            } else {
                report.findings.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
