package com.fearmikey.rf_reapr.ui.web

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.fearmikey.rf_reapr.domain.model.JwtInspectionResult
import com.fearmikey.rf_reapr.domain.model.PayloadFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayloadEncoderScreen(
    viewModel: PayloadEncoderViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Payload Encoder & JWT") },
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
            PrimaryTabRow(selectedTabIndex = uiState.selectedTab) {
                Tab(
                    selected = uiState.selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    text = { Text("Encoder / Decoder") },
                    icon = { Icon(Icons.Default.Code, contentDescription = null) }
                )
                Tab(
                    selected = uiState.selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    text = { Text("JWT Inspector") },
                    icon = { Icon(Icons.Default.VpnKey, contentDescription = null) }
                )
            }

            if (uiState.selectedTab == 0) {
                EncoderDecoderContent(
                    uiState = uiState,
                    viewModel = viewModel,
                    clipboardManager = clipboardManager,
                    onShowToast = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
                )
            } else {
                JwtInspectorContent(
                    uiState = uiState,
                    viewModel = viewModel,
                    clipboardManager = clipboardManager,
                    onShowToast = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
                )
            }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun EncoderDecoderContent(
    uiState: PayloadEncoderUiState,
    viewModel: PayloadEncoderViewModel,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    onShowToast: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Format selector chips
        Text("Conversion Format", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PayloadFormat.entries.forEach { format ->
                FilterChip(
                    selected = uiState.format == format,
                    onClick = { viewModel.setFormat(format) },
                    label = { Text(format.displayName) }
                )
            }
        }

        // Mode selector (Encode vs Decode)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = uiState.mode == ConverterMode.ENCODE,
                onClick = { viewModel.setMode(ConverterMode.ENCODE) },
                label = { Text("Encode") },
                leadingIcon = {
                    if (uiState.mode == ConverterMode.ENCODE) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = uiState.mode == ConverterMode.DECODE,
                onClick = { viewModel.setMode(ConverterMode.DECODE) },
                label = { Text("Decode") },
                leadingIcon = {
                    if (uiState.mode == ConverterMode.DECODE) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }

        // Input card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Input (${if (uiState.mode == ConverterMode.ENCODE) "Plaintext" else uiState.format.displayName})", style = MaterialTheme.typography.titleSmall)
                    Row {
                        IconButton(onClick = {
                            val clip = clipboardManager.getText()?.text
                            if (!clip.isNullOrBlank()) {
                                viewModel.setInputText(clip)
                            }
                        }) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Paste")
                        }
                        if (uiState.inputText.isNotEmpty()) {
                            IconButton(onClick = { viewModel.clearInput() }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = uiState.inputText,
                    onValueChange = { viewModel.setInputText(it) },
                    placeholder = { Text("Enter text to convert...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 8,
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
                )
            }
        }

        // Swap button
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            FilledTonalButton(onClick = { viewModel.swapInputOutput() }) {
                Icon(Icons.Default.SwapVert, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Swap Input / Output")
            }
        }

        // Output card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Output (${if (uiState.mode == ConverterMode.ENCODE) uiState.format.displayName else "Plaintext"})", style = MaterialTheme.typography.titleSmall)
                    if (uiState.outputText.isNotEmpty()) {
                        IconButton(onClick = {
                            clipboardManager.setText(AnnotatedString(uiState.outputText))
                            onShowToast("Output copied to clipboard")
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                        }
                    }
                }

                if (uiState.conversionError != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text(uiState.conversionError, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = uiState.outputText,
                        onValueChange = {},
                        readOnly = true,
                        placeholder = { Text("Result will appear here...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        maxLines = 8,
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
                    )
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun JwtInspectorContent(
    uiState: PayloadEncoderUiState,
    viewModel: PayloadEncoderViewModel,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    onShowToast: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Token input
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Encoded JWT Token", style = MaterialTheme.typography.titleSmall)
                    Row {
                        IconButton(onClick = {
                            val clip = clipboardManager.getText()?.text
                            if (!clip.isNullOrBlank()) {
                                viewModel.setJwtInput(clip)
                            }
                        }) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Paste")
                        }
                        if (uiState.jwtInput.isNotEmpty()) {
                            IconButton(onClick = { viewModel.clearJwt() }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = uiState.jwtInput,
                    onValueChange = { viewModel.setJwtInput(it) },
                    placeholder = { Text("Paste raw JWT token (eyJhbGciOi...)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        // Sample JWT token for testing/demo
                        val sampleJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkFsaWNlIFNlY3VyaXR5IiwiaWF0IjoxNTE2MjM5MDIyLCJleHAiOjE5MTYyMzkwMjIsImlzcyI6Imh0dHBzOi8vcmYtcmVhcHIubG9jYWwifQ.4pzaw4xkwB-y73eB3770sL4hC67FvO6_Jv82fL6a_8g"
                        viewModel.setJwtInput(sampleJwt)
                    }) {
                        Text("Load Sample JWT")
                    }
                }
            }
        }

        uiState.jwtResult?.let { result ->
            if (!result.isValidJwt) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text(
                            text = result.errorMessage ?: "Invalid JWT Token",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                JwtDetailsView(
                    result = result,
                    clipboardManager = clipboardManager,
                    onShowToast = onShowToast
                )
            }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun JwtDetailsView(
    result: JwtInspectionResult,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    onShowToast: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Expiration & Algorithm Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = when (result.isExpired) {
                    true -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                    false -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    null -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Status: ${result.expirationRelative ?: "No Expiration Set"}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = when (result.isExpired) {
                            true -> MaterialTheme.colorScheme.error
                            false -> MaterialTheme.colorScheme.primary
                            null -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    SuggestionChip(
                        onClick = {},
                        label = { Text("Alg: ${result.algorithm ?: "None"}") }
                    )
                }

                result.type?.let { typ ->
                    Text("Token Type: $typ", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Standard Claims Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Standard Claims", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                HorizontalDivider()

                ClaimRow("Issuer (iss)", result.issuer)
                ClaimRow("Subject (sub)", result.subject)
                ClaimRow("Audience (aud)", result.audience)
                ClaimRow("Issued At (iat)", result.issuedAtFormatted)
                ClaimRow("Expires At (exp)", result.expiresAtFormatted)
                ClaimRow("Not Before (nbf)", result.notBeforeFormatted)
                ClaimRow("JWT ID (jti)", result.jwtId)

                if (result.customClaims.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Custom Claims", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    result.customClaims.forEach { (k, v) ->
                        ClaimRow(k, v)
                    }
                }
            }
        }

        // Decoded Header
        JsonBlockCard(
            title = "Header (Algorithm & Token Type)",
            json = result.headerJson,
            clipboardManager = clipboardManager,
            onShowToast = onShowToast
        )

        // Decoded Payload
        JsonBlockCard(
            title = "Payload (Data & Claims)",
            json = result.payloadJson,
            clipboardManager = clipboardManager,
            onShowToast = onShowToast
        )

        // Signature Info
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Signature", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (result.signatureHex.isNotEmpty() && result.signatureHex != "None / Unsigned") {
                        IconButton(onClick = {
                            clipboardManager.setText(AnnotatedString(result.signatureHex))
                            onShowToast("Signature hex copied to clipboard")
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                        }
                    }
                }
                Text(
                    text = result.signatureHex,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun ClaimRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Suppress("DEPRECATION")
@Composable
private fun JsonBlockCard(
    title: String,
    json: String,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    onShowToast: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                IconButton(onClick = {
                    clipboardManager.setText(AnnotatedString(json))
                    onShowToast("Copied to clipboard")
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                }
            }
            OutlinedTextField(
                value = json,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = MaterialTheme.typography.bodySmall.fontSize)
            )
        }
    }
}
