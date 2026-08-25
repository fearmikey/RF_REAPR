package com.fearmikey.rf_reapr.ui.web

import android.text.Html
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fearmikey.rf_reapr.domain.model.Breach
import com.fearmikey.rf_reapr.domain.model.Paste

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HibpScreen(
    viewModel: HibpViewModel,
    onBack: () -> Unit,
) {
    var account by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isApiKeyMissing by viewModel.isApiKeyMissing.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HaveIBeenPwned Checker") },
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
            OutlinedTextField(
                value = account,
                onValueChange = { account = it },
                label = { Text("Email or Account Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("example@email.com") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    autoCorrect = false
                ),
                trailingIcon = {
                    IconButton(
                        onClick = { viewModel.checkAccount(account) },
                        enabled = account.isNotBlank() && (uiState !is HibpUiState.Loading)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                },
                supportingText = {
                    if (isApiKeyMissing) {
                        Text(
                            "Note: Search requires a HaveIBeenPwned API key (active subscription required). Set this in Settings.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                isError = isApiKeyMissing && account.isNotBlank()
            )

            Spacer(modifier = Modifier.height(24.dp))

            when (val state = uiState) {
                is HibpUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is HibpUiState.Success -> {
                    ResultsContent(state.breaches, state.pastes)
                }
                is HibpUiState.NoResults -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Good news — no pwnage found!", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                is HibpUiState.Error -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Dangerous, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(state.message, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
                is HibpUiState.Idle -> {
                    Text(
                        "Check if an email address or phone number is in a data breach.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ResultsContent(breaches: List<Breach>, pastes: List<Paste>) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (breaches.isNotEmpty()) {
            item {
                Text(
                    "Found in ${breaches.size} Breaches",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            items(breaches) { breach ->
                BreachItem(breach)
            }
        }

        if (pastes.isNotEmpty()) {
            item {
                Text(
                    "Found in ${pastes.size} Pastes",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            items(pastes) { paste ->
                PasteItem(paste)
            }
        }
    }
}

@Composable
fun BreachItem(breach: Breach) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(breach.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(breach.breachDate, style = MaterialTheme.typography.labelSmall)
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Note: In a real app we'd use a proper HTML to AnnotatedString converter
            val cleanDescription = Html.fromHtml(breach.description, Html.FROM_HTML_MODE_COMPACT).toString()
            Text(
                text = cleanDescription,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 4
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                "Compromised data: ${breach.dataClasses.joinToString(", ")}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
fun PasteItem(paste: Paste) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        ListItem(
            headlineContent = { Text(paste.title ?: "Untitled Paste") },
            supportingContent = { Text("Source: ${paste.source} • Emails found: ${paste.emailCount}") },
            leadingContent = { Icon(Icons.Default.Info, contentDescription = null) }
        )
    }
}
