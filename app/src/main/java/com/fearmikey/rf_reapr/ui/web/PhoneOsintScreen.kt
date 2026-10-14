package com.fearmikey.rf_reapr.ui.web

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fearmikey.rf_reapr.domain.model.PhoneOsintLink
import com.fearmikey.rf_reapr.domain.model.PhoneOsintResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneOsintScreen(
    viewModel: PhoneOsintViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    var showCountryPicker by remember { mutableStateOf(false) }

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
            // Input Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Target Phone Number",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Country Code Selector Chip
                        OutlinedButton(
                            onClick = { showCountryPicker = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text(uiState.defaultCountryCode, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }

                        // Phone Input Field
                        OutlinedTextField(
                            value = uiState.inputPhone,
                            onValueChange = { viewModel.updateInputPhone(it) },
                            placeholder = { Text("e.g. +1 415 555 2671") },
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = {
                                    val clip = clipboardManager.getText()?.text
                                    if (!clip.isNullOrBlank()) {
                                        viewModel.updateInputPhone(clip)
                                    }
                                }) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste")
                                }
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    // Sample preset buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SuggestionChip(
                            onClick = { viewModel.loadSample("+14155552671") },
                            label = { Text("US Sample (+1)") }
                        )
                        SuggestionChip(
                            onClick = { viewModel.loadSample("+447911123456") },
                            label = { Text("UK Mobile (+44)") }
                        )
                        SuggestionChip(
                            onClick = { viewModel.loadSample("+4915123456789") },
                            label = { Text("DE Mobile (+49)") }
                        )
                        SuggestionChip(
                            onClick = { viewModel.loadSample("+61412345678") },
                            label = { Text("AU Mobile (+61)") }
                        )
                    }
                }
            }

            // Country Code Picker Dialog
            if (showCountryPicker) {
                AlertDialog(
                    onDismissRequest = { showCountryPicker = false },
                    title = { Text("Select Default Country Code") },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 350.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            PhoneOsintViewModel.ALL_COUNTRY_CODES.forEach { item ->
                                TextButton(
                                    onClick = {
                                        viewModel.setDefaultCountryCode(item.code)
                                        showCountryPicker = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("${item.name} (${item.iso})", modifier = Modifier.weight(1f))
                                        Text(item.code, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showCountryPicker = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Error display
            uiState.errorMessage?.let { errorMsg ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text(
                            text = errorMsg,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Result Cards
            uiState.result?.let { result ->
                PhoneDetailsCard(
                    result = result,
                    clipboardManager = clipboardManager,
                    onShowToast = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
                )

                PhoneOsintLinksCard(
                    links = result.osintLinks,
                    onLinkClick = { link -> openPhoneOsintLink(context, link) }
                )
            }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun PhoneDetailsCard(
    result: PhoneOsintResult,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    onShowToast: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = result.e164,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = result.formattedInternational,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = {
                    clipboardManager.setText(AnnotatedString(result.e164))
                    onShowToast("E.164 number copied to clipboard")
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            PhoneInfoRow("Country / Region", "${result.countryName} (${result.countryIso})")
            PhoneInfoRow("Calling Code", result.countryCallingCode)
            PhoneInfoRow("National Number", result.nationalNumber)
            PhoneInfoRow("Estimated Line Type", result.estimatedLineType)
            PhoneInfoRow("Valid E.164 Format", if (result.isValidFormat) "Yes (7-15 digits)" else "Questionable length")
        }
    }
}

@Composable
private fun PhoneInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun PhoneOsintLinksCard(
    links: List<PhoneOsintLink>,
    onLinkClick: (PhoneOsintLink) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "OSINT Shortcuts & Direct Actions",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "Launch direct WhatsApp chat without adding contact, query Telegram profiles, or lookup directories.",
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
                                else -> Icons.AutoMirrored.Filled.OpenInNew
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = link.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = link.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link.url))
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open action: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}
