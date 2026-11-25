package com.fearmikey.rf_reapr.ui.web

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fearmikey.rf_reapr.domain.model.DorkCategory
import com.fearmikey.rf_reapr.domain.model.DorkScope
import com.fearmikey.rf_reapr.domain.model.GoogleDork
import com.fearmikey.rf_reapr.domain.model.GoogleDorkDatabase
import com.fearmikey.rf_reapr.domain.service.DorkQueryBuilder
import com.fearmikey.rf_reapr.domain.service.SearchEngine
import com.fearmikey.rf_reapr.ui.components.ScopeWarning
import com.fearmikey.rf_reapr.ui.theme.WebGold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleDorksScreen(
    viewModel: GoogleDorksViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val filteredDorks = remember(state.selectedCategory, state.searchQuery) { state.filteredDorks }
    val categoryCounts = remember { GoogleDorkDatabase.dorks.groupingBy { it.category }.eachCount() }
    val uriHandler = LocalUriHandler.current
    val copy = rememberCopyAction()
    val target = state.target
    // Queries are derived from the collected state here, so every card follows target/engine changes.
    val scopeTarget = target.orEmpty()
    val engine = state.engine
    val customBuilt = remember(state.customQuery, scopeTarget) { DorkQueryBuilder.buildCustom(state.customQuery, scopeTarget) }
    val open: (String) -> Unit = { query -> runCatching { uriHandler.openUri(engine.url(query)) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Google Dorks Recon") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)
        ) {
            item {
                OutlinedTextField(
                    value = state.targetDomain,
                    onValueChange = { viewModel.updateTargetDomain(it) },
                    label = { Text("Target domain (optional)") },
                    placeholder = { Text("example.com") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = WebGold) },
                    trailingIcon = {
                        if (state.targetDomain.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateTargetDomain("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    isError = target == null,
                    supportingText = {
                        Text(
                            when {
                                target == null -> "Not a valid domain; queries are shown without a target."
                                target.isEmpty() -> "Without a target the queries search the whole web."
                                else -> "Queries are limited to $target (or mention it, for third-party sites)."
                            }
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth()
                )
                if (!target.isNullOrEmpty()) ScopeWarning(target = target)
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SearchEngine.entries.forEach { engine ->
                        FilterChip(
                            selected = state.engine == engine,
                            onClick = { viewModel.selectEngine(engine) },
                            label = { Text(engine.label) }
                        )
                    }
                }
                state.engine.note?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            item {
                CustomDorkCard(
                    query = state.customQuery,
                    built = customBuilt,
                    hasTarget = scopeTarget.isNotEmpty(),
                    onQueryChange = viewModel::updateCustomQuery,
                    onOperator = viewModel::appendOperator,
                    onCopy = { copy("Query", customBuilt) },
                    onLaunch = { open(customBuilt) }
                )
            }

            item {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    label = { Text("Filter the dork library") },
                    placeholder = { Text("e.g. .env, admin, cameras") },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = state.selectedCategory == null,
                        onClick = { viewModel.selectCategory(null) },
                        label = { Text("All (${GoogleDorkDatabase.dorks.size})") }
                    )
                    DorkCategory.entries.forEach { category ->
                        FilterChip(
                            selected = state.selectedCategory == category,
                            onClick = { viewModel.selectCategory(category) },
                            label = { Text("${category.displayName} (${categoryCounts[category] ?: 0})") }
                        )
                    }
                }
                Text(
                    text = "${filteredDorks.size} queries",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            items(filteredDorks, key = { it.title }) { dork ->
                val query = remember(dork, scopeTarget) { DorkQueryBuilder.build(dork, scopeTarget) }
                GoogleDorkCard(
                    dork = dork,
                    formattedQuery = query,
                    onLaunch = { open(query) },
                    onCopy = { copy("Query", query) }
                )
            }
        }
    }
}

@Composable
private fun CustomDorkCard(
    query: String,
    built: String,
    hasTarget: Boolean,
    onQueryChange: (String) -> Unit,
    onOperator: (String) -> Unit,
    onCopy: () -> Unit,
    onLaunch: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Custom query", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("filetype:pdf \"internal use only\"") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DorkQueryBuilder.operators.forEach { operator ->
                    SuggestionChip(onClick = { onOperator(operator) }, label = { Text(operator, fontFamily = FontFamily.Monospace) })
                }
            }
            if (query.isNotBlank()) {
                if (hasTarget) QueryBlock(built)
                DorkActions(onCopy = onCopy, onLaunch = onLaunch)
            }
        }
    }
}

@Composable
private fun QueryBlock(query: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
    ) {
        Text(
            text = query,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(8.dp)
        )
    }
}

@Composable
private fun DorkActions(onCopy: () -> Unit, onLaunch: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(onClick = onCopy, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Copy")
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = onLaunch,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            colors = ButtonDefaults.buttonColors(containerColor = WebGold)
        ) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.surface)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Search", color = MaterialTheme.colorScheme.surface)
        }
    }
}

@Composable
fun GoogleDorkCard(
    dork: GoogleDork,
    formattedQuery: String,
    onLaunch: () -> Unit,
    onCopy: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = dork.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = dork.category.displayName + if (dork.scope == DorkScope.KEYWORD) " · third-party site" else "",
                style = MaterialTheme.typography.labelSmall,
                color = WebGold
            )
            Text(
                text = dork.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            QueryBlock(formattedQuery)
            DorkActions(onCopy = onCopy, onLaunch = onLaunch)
        }
    }
}
