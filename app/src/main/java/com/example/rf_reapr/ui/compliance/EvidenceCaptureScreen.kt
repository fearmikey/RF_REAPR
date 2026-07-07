package com.example.rf_reapr.ui.compliance

import android.Manifest
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.Surface
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.rf_reapr.domain.model.Evidence
import com.example.rf_reapr.domain.model.EvidenceFolder
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun EvidenceCaptureScreen(
    viewModel: EvidenceCaptureViewModel,
    onBack: () -> Unit
) {
    val permissionState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    val currentProject by viewModel.currentProject.collectAsState()
    val currentFolder by viewModel.currentFolder.collectAsState()
    val folders by viewModel.folders.collectAsState()
    
    var showFolderSelector by remember { mutableStateOf(false) }
    var selectedEvidenceForAction by remember { mutableStateOf<Evidence?>(null) }
    var showTransferDialog by remember { mutableStateOf<TransferType?>(null) }
    var showProjectSettings by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        permissionState.launchMultiplePermissionRequest()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(
                            text = currentFolder?.name ?: "Capture Evidence",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = currentProject?.name ?: "No Project",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showProjectSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Project Settings")
                    }
                    IconButton(onClick = { showFolderSelector = true }) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "Select Folder")
                    }
                }
            )
        }
    ) { padding ->
        if (permissionState.allPermissionsGranted) {
            CameraContent(
                viewModel = viewModel, 
                modifier = Modifier.padding(padding),
                onEvidenceDetail = { selectedEvidenceForAction = it }
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Permissions required for camera and location.")
                    Button(onClick = { permissionState.launchMultiplePermissionRequest() }) {
                        Text("Grant Permissions")
                    }
                }
            }
        }

        if (showFolderSelector) {
            FolderSelectorDialog(
                folders = folders,
                onFolderSelected = {
                    viewModel.selectFolder(it)
                    showFolderSelector = false
                },
                onCreateFolder = { name ->
                    viewModel.createFolder(name)
                    showFolderSelector = false
                },
                onDismiss = { showFolderSelector = false }
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

        selectedEvidenceForAction?.let { evidence ->
            EvidenceDetailDialog(
                evidence = evidence,
                onDismiss = { selectedEvidenceForAction = null },
                onMove = { showTransferDialog = TransferType.MOVE },
                onCopy = { showTransferDialog = TransferType.COPY },
                onDelete = {
                    viewModel.deleteEvidence(evidence)
                    selectedEvidenceForAction = null
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
                        selectedEvidenceForAction?.let { evidence ->
                            if (type == TransferType.MOVE) {
                                viewModel.moveEvidence(evidence.id, project.id, targetFolder?.id)
                            } else {
                                viewModel.copyEvidence(evidence.id, project.id, targetFolder?.id)
                            }
                        }
                        showTransferDialog = null
                        selectedEvidenceForAction = null
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

@Composable
fun CameraContent(
    viewModel: EvidenceCaptureViewModel, 
    modifier: Modifier = Modifier,
    onEvidenceDetail: (Evidence) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val evidenceList by viewModel.evidenceList.collectAsState()
    val isCapturing by viewModel.isCapturing.collectAsState()
    val flashMode by viewModel.flashMode.collectAsState()

    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val imageCapture: ImageCapture = remember { ImageCapture.Builder().build() }

    DisposableEffect(context) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val rotation = when (orientation) {
                    in 45 until 135 -> Surface.ROTATION_270
                    in 135 until 225 -> Surface.ROTATION_180
                    in 225 until 315 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
                imageCapture.targetRotation = rotation
            }
        }
        listener.enable()
        onDispose {
            listener.disable()
            cameraExecutor.shutdown()
        }
    }

    LaunchedEffect(flashMode) {
        imageCapture.flashMode = flashMode
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    try {
                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture
                        )

                        // Tap to Focus Implementation
                        val gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                            override fun onSingleTapUp(e: MotionEvent): Boolean {
                                val factory = previewView.meteringPointFactory
                                val point = factory.createPoint(e.x, e.y)
                                val action = FocusMeteringAction.Builder(point).build()
                                camera.cameraControl.startFocusAndMetering(action)
                                return true
                            }
                        })

                        previewView.setOnTouchListener { _, event ->
                            gestureDetector.onTouchEvent(event)
                            true
                        }

                    } catch (e: Exception) {
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        IconButton(
            onClick = { viewModel.toggleFlash() },
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(
                imageVector = when (flashMode) {
                    ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                    ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                    else -> Icons.Default.FlashOff
                },
                contentDescription = "Flash Mode",
                tint = Color.White
            )
        }

        if (evidenceList.isNotEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(horizontal = 16.dp, vertical = 64.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.5f)).padding(8.dp)
            ) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(evidenceList) { evidence ->
                        EvidenceThumbnail(
                            evidence = evidence, 
                            onDelete = { viewModel.deleteEvidence(it) },
                            onDetail = { onEvidenceDetail(it) }
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)) {
            IconButton(
                onClick = {
                    imageCapture.takePicture(
                        cameraExecutor,
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                viewModel.captureEvidence(context, image)
                            }
                        }
                    )
                },
                enabled = !isCapturing,
                modifier = Modifier.size(80.dp).background(if (isCapturing) Color.Gray else MaterialTheme.colorScheme.primary, CircleShape)
            ) {
                if (isCapturing) {
                    CircularProgressIndicator(color = Color.White)
                } else {
                    Icon(Icons.Default.Camera, contentDescription = "Capture", tint = Color.White, modifier = Modifier.size(40.dp))
                }
            }
        }
    }
}

@Composable
fun EvidenceThumbnail(
    evidence: Evidence, 
    onDelete: (Evidence) -> Unit,
    onDetail: (Evidence) -> Unit
) {
    Box(modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp)).clickable { onDetail(evidence) }) {
        AsyncImage(model = File(evidence.filePath), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        IconButton(
            onClick = { onDelete(evidence) },
            modifier = Modifier.align(Alignment.TopEnd).size(24.dp).background(Color.Black.copy(alpha = 0.6f), CircleShape)
        ) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun FolderSelectorDialog(folders: List<EvidenceFolder>, onFolderSelected: (EvidenceFolder?) -> Unit, onCreateFolder: (String) -> Unit, onDismiss: () -> Unit) {
    var newFolderName by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Project Folders") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (isCreating) {
                    OutlinedTextField(value = newFolderName, onValueChange = { newFolderName = it }, label = { Text("New Folder Name") }, modifier = Modifier.fillMaxWidth())
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        item {
                            ListItem(headlineContent = { Text("Root (No Folder)") }, modifier = Modifier.clickable { onFolderSelected(null) }, leadingContent = { Icon(Icons.Default.FolderOpen, contentDescription = null) })
                        }
                        items(folders) { folder ->
                            ListItem(headlineContent = { Text(folder.name) }, modifier = Modifier.clickable { onFolderSelected(folder) }, leadingContent = { Icon(Icons.Default.FolderSpecial, contentDescription = null) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isCreating) {
                Button(onClick = { if (newFolderName.isNotBlank()) onCreateFolder(newFolderName) }, enabled = newFolderName.isNotBlank()) { Text("Create") }
            } else {
                Button(onClick = { isCreating = true }) { Text("New Folder") }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (isCreating) isCreating = false else onDismiss() }) { Text(if (isCreating) "Cancel" else "Close") }
        }
    )
}
