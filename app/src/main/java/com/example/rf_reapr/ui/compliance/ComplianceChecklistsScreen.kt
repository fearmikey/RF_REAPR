package com.example.rf_reapr.ui.compliance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.example.rf_reapr.domain.model.ComplianceControl
import com.example.rf_reapr.domain.model.ComplianceFramework
import com.example.rf_reapr.domain.model.ComplianceStatus
import com.example.rf_reapr.domain.repository.ComplianceRepository
import com.example.rf_reapr.ui.theme.RF_REAPRTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComplianceChecklistsScreen(
    viewModel: ComplianceViewModel,
    onFrameworkClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val frameworks by viewModel.frameworks.collectAsState()

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
    val mockRepository = object : ComplianceRepository {
        override fun getFrameworks(): List<ComplianceFramework> = ComplianceFramework.entries
        override fun getControlsForFramework(frameworkId: String): Flow<List<ComplianceControl>> = flowOf(emptyList())
        override suspend fun updateControlStatus(controlId: String, frameworkId: String, status: ComplianceStatus, notes: String) {}
    }
    RF_REAPRTheme {
        ComplianceChecklistsScreen(
            viewModel = ComplianceViewModel(mockRepository),
            onFrameworkClick = {},
            onBack = {}
        )
    }
}
