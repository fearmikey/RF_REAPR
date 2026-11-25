package com.fearmikey.rf_reapr.ui.web

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fearmikey.rf_reapr.domain.model.Breach
import com.fearmikey.rf_reapr.domain.model.Paste
import com.fearmikey.rf_reapr.domain.model.RiskLevel
import com.fearmikey.rf_reapr.domain.service.BreachAnalyzer
import com.fearmikey.rf_reapr.domain.service.PwnedPasswords
import com.fearmikey.rf_reapr.ui.scanner.SeverityBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HibpScreen(
    viewModel: HibpViewModel,
    onBack: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

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
        ) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0, onClick = { tab = 0 },
                    text = { Text("Account") }, icon = { Icon(Icons.Default.Person, contentDescription = null) }
                )
                Tab(
                    selected = tab == 1, onClick = { tab = 1 },
                    text = { Text("Password") }, icon = { Icon(Icons.Default.Password, contentDescription = null) }
                )
            }
            if (tab == 0) AccountTab(viewModel) else PasswordTab(viewModel)
        }
    }
}

@Composable
private fun AccountTab(viewModel: HibpViewModel) {
    var account by rememberSaveable { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isApiKeyMissing by viewModel.isApiKeyMissing.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current
    val canSearch = account.isNotBlank() && !isApiKeyMissing && uiState !is HibpUiState.Loading
    val submit = {
        if (canSearch) {
            keyboardController?.hide()
            viewModel.checkAccount(account)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = account,
            onValueChange = { account = it },
            label = { Text("Email address") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("name@example.com") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email, imeAction = ImeAction.Search, autoCorrectEnabled = false
            ),
            keyboardActions = KeyboardActions(onSearch = { submit() }),
            trailingIcon = {
                IconButton(onClick = submit, enabled = canSearch) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
            },
            supportingText = {
                if (isApiKeyMissing) {
                    Text(
                        "Account searches need a Have I Been Pwned API key (paid subscription). Set it under Settings → API Keys. " +
                            "The Password tab works without a key.",
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text("Sends this address to haveibeenpwned.com when you tap search.")
                }
            },
            isError = isApiKeyMissing && account.isNotBlank()
        )

        Spacer(modifier = Modifier.height(16.dp))

        when (val state = uiState) {
            is HibpUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is HibpUiState.Success -> ResultsContent(state)
            is HibpUiState.NoResults -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No breaches found for ${state.account}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Only breaches that have been made public and loaded into Have I Been Pwned can show up here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    state.pasteNote?.let { OsintNotice(it, Modifier.padding(top = 12.dp)) }
                }
            }
            is HibpUiState.Error -> OsintNotice(state.message, kind = NoticeKind.ERROR)
            is HibpUiState.Idle -> {
                Text(
                    "Check whether an email address appears in known data breaches and public pastes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ResultsContent(state: HibpUiState.Success) {
    val copy = rememberCopyAction()
    val summary = state.summary
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "${summary.total} breach${if (summary.total == 1) "" else "es"}" +
                                (if (state.pastes.isNotEmpty()) ", ${state.pastes.size} paste${if (state.pastes.size == 1) "" else "s"}" else ""),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        SeverityBadge(summary.severity)
                    }
                    if (summary.passwordExposures > 0) {
                        Text(
                            "${summary.passwordExposures} exposed passwords or other credentials",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (summary.mostRecent != null) {
                        Text(
                            if (summary.earliest == summary.mostRecent) "Breach date: ${summary.mostRecent}"
                            else "Between ${summary.earliest} and ${summary.mostRecent}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (summary.exposedClasses.isNotEmpty()) {
                        Text(
                            "Exposed: " + summary.exposedClasses.take(8).joinToString(", ") { (name, count) ->
                                if (count > 1) "$name ($count)" else name
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    summary.advice.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    TextButton(
                        onClick = {
                            copy(
                                "Breach list",
                                "${state.account}: ${summary.total} breaches\n" + state.breaches.joinToString("\n") { b ->
                                    "${b.title} (${b.breachDate.ifBlank { "undated" }}): ${b.dataClasses.joinToString(", ")}"
                                }
                            )
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) { Text("Copy breach list") }
                }
            }
        }
        if (state.recordedFindings > 0) item { FindingsRecordedNotice(state.recordedFindings) }
        state.pasteNote?.let { note -> item { OsintNotice(note) } }

        items(state.breaches, key = { it.name }) { breach -> BreachItem(breach) }

        if (state.pastes.isNotEmpty()) {
            item {
                Text("Pastes (${state.pastes.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            items(state.pastes) { paste -> PasteItem(paste) }
        }
    }
}

@Composable
private fun BreachItem(breach: Breach) {
    var expanded by rememberSaveable(breach.name) { mutableStateOf(false) }
    val description = remember(breach.description) { BreachAnalyzer.cleanDescription(breach.description) }
    Card(modifier = Modifier.fillMaxWidth(), onClick = { expanded = !expanded }) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(breach.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                SeverityBadge(BreachAnalyzer.severity(breach))
            }
            Text(
                listOfNotNull(
                    breach.breachDate.ifBlank { null },
                    breach.domain.ifBlank { null },
                    if (breach.pwnCount > 0) "${BreachAnalyzer.formatCount(breach.pwnCount)} accounts" else null
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val flags = listOfNotNull(
                "Stealer log".takeIf { breach.isStealerLog },
                "Malware".takeIf { breach.isMalware },
                "Unverified".takeIf { !breach.isVerified },
                "Fabricated".takeIf { breach.isFabricated },
                "Spam list".takeIf { breach.isSpamList },
                "Sensitive".takeIf { breach.isSensitive }
            )
            if (flags.isNotEmpty()) {
                Text(flags.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
            Text(
                "Compromised data: ${breach.dataClasses.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PasteItem(paste: Paste) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        ListItem(
            headlineContent = { Text(paste.title ?: "Untitled paste") },
            supportingContent = {
                Text(
                    listOfNotNull(paste.source, paste.date?.take(10), "${paste.emailCount} addresses in the paste").joinToString(" · ")
                )
            },
            leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@Composable
private fun PasswordTab(viewModel: HibpViewModel) {
    // Deliberately `remember`, not `rememberSaveable`: a password must not be written into saved instance state.
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val state by viewModel.passwordState.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current
    val submit = {
        if (password.isNotEmpty() && state !is PasswordCheckState.Loading) {
            keyboardController?.hide()
            viewModel.checkPassword(password)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OsintNotice(
            "The password is hashed on this device. Only the first 5 characters of its SHA-1 hash are sent " +
                "(k-anonymity), so neither the password nor its full hash leaves the phone. No API key needed."
        )
        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                viewModel.clearPasswordResult()
            },
            label = { Text("Password to check") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password, imeAction = ImeAction.Search, autoCorrectEnabled = false
            ),
            keyboardActions = KeyboardActions(onSearch = { submit() }),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (visible) "Hide password" else "Show password"
                    )
                }
            }
        )
        Button(onClick = submit, enabled = password.isNotEmpty() && state !is PasswordCheckState.Loading, modifier = Modifier.fillMaxWidth()) {
            if (state is PasswordCheckState.Loading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text("Check against breached passwords")
        }

        when (val current = state) {
            is PasswordCheckState.Result -> {
                val pwned = current.count > 0
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (pwned) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                if (pwned) "Compromised password" else "Not in the breach corpus",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            if (pwned) SeverityBadge(if (current.count >= 10) RiskLevel.CRITICAL else RiskLevel.HIGH)
                        }
                        Text(PwnedPasswords.verdict(current.count), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            is PasswordCheckState.Error -> OsintNotice(current.message, kind = NoticeKind.ERROR)
            else -> Unit
        }
    }
}
