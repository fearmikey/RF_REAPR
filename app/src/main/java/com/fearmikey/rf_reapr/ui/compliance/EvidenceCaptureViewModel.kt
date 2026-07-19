package com.fearmikey.rf_reapr.ui.compliance

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.location.Location
import android.net.Uri
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.model.Evidence
import com.fearmikey.rf_reapr.domain.model.EvidenceFolder
import com.fearmikey.rf_reapr.domain.model.EvidenceProject
import com.fearmikey.rf_reapr.domain.repository.EvidenceRepository
import com.fearmikey.rf_reapr.domain.repository.LogRepository
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class EvidenceCaptureViewModel(
    private val evidenceRepository: EvidenceRepository,
    private val logRepository: LogRepository
) : ViewModel() {

    private val _projects = MutableStateFlow<List<EvidenceProject>>(emptyList())
    val projects: StateFlow<List<EvidenceProject>> = _projects.asStateFlow()

    private val _currentProject = MutableStateFlow<EvidenceProject?>(null)
    val currentProject: StateFlow<EvidenceProject?> = _currentProject.asStateFlow()

    private val _folders = MutableStateFlow<List<EvidenceFolder>>(emptyList())
    val folders: StateFlow<List<EvidenceFolder>> = _folders.asStateFlow()

    private val _currentFolder = MutableStateFlow<EvidenceFolder?>(null)
    val currentFolder: StateFlow<EvidenceFolder?> = _currentFolder.asStateFlow()

    private val _evidenceList = MutableStateFlow<List<Evidence>>(emptyList())
    val evidenceList: StateFlow<List<Evidence>> = _evidenceList.asStateFlow()

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _flashMode = MutableStateFlow(ImageCapture.FLASH_MODE_OFF)
    val flashMode: StateFlow<Int> = _flashMode.asStateFlow()

    private var evidenceJob: Job? = null
    private var folderJob: Job? = null

    init {
        viewModelScope.launch {
            evidenceRepository.getAllProjects().collect {
                _projects.value = it
                // Keep current project in sync if it was updated (e.g. settings changed)
                _currentProject.value?.let { current ->
                    it.find { p -> p.id == current.id }?.let { updated ->
                        _currentProject.value = updated
                    }
                }
            }
        }

        viewModelScope.launch {
            combine(_currentProject, _currentFolder) { project, folder ->
                project to folder
            }.collect { (project, folder) ->
                evidenceJob?.cancel()
                folderJob?.cancel()

                if (project != null) {
                    folderJob = viewModelScope.launch {
                        evidenceRepository.getFolders(project.id, folder?.id).collect {
                            _folders.value = it
                        }
                    }

                    evidenceJob = viewModelScope.launch {
                        if (folder != null) {
                            evidenceRepository.getEvidenceForFolder(folder.id).collect {
                                _evidenceList.value = it
                            }
                        } else {
                            evidenceRepository.getEvidenceForProject(project.id).map { list ->
                                list.filter { it.folderId == null }
                            }.collect {
                                _evidenceList.value = it
                            }
                        }
                    }
                } else {
                    _folders.value = emptyList()
                    _evidenceList.value = emptyList()
                }
            }
        }
    }

    fun selectProject(project: EvidenceProject?) {
        _currentProject.value = project
    }

    fun createProject(name: String, description: String = "") {
        viewModelScope.launch {
            val project = EvidenceProject(
                id = UUID.randomUUID().toString(),
                name = name,
                description = description
            )
            evidenceRepository.createProject(project)
            _currentProject.value = project
        }
    }

    fun updateProjectSettings(showGps: Boolean, showDate: Boolean, showTimestamp: Boolean) {
        val project = _currentProject.value ?: return
        viewModelScope.launch {
            val updated = project.copy(
                showGps = showGps,
                showDate = showDate,
                showTimestamp = showTimestamp
            )
            evidenceRepository.updateProject(updated)
            _currentProject.value = updated
        }
    }

    fun selectFolder(folder: EvidenceFolder?) {
        _currentFolder.value = folder
    }

    fun navigateUp() {
        val current = _currentFolder.value ?: return
        viewModelScope.launch {
            if (current.parentFolderId == null) {
                _currentFolder.value = null
            } else {
                _currentFolder.value = evidenceRepository.getFolderById(current.parentFolderId)
            }
        }
    }

    fun createFolder(name: String) {
        val project = _currentProject.value ?: return
        val parentFolder = _currentFolder.value
        viewModelScope.launch {
            val folder = EvidenceFolder(
                id = UUID.randomUUID().toString(),
                projectId = project.id,
                parentFolderId = parentFolder?.id,
                name = name
            )
            evidenceRepository.createFolder(folder)
            _currentFolder.value = folder
        }
    }

    fun toggleFlash() {
        _flashMode.value = when (_flashMode.value) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
            else -> ImageCapture.FLASH_MODE_OFF
        }
    }

    @SuppressLint("MissingPermission")
    fun captureEvidence(context: Context, imageProxy: ImageProxy, notes: String = "", controlId: String? = null) {
        if (_isCapturing.value) return
        _isCapturing.value = true

        val project = _currentProject.value
        val projectId = project?.id
        val folderId = _currentFolder.value?.id
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val plane = imageProxy.planes[0]
                val buffer = plane.buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                val originalBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

                val matrix = Matrix()
                matrix.postRotate(rotationDegrees.toFloat())
                val uprightBitmap = Bitmap.createBitmap(
                    originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true
                )
                
                if (originalBitmap != uprightBitmap) {
                    originalBitmap.recycle()
                }
                
                imageProxy.close()

                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                val location: Location? = try {
                    Tasks.await(fusedLocationClient.lastLocation, 5, TimeUnit.SECONDS)
                } catch (e: Exception) {
                    Log.e("EvidenceCapture", "Location fetch failed", e)
                    null
                }

                val timestamp = Date()
                
                // Add Watermark based on project settings
                val watermarkedBitmap = if (project != null) {
                    addWatermark(
                        uprightBitmap, 
                        timestamp, 
                        location,
                        project.showGps,
                        project.showDate,
                        project.showTimestamp
                    )
                } else {
                    addWatermark(uprightBitmap, timestamp, location) // Default
                }
                
                if (uprightBitmap != watermarkedBitmap) {
                    uprightBitmap.recycle()
                }
                
                val fileName = "evidence_${System.currentTimeMillis()}.jpg"
                val file = File(context.filesDir, fileName)
                
                FileOutputStream(file).use { out ->
                    watermarkedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                watermarkedBitmap.recycle()

                val evidence = Evidence(
                    id = UUID.randomUUID().toString(),
                    filePath = file.absolutePath,
                    timestamp = timestamp,
                    latitude = location?.latitude,
                    longitude = location?.longitude,
                    controlId = controlId,
                    projectId = projectId,
                    folderId = folderId,
                    notes = notes
                )

                evidenceRepository.saveEvidence(evidence)
                logRepository.saveLog(
                    type = "COMPLIANCE",
                    summary = "Evidence captured (Folder: ${folderId ?: "None"})",
                    detailJson = "{\"id\":\"${evidence.id}\", \"path\":\"${evidence.filePath}\", \"notes\":\"$notes\"}"
                )
            } catch (e: Exception) {
                Log.e("EvidenceCapture", "Evidence capture failed", e)
                logRepository.saveLog(
                    type = "ERROR",
                    summary = "Failed to capture evidence",
                    detailJson = "{\"error\":\"${e.message}\"}"
                )
            } finally {
                _isCapturing.value = false
            }
        }
    }

    private fun addWatermark(
        bitmap: Bitmap, 
        date: Date, 
        location: Location?,
        showGps: Boolean = true,
        showDate: Boolean = true,
        showTimestamp: Boolean = true
    ): Bitmap {
        if (!showGps && !showDate && !showTimestamp) return bitmap

        val result = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        
        val paint = Paint().apply {
            color = Color.WHITE
            textSize = bitmap.height / 40f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setShadowLayer(2f, 1f, 1f, Color.BLACK)
        }

        val lines = mutableListOf<String>()
        
        if (showGps) {
            val locationText = location?.let { 
                "GPS: ${String.format(Locale.US, "%.6f", it.latitude)}, ${String.format(Locale.US, "%.6f", it.longitude)}" 
            } ?: "GPS: Unavailable"
            lines.add(locationText)
        }
        
        val datePattern = when {
            showDate && showTimestamp -> "yyyy-MM-dd HH:mm:ss"
            showDate -> "yyyy-MM-dd"
            showTimestamp -> "HH:mm:ss"
            else -> ""
        }
        
        if (datePattern.isNotEmpty()) {
            val sdf = SimpleDateFormat(datePattern, Locale.getDefault())
            lines.add(sdf.format(date))
        }

        val x = 20f
        var y = bitmap.height - 40f
        
        lines.reversed().forEach { line ->
            canvas.drawText(line, x, y, paint)
            y -= paint.textSize + 10f
        }
        
        return result
    }

    fun deleteEvidence(evidence: Evidence) {
        viewModelScope.launch {
            evidenceRepository.deleteEvidence(evidence)
        }
    }

    fun deleteFolder(folderId: String) {
        viewModelScope.launch {
            // Need to get the folder object or just use softDeleteFolder(folderId) if it was available
            // Looking at EvidenceRepository, it has suspend fun softDeleteFolder(folder: EvidenceFolder)
            // I should probably add a simpler one or just get it first.
            // Actually, I'll just use softDeleteFolder if I can get the folder.
            // Or I can just call evidenceRepository.softDeleteFolder(folder) if I had it.
            // Let's assume I need to get it or the repository can handle it.
            // Looking at Repository, it has deleteFolder(folder: EvidenceFolder)
            // I'll use a simplified version for batch delete if needed, but for now I'll just find it.
            _folders.value.find { it.id == folderId }?.let { folder ->
                evidenceRepository.deleteFolder(folder)
            }
        }
    }

    fun deleteProject(project: EvidenceProject) {
        viewModelScope.launch {
            evidenceRepository.deleteProject(project)
        }
    }

    fun moveEvidence(evidenceId: String, targetProjectId: String?, targetFolderId: String?) {
        viewModelScope.launch {
            evidenceRepository.moveEvidence(evidenceId, targetProjectId, targetFolderId)
        }
    }

    fun copyEvidence(evidenceId: String, targetProjectId: String?, targetFolderId: String?) {
        viewModelScope.launch {
            evidenceRepository.copyEvidence(evidenceId, targetProjectId, targetFolderId)
        }
    }

    fun moveFolder(folderId: String, targetProjectId: String?, targetFolderId: String?) {
        viewModelScope.launch {
            evidenceRepository.moveFolder(folderId, targetProjectId, targetFolderId)
        }
    }

    fun copyFolder(folderId: String, targetProjectId: String?, targetFolderId: String?) {
        viewModelScope.launch {
            evidenceRepository.copyFolder(folderId, targetProjectId, targetFolderId)
        }
    }

    fun getFoldersForProject(projectId: String): Flow<List<EvidenceFolder>> {
        return evidenceRepository.getFoldersForProject(projectId)
    }

    fun resetProjectSelection() {
        _currentProject.value = null
        _currentFolder.value = null
    }

    fun importEvidence(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        
        val project = _currentProject.value
        val projectId = project?.id
        val folderId = _currentFolder.value?.id
        
        viewModelScope.launch(Dispatchers.IO) {
            uris.forEach { uri ->
                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val fileName = "evidence_import_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg"
                        val file = File(context.filesDir, fileName)
                        
                        FileOutputStream(file).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }

                        val timestamp = Date()
                        val evidence = Evidence(
                            id = UUID.randomUUID().toString(),
                            filePath = file.absolutePath,
                            timestamp = timestamp,
                            latitude = null,
                            longitude = null,
                            projectId = projectId,
                            folderId = folderId
                        )

                        evidenceRepository.saveEvidence(evidence)
                        logRepository.saveLog(
                            type = "COMPLIANCE",
                            summary = "Evidence imported from gallery",
                            detailJson = "{\"id\":\"${evidence.id}\", \"path\":\"${evidence.filePath}\"}"
                        )
                    }
                } catch (e: Exception) {
                    Log.e("EvidenceCapture", "Failed to import image: $uri", e)
                }
            }
        }
    }

    fun hideData(evidence: Evidence, message: String, passphrase: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                evidenceRepository.hideData(evidence, message, passphrase.toCharArray())
                logRepository.saveLog(
                    type = "COMPLIANCE",
                    summary = "Data hidden in evidence (Vault)",
                    detailJson = "{\"id\":\"${evidence.id}\"}"
                )
            } catch (e: Exception) {
                Log.e("EvidenceCapture", "Stego encoding failed", e)
            }
        }
    }

    suspend fun revealData(evidence: Evidence, passphrase: String): Result<String> {
        return withContext(Dispatchers.IO) {
            evidenceRepository.revealData(evidence, passphrase.toCharArray())
        }
    }
}
