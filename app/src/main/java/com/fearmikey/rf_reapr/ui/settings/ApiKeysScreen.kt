package com.fearmikey.rf_reapr.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fearmikey.rf_reapr.domain.model.ThemePreference
import com.fearmikey.rf_reapr.ui.theme.WebGold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeysScreen(
    viewModel: SettingsViewModel,
    highlightKey: String? = null,
    onBack: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val currentTheme by viewModel.themePreference.collectAsStateWithLifecycle()
    val apiKey by viewModel.vulnerabilityApiKey.collectAsStateWithLifecycle()
    val hibpApiKey by viewModel.hibpApiKey.collectAsStateWithLifecycle()
    val shodanApiKey by viewModel.shodanApiKey.collectAsStateWithLifecycle()
    
    var editedApiKey by remember(apiKey) { mutableStateOf(apiKey) }
    var editedHibpApiKey by remember(hibpApiKey) { mutableStateOf(hibpApiKey) }
    var editedShodanApiKey by remember(shodanApiKey) { mutableStateOf(shodanApiKey) }
    var showApiKey by remember { mutableStateOf(false) }
    var showHibpApiKey by remember { mutableStateOf(false) }
    var showShodanApiKey by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()

    var flashTrigger by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.exportStatus.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }
    
    // Auto-scroll to the requested section if highlightKey is provided and trigger flash
    LaunchedEffect(highlightKey) {
        if (highlightKey != null) {
            // Give layout a moment to render before scrolling
            kotlinx.coroutines.delay(300)
            // Estimated scroll positions (can be tuned or measured dynamically)
            val scrollTarget = when(highlightKey) {
                "hibp" -> 150
                "shodan" -> 350
                else -> 0
            }
            if (scrollTarget > 0) {
                 scrollState.animateScrollTo(scrollTarget)
            }
            
            // Trigger a double flash
            repeat(2) {
                flashTrigger = true
                kotlinx.coroutines.delay(400)
                flashTrigger = false
                kotlinx.coroutines.delay(400)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("3rd Party Service API Keys") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val highlightAlpha by animateFloatAsState(
            targetValue = if (flashTrigger) 0.6f else 0f,
            animationSpec = tween(300),
            label = "highlightAlpha"
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
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

            val hibpHighlightColor = if (highlightKey == "hibp") MaterialTheme.colorScheme.primaryContainer.copy(alpha = highlightAlpha) else Color.Transparent
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
                    .background(hibpHighlightColor, shape = MaterialTheme.shapes.small)
                    .padding(if (highlightKey == "hibp") 8.dp else 0.dp)
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

            Spacer(modifier = Modifier.height(16.dp))

            val shodanFontSize = when {
                editedShodanApiKey.length > 35 -> 11.sp
                editedShodanApiKey.length > 25 -> 13.sp
                else -> 16.sp
            }

            val shodanHighlightColor = if (highlightKey == "shodan") MaterialTheme.colorScheme.primaryContainer.copy(alpha = highlightAlpha) else Color.Transparent
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
                    .background(shodanHighlightColor, shape = MaterialTheme.shapes.small)
                    .padding(if (highlightKey == "shodan") 8.dp else 0.dp)
            ) {
                OutlinedTextField(
                    value = editedShodanApiKey,
                    onValueChange = { editedShodanApiKey = it },
                    label = { Text("Shodan API Key") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = shodanFontSize),
                    visualTransformation = if (showShodanApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showShodanApiKey = !showShodanApiKey }) {
                            Icon(
                                imageVector = if (showShodanApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showShodanApiKey) "Hide Shodan API Key" else "Show Shodan API Key"
                            )
                        }
                    },
                    supportingText = {
                        Text("Required for finding exposed IoT devices and WAN open ports.")
                    }
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = { viewModel.setShodanApiKey(editedShodanApiKey) },
                    enabled = editedShodanApiKey != shodanApiKey,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Text("Apply")
                }
            }

            TextButton(
                onClick = { uriHandler.openUri("https://account.shodan.io/") },
                modifier = Modifier.align(Alignment.End),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (darkTheme) WebGold else Color(0xFF8B6B00)
                )
            ) {
                Text(
                    text = "Get an API Key from Shodan",
                    textDecoration = TextDecoration.Underline
                )
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
