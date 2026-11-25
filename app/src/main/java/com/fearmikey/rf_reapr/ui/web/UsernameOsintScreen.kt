package com.fearmikey.rf_reapr.ui.web

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fearmikey.rf_reapr.domain.model.UsernameCheckResult
import com.fearmikey.rf_reapr.domain.model.UsernameSiteCategory
import com.fearmikey.rf_reapr.domain.model.UsernameStatus
import com.fearmikey.rf_reapr.ui.theme.NetworkGreen
import com.fearmikey.rf_reapr.ui.theme.WebGold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsernameOsintScreen(
    viewModel: UsernameOsintViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val copy = rememberCopyAction()

    val displayedResults = remember(state.results, state.selectedCategory, state.filterFoundOnly) { state.displayedResults }
    val progress = if (state.totalSites > 0) state.results.size.toFloat() / state.totalSites else 0f
    val submit = {
        keyboardController?.hide()
        viewModel.startSearch()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Username OSINT") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!state.isScanning && state.results.isNotEmpty()) {
                        IconButton(onClick = { copy("Report", viewModel.report()) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy report")
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
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = state.username,
                onValueChange = { viewModel.onUsernameChanged(it) },
                label = { Text("Username") },
                placeholder = { Text("e.g. torvalds") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = WebGold) },
                trailingIcon = {
                    if (state.isScanning) {
                        IconButton(onClick = { viewModel.cancelSearch() }) {
                            Icon(Icons.Default.Cancel, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        IconButton(onClick = submit, enabled = state.username.isNotBlank()) {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = WebGold)
                        }
                    }
                },
                isError = state.inputError != null,
                supportingText = {
                    Text(state.inputError ?: "Requests the public profile page on ${state.totalSites} sites. No logins, nothing is posted.")
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(6.dp))

            if (state.isScanning) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = WebGold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = "Checking sites… (${state.results.size}/${state.totalSites})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${state.foundCount} found",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = NetworkGreen
                    )
                }
            } else if (state.results.isNotEmpty()) {
                Text(
                    text = "\"${state.searchedName}\": found on ${state.foundCount} of ${state.results.size} sites" +
                        (if (state.uncertainCount > 0) ", ${state.uncertainCount} to check by hand" else ""),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (state.foundCount > 0) NetworkGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "A matching username is a lead, not proof that the accounts belong to the same person.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = state.filterFoundOnly,
                    onClick = { viewModel.toggleFilterFoundOnly() },
                    label = { Text("Found only") },
                    leadingIcon = if (state.filterFoundOnly) {
                        { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
                FilterChip(
                    selected = state.selectedCategory == null,
                    onClick = { viewModel.selectCategory(null) },
                    label = { Text("All categories") }
                )
                UsernameSiteCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = state.selectedCategory == cat,
                        onClick = { viewModel.selectCategory(cat) },
                        label = { Text(cat.title) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (displayedResults.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when {
                            state.isScanning -> "Checking sites…"
                            state.results.isEmpty() -> "Enter a username to see where it is registered."
                            else -> "Nothing matches the current filters."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(displayedResults, key = { it.site.name }) { result ->
                        UsernameResultCard(
                            result = result,
                            onOpen = { runCatching { uriHandler.openUri(result.profileUrl) } },
                            onCopy = { copy("URL", result.profileUrl) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UsernameResultCard(
    result: UsernameCheckResult,
    onOpen: () -> Unit,
    onCopy: () -> Unit
) {
    val found = result.status == UsernameStatus.FOUND
    // "Check manually" entries stay openable: the person can look where the automated check was blocked.
    val openable = found || result.status == UsernameStatus.UNCERTAIN || result.status == UsernameStatus.ERROR
    val statusColor = when (result.status) {
        UsernameStatus.FOUND -> NetworkGreen
        UsernameStatus.UNCERTAIN -> WebGold
        UsernameStatus.ERROR -> MaterialTheme.colorScheme.error
        UsernameStatus.NOT_FOUND, UsernameStatus.INVALID -> Color.Gray
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (found) 0.8f else 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = result.site.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = result.site.category.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (openable) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = result.profileUrl,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = result.status.label + (result.note?.let { ": $it" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (found) FontWeight.SemiBold else FontWeight.Normal,
                    color = statusColor
                )
            }

            if (openable) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy URL", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onOpen, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open profile", tint = WebGold, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
