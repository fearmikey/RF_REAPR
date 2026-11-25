package com.fearmikey.rf_reapr.ui.compliance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.fearmikey.rf_reapr.domain.model.ComplianceFramework
import com.fearmikey.rf_reapr.ui.theme.RF_REAPRTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComplianceChecklistsScreen(
    viewModel: ComplianceViewModel,
    onFrameworkClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val frameworks by viewModel.frameworks.collectAsStateWithLifecycle()
    ComplianceChecklistsContent(frameworks = frameworks, onFrameworkClick = onFrameworkClick, onBack = onBack)
}

/** Stateless body, so previews don't have to construct a ViewModel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComplianceChecklistsContent(
    frameworks: List<ComplianceFramework>,
    onFrameworkClick: (String) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compliance Frameworks") },
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
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Select a framework to begin auditing.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            items(frameworks) { framework ->
                FrameworkCard(framework, onClick = { onFrameworkClick(framework.id) })
            }
        }
    }
}

@Composable
fun FrameworkCard(framework: ComplianceFramework, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        ListItem(
            headlineContent = { Text(framework.title, fontWeight = FontWeight.Bold) },
            supportingContent = { Text(framework.description) },
            leadingContent = { Icon(Icons.AutoMirrored.Filled.Assignment, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ComplianceChecklistsScreenPreview() {
    RF_REAPRTheme {
        ComplianceChecklistsContent(
            frameworks = ComplianceFramework.entries,
            onFrameworkClick = {},
            onBack = {}
        )
    }
}
