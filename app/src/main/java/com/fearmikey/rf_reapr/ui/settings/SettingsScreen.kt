package com.fearmikey.rf_reapr.ui.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.fearmikey.rf_reapr.BuildConfig
import com.fearmikey.rf_reapr.domain.model.ThemePreference
import com.fearmikey.rf_reapr.ui.theme.WebGold

@Preview(showBackground = true)
@Composable
fun SettingsPreview() {
    // We can't easily mock the ViewModel here without a proper factory or interface
    // but for preview purposes we can show the layout with a dummy UI
    MaterialTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Settings Preview", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {}) {
                Icon(Icons.Default.BugReport, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Export Debug Logs")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val currentTheme by viewModel.themePreference.collectAsState()
    val apiKey by viewModel.vulnerabilityApiKey.collectAsState()
    val hibpApiKey by viewModel.hibpApiKey.collectAsState()
    val cameraShortcutEnabled by viewModel.isCameraShortcutEnabled.collectAsState()
    
    var editedApiKey by remember(apiKey) { mutableStateOf(apiKey) }
    var editedHibpApiKey by remember(hibpApiKey) { mutableStateOf(hibpApiKey) }
    var expanded by remember { mutableStateOf(false) }
    var showApiKey by remember { mutableStateOf(false) }
    var showHibpApiKey by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.exportStatus.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                .padding(16.dp)
        ) {
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = when (currentTheme) {
                        ThemePreference.LIGHT -> "Light"
                        ThemePreference.DARK -> "Dark"
                        ThemePreference.SYSTEM -> "Match Device"
                    },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Theme") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Light") },
                        onClick = {
                            viewModel.setThemePreference(ThemePreference.LIGHT)
                            expanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Dark") },
                        onClick = {
                            viewModel.setThemePreference(ThemePreference.DARK)
                            expanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Match Device") },
                        onClick = {
                            viewModel.setThemePreference(ThemePreference.SYSTEM)
                            expanded = false
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Camera Shortcut",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Open camera by double-pressing the power button (even when locked).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = cameraShortcutEnabled,
                    onCheckedChange = { viewModel.setCameraShortcutEnabled(it) }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Vulnerability Auditing",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            val fontSize = when {
                editedApiKey.length > 35 -> 11.sp
                editedApiKey.length > 25 -> 13.sp
                else -> 16.sp
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = editedApiKey,
                    onValueChange = { editedApiKey = it },
                    label = { Text("NVD API Key (Optional)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = fontSize),
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showApiKey) "Hide API Key" else "Show API Key"
                            )
                        }
                    },
                    supportingText = {
                        Text("Optional key used to increase rate limits for global CVE synchronization.")
                    }
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = { viewModel.setVulnerabilityApiKey(editedApiKey) },
                    enabled = editedApiKey != apiKey,
                    modifier = Modifier.padding(bottom = 16.dp) // Align with TextField body, above supporting text
                ) {
                    Text("Apply")
                }
            }

            val darkTheme = when (currentTheme) {
                ThemePreference.LIGHT -> false
                ThemePreference.DARK -> true
                ThemePreference.SYSTEM -> isSystemInDarkTheme()
            }

            TextButton(
                onClick = { uriHandler.openUri("https://nvd.nist.gov/developers/request-an-api-key") },
                modifier = Modifier.align(Alignment.End),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (darkTheme) WebGold else Color(0xFF8B6B00)
                )
            ) {
                Text(
                    text = "Get an API Key from NIST",
                    textDecoration = TextDecoration.Underline
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            val hibpFontSize = when {
                editedHibpApiKey.length > 35 -> 11.sp
                editedHibpApiKey.length > 25 -> 13.sp
                else -> 16.sp
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = editedHibpApiKey,
                    onValueChange = { editedHibpApiKey = it },
                    label = { Text("HaveIBeenPwned API Key") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = hibpFontSize),
                    visualTransformation = if (showHibpApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showHibpApiKey = !showHibpApiKey }) {
                            Icon(
                                imageVector = if (showHibpApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showHibpApiKey) "Hide HaveIBeenPwned API Key" else "Show HaveIBeenPwned API Key"
                            )
                        }
                    },
                    supportingText = {
                        Text("Required for HaveIBeenPwned breach checks during OSINT audits.")
                    }
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = { viewModel.setHibpApiKey(editedHibpApiKey) },
                    enabled = editedHibpApiKey != hibpApiKey,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Text("Apply")
                }
            }

            TextButton(
                onClick = { uriHandler.openUri("https://haveibeenpwned.com/API/Key") },
                modifier = Modifier.align(Alignment.End),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (darkTheme) WebGold else Color(0xFF8B6B00)
                )
            ) {
                Text(
                    text = "Get an API Key from HaveIBeenPwned",
                    textDecoration = TextDecoration.Underline
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Text(
                text = "RF_REAPR",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = "RF - Recon, Evaluation, Analysis, and Penetration Reporting",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(vertical = 4.dp),
                maxLines = 1
            )

            Text(
                text = "Version Number: ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary
            )

            TextButton(
                onClick = { uriHandler.openUri("https://github.com/fearmikey/RF_REAPR") },
                modifier = Modifier.padding(top = 8.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (darkTheme) WebGold else Color(0xFF8B6B00)
                ),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    text = "View on GitHub",
                    textDecoration = TextDecoration.Underline,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
