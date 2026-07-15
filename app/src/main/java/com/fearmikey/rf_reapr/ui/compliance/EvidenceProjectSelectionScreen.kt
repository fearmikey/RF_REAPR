package com.fearmikey.rf_reapr.ui.compliance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fearmikey.rf_reapr.domain.model.EvidenceProject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvidenceProjectSelectionScreen(
    viewModel: EvidenceCaptureViewModel,
    onProjectSelected: (EvidenceProject) -> Unit,
    onNavigateToRecycleBin: () -> Unit,
    onBack: () -> Unit,
) {
    val projects by viewModel.projects.collectAsState()
    var showProjectCreator by remember { mutableStateOf(value = false) }
    var projectToDelete by remember { mutableStateOf<EvidenceProject?>(value = null) }
    
    // Reset selection whenever we enter this screen
    LaunchedEffect(Unit) {
        viewModel.resetProjectSelection()
    }

    // Auto-show creator if no projects exist (only once per entry)
    var hasAutoPrompted by remember { mutableStateOf(value = false) }
    LaunchedEffect(projects) {
        if (!hasAutoPrompted && projects.isEmpty()) {
            showProjectCreator = true
            hasAutoPrompted = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select a Project") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToRecycleBin) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Recycle Bin")
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Available Projects",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = { showProjectCreator = true },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("New Project")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (projects.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("No projects found. Create one to begin.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(projects) { project ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    viewModel.selectProject(project)
                                    onProjectSelected(project)
                                },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            ListItem(
                                headlineContent = { Text(project.name) },
                                supportingContent = { Text("Physical Security Audit") },
                                leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) },
                                trailingContent = {
                                    IconButton(onClick = { projectToDelete = project }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete Project",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showProjectCreator) {
            var newProjectName by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showProjectCreator = false },
                title = { Text("New Project") },
                text = {
                    OutlinedTextField(
                        value = newProjectName,
                        onValueChange = { newProjectName = it },
                        label = { Text("Project Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newProjectName.isNotBlank()) {
                                viewModel.createProject(newProjectName)
                                showProjectCreator = false
                            }
                        },
                        enabled = newProjectName.isNotBlank()
                    ) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showProjectCreator = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        projectToDelete?.let { project ->
            AlertDialog(
                onDismissRequest = { projectToDelete = null },
                title = { Text("Delete Project?") },
                text = { Text("Are you sure you want to delete '${project.name}'? This will permanently remove all folders and evidence.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteProject(project)
                            projectToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { projectToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
