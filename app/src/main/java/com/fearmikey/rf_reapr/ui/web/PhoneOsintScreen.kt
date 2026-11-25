package com.fearmikey.rf_reapr.ui.web

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fearmikey.rf_reapr.domain.model.PhoneOsintLink
import com.fearmikey.rf_reapr.domain.model.PhoneOsintResult
import com.fearmikey.rf_reapr.domain.model.PhoneValidity
import com.fearmikey.rf_reapr.domain.service.PhoneCountry
import com.fearmikey.rf_reapr.domain.service.PhoneNumberAnalyzer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneOsintScreen(
    viewModel: PhoneOsintViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val copy = rememberCopyAction()
    var showCountryPicker by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Phone Number OSINT") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    viewModel.report()?.let { report ->
                        IconButton(onClick = { copy("Report", report) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy report")
                        }
                    }
                    if (uiState.inputPhone.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clear() }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { showCountryPicker = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text(uiState.defaultCountryCode, fontWeight = FontWeight.Bold)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Change default country")
                        }
                        OutlinedTextField(
                            value = uiState.inputPhone,
                            onValueChange = { viewModel.updateInputPhone(it) },
                            label = { Text("Phone number") },
                            placeholder = { Text("+1 415 555 2671") },
                            trailingIcon = {
                                IconButton(onClick = {
                                    clipboardManager.getText()?.text?.takeIf { it.isNotBlank() }?.let { viewModel.updateInputPhone(it.take(64)) }
                                }) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste")
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    Text(
                        "Parsed on the device. The country on the left is only used for numbers written without \"+\" or \"00\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (showCountryPicker) {
                CountryPickerDialog(
                    countries = viewModel.countries,
                    onSelect = {
                        viewModel.setDefaultCountryCode(it.dialCode)
                        showCountryPicker = false
                    },
                    onDismiss = { showCountryPicker = false }
                )
            }

            uiState.errorMessage?.let { OsintNotice(it, kind = NoticeKind.ERROR) }

            uiState.result?.let { result ->
                PhoneDetailsCard(result = result, onCopy = { copy("Number", result.e164) })
                SearchFormatsCard(result = result, onCopy = copy)
                PhoneOsintLinksCard(links = result.osintLinks, onLinkClick = { link -> openPhoneOsintLink(context, link) })
            }
        }
    }
}

@Composable
private fun CountryPickerDialog(countries: List<PhoneCountry>, onSelect: (PhoneCountry) -> Unit, onDismiss: () -> Unit) {
    var filter by remember { mutableStateOf("") }
    val shown = remember(filter, countries) {
        val needle = filter.trim().removePrefix("+")
        if (needle.isEmpty()) countries
        else countries.filter { it.name.contains(needle, true) || it.callingCode.startsWith(needle) || it.iso.equals(needle, true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Default country") },
        text = {
            Column {
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    label = { Text("Search name or code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).padding(top = 8.dp)) {
                    items(shown, key = { it.callingCode }) { country ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onSelect(country) }.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(country.name, modifier = Modifier.weight(1f))
                            Text(country.dialCode, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PhoneDetailsCard(result: PhoneOsintResult, onCopy: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = result.e164,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = result.formattedInternational + (result.extension?.let { " ext. $it" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onCopy) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy E.164 number")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            PhoneInfoRow("Country / region", "${result.countryName} (${result.countryIso})")
            result.region?.let { PhoneInfoRow("Numbering area", it) }
            PhoneInfoRow("Calling code", result.countryCallingCode)
            PhoneInfoRow("National number", result.nationalNumber)
            PhoneInfoRow("Line type (estimate)", result.estimatedLineType)
            Text(
                result.validity.label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = when (result.validity) {
                    PhoneValidity.VALID_LENGTH -> MaterialTheme.colorScheme.primary
                    PhoneValidity.POSSIBLE -> MaterialTheme.colorScheme.onSurfaceVariant
                    PhoneValidity.UNUSUAL_LENGTH, PhoneValidity.INVALID -> MaterialTheme.colorScheme.error
                }
            )
            result.notes.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            Text(
                "Country and line type come from the numbering plan. Ported numbers keep their prefix, so the current " +
                    "carrier and location can differ.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PhoneInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

@Composable
private fun SearchFormatsCard(result: PhoneOsintResult, onCopy: (String, String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Search Formats", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "People publish the same number in different spellings. Searching all of them finds mentions a single format misses.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            result.searchVariants.forEach { variant ->
                Text(variant, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
            }
            TextButton(
                onClick = { onCopy("Search query", PhoneNumberAnalyzer.exactMatchQuery(result.searchVariants)) },
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy combined query")
            }
        }
    }
}

@Composable
private fun PhoneOsintLinksCard(links: List<PhoneOsintLink>, onLinkClick: (PhoneOsintLink) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Lookups", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Each entry opens a third-party site or app with the number filled in. Nothing is sent until you tap one.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            links.forEach { link ->
                Surface(
                    onClick = { onLinkClick(link) },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = when {
                                link.url.startsWith("tel:") -> Icons.Default.Call
                                link.url.startsWith("sms:") -> Icons.AutoMirrored.Filled.Chat
                                link.url.contains("wa.me") -> Icons.Default.ChatBubble
                                link.url.contains("t.me") -> Icons.AutoMirrored.Filled.Send
                                link.url.contains("search") || link.url.contains("duckduckgo") -> Icons.Default.Search
                                else -> Icons.AutoMirrored.Filled.OpenInNew
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = link.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(text = link.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun openPhoneOsintLink(context: Context, link: PhoneOsintLink) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, link.url.toUri()))
    } catch (_: Exception) {
        Toast.makeText(context, "No app can open this link", Toast.LENGTH_SHORT).show()
    }
}
