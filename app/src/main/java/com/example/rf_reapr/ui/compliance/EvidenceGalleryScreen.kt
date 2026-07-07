package com.example.rf_reapr.ui.compliance

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.rf_reapr.domain.model.Evidence
import com.example.rf_reapr.domain.model.EvidenceFolder
import com.example.rf_reapr.domain.model.EvidenceProject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvidenceGalleryScreen(
    viewModel: EvidenceCaptureViewModel,
    onBack: () -> Unit,
    onNavigateToCapture: () -> Unit
) {
    val folders by viewModel.folders.collectAsState()
    val evidenceList by viewModel.evidenceList.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    val currentFolder by viewModel.currentFolder.collectAsState()
    
    var selectedEvidenceForDetail by remember { mutableStateOf<Evidence?>(null) }
    var showCreationDialog by remember { mutableStateOf<CreationType?>(null) }
    var showTransferDialog by remember { mutableStateOf<TransferType?>(null) }
    var showProjectSettings by remember { mutableStateOf(false) }
    
    // Multi-selection state
    val selectedIds = remember { mutableStateListOf<String>() }
    val isInSelectionMode = selectedIds.isNotEmpty()
    var isManualSelectionModeActive by remember { mutableStateOf(false) }
    
    val effectivelyInSelectionMode = isInSelectionMode || isManualSelectionModeActive

    // Handle back button for selection mode and navigation
    BackHandler(enabled = effectivelyInSelectionMode || currentFolder != null) {
        if (effectivelyInSelectionMode) {
            selectedIds.clear()
            isManualSelectionModeActive = false
        } else if (currentFolder != null) {
            viewModel.selectFolder(null)
        }
    }

    Scaffold(
        topBar = {
            if (effectivelyInSelectionMode) {
                SelectionTopBar(
                    selectedCount = selectedIds.size,
                    onClearSelection = { 
                        selectedIds.clear()
                        isManualSelectionModeActive = false
                    },
                    onMove = { if (selectedIds.isNotEmpty()) showTransferDialog = TransferType.MOVE },
                    onCopy = { if (selectedIds.isNotEmpty()) showTransferDialog = TransferType.COPY },
                    onDelete = {
                        evidenceList.filter { it.id in selectedIds }.forEach { viewModel.deleteEvidence(it) }
                        selectedIds.clear()
                        isManualSelectionModeActive = false
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = currentFolder?.name ?: currentProject?.name ?: "Gallery",
                                style = MaterialTheme.typography.titleLarge
                            )
                            if (currentProject != null && currentFolder != null) {
                                Text(
                                    text = currentProject!!.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (currentFolder != null) {
                                viewModel.selectFolder(null)
                            } else {
                                onBack()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        // Blue "Add Images" button (Camera)
                        Button(
                            onClick = onNavigateToCapture,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Add Images")
                        }

                        IconButton(onClick = { isManualSelectionModeActive = true }) {
                            Icon(Icons.Default.Checklist, contentDescription = "Select")
                        }
                        
                        IconButton(onClick = { showCreationDialog = CreationType.FOLDER }) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder")
                        }

                        if (currentProject != null && currentFolder == null) {
                            IconButton(onClick = { showProjectSettings = true }) {
                                Icon(Icons.Default.Settings, contentDescription = "Project Settings")
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            Crossfade(targetState = currentFolder, label = "GalleryTransition") { folder ->
                if (folder == null) {
                    // Sub-folders and loose evidence for the project
                    SubFolderAndEvidenceGrid(
                        folders = folders,
                        evidenceList = evidenceList.filter { it.folderId == null },
                        selectedIds = selectedIds,
                        isInSelectionMode = effectivelyInSelectionMode,
                        onFolderClick = { viewModel.selectFolder(it) },
                        onEvidenceClick = { evidence ->
                            if (effectivelyInSelectionMode) {
                                if (evidence.id in selectedIds) selectedIds.remove(evidence.id)
                                else selectedIds.add(evidence.id)
                            } else {
                                selectedEvidenceForDetail = evidence
                            }
                        },
                        onEvidenceLongClick = { evidence ->
                            if (!effectivelyInSelectionMode) {
                                selectedIds.add(evidence.id)
                            }
                        },
                        onEmptyAction = { showCreationDialog = CreationType.FOLDER }
                    )
                } else {
                    // Evidence within a folder
                    EvidenceGrid(
                        evidenceList = evidenceList,
                        selectedIds = selectedIds,
                        isInSelectionMode = effectivelyInSelectionMode,
                        onEvidenceClick = { evidence ->
                            if (effectivelyInSelectionMode) {
                                if (evidence.id in selectedIds) selectedIds.remove(evidence.id)
                                else selectedIds.add(evidence.id)
                            } else {
                                selectedEvidenceForDetail = evidence
                            }
                        },
                        onEvidenceLongClick = { evidence ->
                            if (!effectivelyInSelectionMode) {
                                selectedIds.add(evidence.id)
                            }
                        },
                        onEmptyAction = { onNavigateToCapture() }
                    )
                }
            }
        }

        if (showCreationDialog != null) {
            CreationDialog(
                type = showCreationDialog!!,
                onCreate = { name ->
                    viewModel.createFolder(name)
                    showCreationDialog = null
                },
                onDismiss = { showCreationDialog = null }
            )
        }

        if (showProjectSettings && currentProject != null) {
            ProjectSettingsDialog(
                project = currentProject!!,
                onUpdate = { gps, date, time ->
                    viewModel.updateProjectSettings(gps, date, time)
                },
                onDismiss = { showProjectSettings = false }
            )
        }

        selectedEvidenceForDetail?.let { evidence ->
            EvidenceDetailDialog(
                evidence = evidence,
                onDismiss = { selectedEvidenceForDetail = null },
                onMove = { 
                    selectedIds.clear()
                    selectedIds.add(evidence.id)
                    showTransferDialog = TransferType.MOVE 
                },
                onCopy = { 
                    selectedIds.clear()
                    selectedIds.add(evidence.id)
                    showTransferDialog = TransferType.COPY 
                },
                onDelete = {
                    viewModel.deleteEvidence(evidence)
                    selectedEvidenceForDetail = null
                }
            )
        }

        showTransferDialog?.let { type ->
            currentProject?.let { project ->
                InternalTransferDialog(
                    type = type,
                    projectName = project.name,
                    folders = folders,
                    onConfirm = { targetFolder ->
                        selectedIds.forEach { id ->
                            if (type == TransferType.MOVE) {
                                viewModel.moveEvidence(id, project.id, targetFolder?.id)
                            } else {
                                viewModel.copyEvidence(id, project.id, targetFolder?.id)
                            }
                        }
                        showTransferDialog = null
                        selectedIds.clear()
                        isManualSelectionModeActive = false
                        selectedEvidenceForDetail = null
                    },
                    onDismiss = { showTransferDialog = null },
                    onCreateFolder = { name ->
                        viewModel.createFolder(name)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    TopAppBar(
        title = { Text("$selectedCount selected") },
        navigationIcon = {
            IconButton(onClick = onClearSelection) {
                Icon(Icons.Default.Close, contentDescription = "Clear")
            }
        },
        actions = {
            IconButton(onClick = onMove, enabled = selectedCount > 0) {
                Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = "Move")
            }
            IconButton(onClick = onCopy, enabled = selectedCount > 0) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
            }
            IconButton(onClick = onDelete, enabled = selectedCount > 0) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    )
}

enum class TransferType { MOVE, COPY }
enum class CreationType { PROJECT, FOLDER }

@Composable
fun ProjectFolderGrid(
    projects: List<EvidenceProject>, 
    onProjectClick: (EvidenceProject) -> Unit,
    onEmptyAction: () -> Unit
) {
    if (projects.isEmpty()) {
        EmptyState("No projects found. Create one to begin.", Icons.Default.FolderOpen, onEmptyAction)
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(projects) { project ->
                FolderItem(
                    name = project.name,
                    icon = Icons.Default.Folder,
                    date = project.createdAt,
                    onClick = { onProjectClick(project) }
                )
            }
        }
    }
}

@Composable
fun SubFolderAndEvidenceGrid(
    folders: List<EvidenceFolder>,
    evidenceList: List<Evidence>,
    selectedIds: List<String>,
    isInSelectionMode: Boolean,
    onFolderClick: (EvidenceFolder) -> Unit,
    onEvidenceClick: (Evidence) -> Unit,
    onEvidenceLongClick: (Evidence) -> Unit,
    onEmptyAction: () -> Unit
) {
    if (folders.isEmpty() && evidenceList.isEmpty()) {
        EmptyState("No sub-folders or photos found.", Icons.Default.FolderSpecial, onEmptyAction)
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(120.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(folders) { folder ->
                FolderItem(
                    name = folder.name,
                    icon = Icons.Default.FolderSpecial,
                    date = folder.createdAt,
                    onClick = { if (!isInSelectionMode) onFolderClick(folder) }
                )
            }
            items(evidenceList) { evidence ->
                GalleryItem(
                    evidence = evidence,
                    isSelected = evidence.id in selectedIds,
                    isInSelectionMode = isInSelectionMode,
                    onItemClick = { onEvidenceClick(it) },
                    onItemLongClick = { onEvidenceLongClick(it) }
                )
            }
        }
    }
}

@Composable
fun FolderItem(name: String, icon: androidx.compose.ui.graphics.vector.ImageVector, date: Date, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            Text(text = sdf.format(date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun EvidenceGrid(
    evidenceList: List<Evidence>, 
    selectedIds: List<String>,
    isInSelectionMode: Boolean,
    onEvidenceClick: (Evidence) -> Unit,
    onEvidenceLongClick: (Evidence) -> Unit,
    onEmptyAction: () -> Unit
) {
    if (evidenceList.isEmpty()) {
        EmptyState("No evidence found in this folder", Icons.Default.PhotoLibrary, onEmptyAction)
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(120.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(evidenceList) { evidence ->
                GalleryItem(
                    evidence = evidence, 
                    isSelected = evidence.id in selectedIds,
                    isInSelectionMode = isInSelectionMode,
                    onItemClick = { onEvidenceClick(it) }, 
                    onItemLongClick = { onEvidenceLongClick(it) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryItem(
    evidence: Evidence, 
    isSelected: Boolean,
    isInSelectionMode: Boolean,
    onItemClick: (Evidence) -> Unit, 
    onItemLongClick: (Evidence) -> Unit
) {
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .combinedClickable(
                onClick = { onItemClick(evidence) },
                onLongClick = { onItemLongClick(evidence) }
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 8.dp else 2.dp),
        border = if (isSelected) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary), width = 3.dp) else null
    ) {
        Box {
            AsyncImage(
                model = File(evidence.filePath), 
                contentDescription = null, 
                contentScale = ContentScale.Crop, 
                modifier = Modifier.fillMaxSize().then(
                    if (isSelected) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)) else Modifier
                )
            )
            
            // Selection indicator
            if (isInSelectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyState(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(16.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onClick) { Text("Get Started") }
        }
    }
}

@Composable
fun EvidenceDetailDialog(
    evidence: Evidence, 
    onDismiss: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = File(evidence.filePath),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().align(Alignment.Center),
                    contentScale = ContentScale.Fit
                )
                
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(bottom = 48.dp, start = 24.dp, end = 24.dp, top = 24.dp)
                        .fillMaxWidth()
                ) {
                    val sdf = SimpleDateFormat("MMM dd, yyyy - HH:mm:ss", Locale.getDefault())
                    Text(
                        text = sdf.format(evidence.timestamp),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (evidence.latitude != null && evidence.longitude != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "GPS: ${String.format(Locale.US, "%.6f", evidence.latitude)}, ${String.format(Locale.US, "%.6f", evidence.longitude)}",
                            color = Color.LightGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    
                    if (evidence.notes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = evidence.notes,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onMove,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Move")
                        }
                        Button(
                            onClick = onCopy,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Copy")
                        }
                    }
                }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(top = 48.dp, start = 16.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun InternalTransferDialog(
    type: TransferType,
    projectName: String,
    folders: List<EvidenceFolder>,
    onConfirm: (EvidenceFolder?) -> Unit,
    onDismiss: () -> Unit,
    onCreateFolder: (String) -> Unit
) {
    var selectedFolder by remember { mutableStateOf<EvidenceFolder?>(null) }
    var isRootSelected by remember { mutableStateOf(false) }
    var isCreatingFolder by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (type == TransferType.MOVE) "Move within $projectName" else "Copy within $projectName") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (isCreatingFolder) {
                    Text("New Folder in $projectName", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = { Text("Folder Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Select Target Folder:", style = MaterialTheme.typography.labelMedium)
                        TextButton(
                            onClick = { isCreatingFolder = true },
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("New Folder", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    
                    LazyColumn(modifier = Modifier.heightIn(max = 250.dp)) {
                        item {
                            ListItem(
                                headlineContent = { Text("Project Root (No Folder)") },
                                modifier = Modifier.clickable { 
                                    isRootSelected = true
                                    selectedFolder = null
                                },
                                leadingContent = { RadioButton(selected = isRootSelected, onClick = null) }
                            )
                        }
                        
                        items(folders) { folder ->
                            ListItem(
                                headlineContent = { Text(folder.name) },
                                modifier = Modifier.clickable { 
                                    selectedFolder = folder
                                    isRootSelected = false
                                },
                                leadingContent = { RadioButton(selected = selectedFolder == folder, onClick = null) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isCreatingFolder) {
                Button(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            onCreateFolder(newFolderName)
                            isCreatingFolder = false
                            newFolderName = ""
                        }
                    },
                    enabled = newFolderName.isNotBlank()
                ) {
                    Text("Create & Select")
                }
            } else {
                Button(
                    onClick = { onConfirm(selectedFolder) },
                    enabled = isRootSelected || selectedFolder != null
                ) {
                    Text(if (type == TransferType.MOVE) "Move" else "Copy")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = { 
                    if (isCreatingFolder) {
                        isCreatingFolder = false
                        newFolderName = ""
                    } else {
                        onDismiss()
                    }
                }
            ) { 
                Text(if (isCreatingFolder) "Back" else "Cancel") 
            }
        }
    )
}

@Composable
fun CreationDialog(type: CreationType, onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (type == CreationType.PROJECT) "New Project" else "New Folder") },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onCreate(name) }, enabled = name.isNotBlank()) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ProjectSettingsDialog(
    project: EvidenceProject,
    onUpdate: (gps: Boolean, date: Boolean, time: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var showGps by remember { mutableStateOf(project.showGps) }
    var showDate by remember { mutableStateOf(project.showDate) }
    var showTimestamp by remember { mutableStateOf(project.showTimestamp) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Project Watermark Settings") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Enable or disable info baked into captures:", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(16.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { showGps = !showGps }) {
                    Checkbox(checked = showGps, onCheckedChange = { showGps = it })
                    Text("GPS Coordinates")
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { showDate = !showDate }) {
                    Checkbox(checked = showDate, onCheckedChange = { showDate = it })
                    Text("Date (YYYY-MM-DD)")
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { showTimestamp = !showTimestamp }) {
                    Checkbox(checked = showTimestamp, onCheckedChange = { showTimestamp = it })
                    Text("Timestamp (HH:MM:SS)")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onUpdate(showGps, showDate, showTimestamp)
                onDismiss()
            }) {
                Text("Save Settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
