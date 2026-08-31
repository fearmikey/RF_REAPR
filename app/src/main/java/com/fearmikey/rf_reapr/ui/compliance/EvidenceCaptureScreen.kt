package com.fearmikey.rf_reapr.ui.compliance

import android.Manifest
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.ZoomState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import coil.compose.AsyncImage
import com.fearmikey.rf_reapr.domain.model.Evidence
import com.fearmikey.rf_reapr.domain.model.EvidenceFolder
import com.fearmikey.rf_reapr.domain.model.EvidenceProject
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
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

    val currentProject by viewModel.currentProject.collectAsStateWithLifecycle()
    val currentFolder by viewModel.currentFolder.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    
    var showFolderSelector by rememberSaveable { mutableStateOf(false) }
    var selectedEvidenceForAction by remember { mutableStateOf<Evidence?>(null) }
    var showTransferDialog by rememberSaveable { mutableStateOf<TransferType?>(null) }
    var showProjectSettings by rememberSaveable { mutableStateOf(false) }
    var captureNotes by rememberSaveable { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                onEvidenceDetail = { selectedEvidenceForAction = it },
                captureNotes = captureNotes,
                onNotesChange = { captureNotes = it },
                onCameraError = { message ->
                    coroutineScope.launch { snackbarHostState.showSnackbar(message) }
                }
            )
        } else {
            PermissionDeniedContent(
                modifier = Modifier.padding(padding),
                onGrant = { permissionState.launchMultiplePermissionRequest() }
            )
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
                },
                viewModel = viewModel
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

        if (currentProject == null) {
            if (projects.isNotEmpty()) {
                ProjectSelectorDialog(
                    projects = projects,
                    onProjectSelected = { viewModel.selectProject(it) },
                    onCreateProject = { viewModel.createProject(it) },
                    onDismiss = onBack
                )
            } else {
                // Force project creation if none exist
                var newProjectName by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = onBack,
                    title = { Text("Create First Project") },
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
                            onClick = { if (newProjectName.isNotBlank()) viewModel.createProject(newProjectName) },
                            enabled = newProjectName.isNotBlank()
                        ) { Text("Create") }
                    },
                    dismissButton = {
                        TextButton(onClick = onBack) { Text("Cancel") }
                    }
                )
            }
        }
    }
}

@Composable
fun PermissionDeniedContent(
    modifier: Modifier = Modifier,
    onGrant: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF00050A))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Security,
            contentDescription = null,
            tint = Color(0xFFEF5350), // Red
            modifier = Modifier.size(64.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "HARDWARE ACCESS BLOCKED",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Evidence capture requires direct access to the Camera and Location sensors for verifiable audit logging.\n\nPlease grant permissions to proceed.",
            color = Color.Gray,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onGrant,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFEF5350),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = "GRANT ACCESS",
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun ProjectSelectorDialog(
    projects: List<EvidenceProject>,
    onProjectSelected: (EvidenceProject) -> Unit,
    onCreateProject: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var isCreating by remember { mutableStateOf(false) }
    var newProjectName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isCreating) "New Project" else "Select Project") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (isCreating) {
                    OutlinedTextField(
                        value = newProjectName,
                        onValueChange = { newProjectName = it },
                        label = { Text("Project Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(projects) { project ->
                            ListItem(
                                headlineContent = { Text(project.name) },
                                modifier = Modifier.clickable { onProjectSelected(project) },
                                leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isCreating) {
                Button(
                    onClick = { if (newProjectName.isNotBlank()) onCreateProject(newProjectName) },
                    enabled = newProjectName.isNotBlank()
                ) { Text("Create") }
            } else {
                Button(onClick = { isCreating = true }) { Text("New Project") }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (isCreating) isCreating = false else onDismiss() }) {
                Text(if (isCreating) "Cancel" else "Exit Camera")
            }
        }
    )
}

@Composable
fun CameraContent(
    viewModel: EvidenceCaptureViewModel, 
    modifier: Modifier = Modifier,
    onEvidenceDetail: (Evidence) -> Unit,
    captureNotes: String,
    onNotesChange: (String) -> Unit,
    onCameraError: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val evidenceList by viewModel.evidenceList.collectAsStateWithLifecycle()
    val isCapturing by viewModel.isCapturing.collectAsStateWithLifecycle()
    val flashMode by viewModel.flashMode.collectAsStateWithLifecycle()

    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    
    val zoomState by produceState<ZoomState?>(initialValue = null, camera) {
        val cameraInfo = camera?.cameraInfo ?: return@produceState
        val observer = Observer<ZoomState> { value = it }
        cameraInfo.zoomState.observeForever(observer)
        awaitDispose {
            cameraInfo.zoomState.removeObserver(observer)
        }
    }

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
                        val boundCamera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture
                        )
                        camera = boundCamera

                        // Tap to Focus Implementation
                        val gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                            override fun onSingleTapUp(e: MotionEvent): Boolean {
                                val factory = previewView.meteringPointFactory
                                val point = factory.createPoint(e.x, e.y)
                                val action = FocusMeteringAction.Builder(point).build()
                                boundCamera.cameraControl.startFocusAndMetering(action)
                                return true
                            }
                        })

                        // Pinch to Zoom Implementation
                        val scaleGestureDetector = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                            override fun onScale(detector: ScaleGestureDetector): Boolean {
                                val currentZoomRatio = boundCamera.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                                val delta = detector.scaleFactor
                                boundCamera.cameraControl.setZoomRatio(currentZoomRatio * delta)
                                return true
                            }
                        })

                        previewView.setOnTouchListener { _, event ->
                            scaleGestureDetector.onTouchEvent(event)
                            gestureDetector.onTouchEvent(event)
                            true
                        }

                    } catch (e: Exception) {
                        Log.e("EvidenceCapture", "Failed to bind camera use cases", e)
                        onCameraError("Camera failed to start: ${e.message ?: "Unknown error"}")
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

        // Bottom control cluster: zoom controls, notes, and shutter are stacked
        // vertically (instead of overlapping absolute-positioned boxes) and the
        // whole cluster is pushed above the IME + nav bar so nothing overlaps
        // and the notes field stays visible while the keyboard is open.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Zoom Ratio Indicator
            zoomState?.let { state ->
                if (state.zoomRatio > 1.05f || state.zoomRatio < 0.95f) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = "%.1fx".format(Locale.US, state.zoomRatio),
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Zoom Shortcuts
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                val shortcuts = listOf(0.5f, 1f, 2f, 5f)
                shortcuts.forEach { ratio ->
                    val isSupported = zoomState?.let { ratio >= it.minZoomRatio && ratio <= it.maxZoomRatio } ?: (ratio == 1f)
                    if (isSupported) {
                        val isSelected = zoomState?.let { Math.abs(it.zoomRatio - ratio) < 0.05f } ?: (ratio == 1f)
                        ZoomShortcutButton(
                            ratio = ratio,
                            isSelected = isSelected,
                            onClick = {
                                camera?.cameraControl?.setZoomRatio(ratio)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Live Notes Input - multiline so longer audit notes aren't truncated
            OutlinedTextField(
                value = captureNotes,
                onValueChange = onNotesChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                placeholder = { Text("Add capture notes...", color = Color.White.copy(alpha = 0.6f)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    unfocusedContainerColor = Color.Black.copy(alpha = 0.4f),
                    focusedContainerColor = Color.Black.copy(alpha = 0.6f),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp),
                minLines = 1,
                maxLines = 4
            )

            Spacer(modifier = Modifier.height(16.dp))

            Box {
                IconButton(
                    onClick = {
                        imageCapture.takePicture(
                            cameraExecutor,
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    viewModel.captureEvidence(context, image, captureNotes)
                                    onNotesChange("") // Clear notes after capture
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    Log.e("EvidenceCapture", "Image capture failed", exception)
                                    onCameraError("Capture failed: ${exception.message ?: "Unknown error"}")
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
}

@Composable
fun ZoomShortcutButton(
    ratio: Float,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val text = if (ratio == 0.5f) ".5" else if (ratio % 1f == 0f) ratio.toInt().toString() else ratio.toString()
    
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        border = if (isSelected) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
        modifier = Modifier.size(36.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
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
