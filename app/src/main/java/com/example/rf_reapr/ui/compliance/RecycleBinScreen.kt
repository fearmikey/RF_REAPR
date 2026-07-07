package com.example.rf_reapr.ui.compliance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.rf_reapr.domain.model.Evidence
import com.example.rf_reapr.domain.model.EvidenceFolder
import com.example.rf_reapr.domain.model.EvidenceProject
import java.io.File
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinScreen(
    viewModel: RecycleBinViewModel,
    onBack: () -> Unit
) {
    val deletedProjects by viewModel.deletedProjects.collectAsState()
    val deletedFolders by viewModel.deletedFolders.collectAsState()
    val deletedEvidence by viewModel.deletedEvidence.collectAsState()
    val isEmpty by viewModel.isEmpty.collectAsState()

    var showEmptyConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Recycle Bin")
                        Text(
                            "Items are auto-purged after 30 days",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!isEmpty) {
                        IconButton(onClick = { showEmptyConfirm = true }) {
                            Icon(Icons.Default.DeleteForever, contentDescription = "Empty Bin", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (isEmpty) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Recycle Bin is empty", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (deletedProjects.isNotEmpty()) {
                    item { Text("Deleted Projects", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    items(deletedProjects) { project ->
                        TrashHierarchyItem(
                            name = project.name,
                            icon = Icons.Default.Folder,
                            deletedAt = project.deletedAt,
                            onRestore = { viewModel.restoreProject(project) },
                            onPurge = { viewModel.purgeProject(project) }
                        )
                    }
                }

                if (deletedFolders.isNotEmpty()) {
                    item { Text("Deleted Folders", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    items(deletedFolders) { folder ->
                        TrashHierarchyItem(
                            name = folder.name,
                            icon = Icons.Default.FolderSpecial,
                            deletedAt = folder.deletedAt,
                            onRestore = { viewModel.restoreFolder(folder) },
                            onPurge = { viewModel.purgeFolder(folder) }
                        )
                    }
                }

                if (deletedEvidence.isNotEmpty()) {
                    item { Text("Deleted Photos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    item {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(100.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.heightIn(max = 2000.dp) // Large enough to scroll
                        ) {
                            items(deletedEvidence) { evidence ->
                                TrashPhotoItem(
                                    evidence = evidence,
                                    onRestore = { viewModel.restoreEvidence(evidence) },
                                    onPurge = { viewModel.purgeEvidence(evidence) }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showEmptyConfirm) {
            AlertDialog(
                onDismissRequest = { showEmptyConfirm = false },
                title = { Text("Empty Recycle Bin?") },
                text = { Text("All items will be permanently deleted from your device. This cannot be undone.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.emptyRecycleBin()
                            showEmptyConfirm = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Empty Bin")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEmptyConfirm = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
fun TrashHierarchyItem(
    name: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    deletedAt: java.util.Date?,
    onRestore: () -> Unit,
    onPurge: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        ListItem(
            headlineContent = { Text(name) },
            leadingContent = { Icon(icon, contentDescription = null) },
            supportingContent = {
                val daysRemaining = deletedAt?.let {
                    val diff = it.time + TimeUnit.DAYS.toMillis(30) - System.currentTimeMillis()
                    (diff / TimeUnit.DAYS.toMillis(1)).coerceAtLeast(0)
                } ?: 30
                Text("Purges in $daysRemaining days", color = MaterialTheme.colorScheme.error)
            },
            trailingContent = {
                Row {
                    IconButton(onClick = onRestore) {
                        Icon(Icons.Default.Restore, contentDescription = "Restore")
                    }
                    IconButton(onClick = onPurge) {
                        Icon(Icons.Default.DeleteForever, contentDescription = "Purge", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        )
    }
}

@Composable
fun TrashPhotoItem(
    evidence: Evidence,
    onRestore: () -> Unit,
    onPurge: () -> Unit
) {
    Card(
        modifier = Modifier.aspectRatio(1f),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box {
            AsyncImage(
                model = File(evidence.filePath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.6f)
            )
            
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.4f)),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = onRestore, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Restore, contentDescription = "Restore", tint = Color.White)
                }
                IconButton(onClick = onPurge, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.DeleteForever, contentDescription = "Purge", tint = Color.White)
                }
            }
        }
    }
}
