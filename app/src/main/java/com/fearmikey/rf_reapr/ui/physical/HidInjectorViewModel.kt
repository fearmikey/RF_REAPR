package com.fearmikey.rf_reapr.ui.physical

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.model.HidPayload
import com.fearmikey.rf_reapr.domain.repository.HidAssetRepository
import com.fearmikey.rf_reapr.domain.repository.HidRepository
import com.fearmikey.rf_reapr.domain.service.HidScriptParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

class HidInjectorViewModel(
    private val repository: HidRepository,
    private val assetRepository: HidAssetRepository,
    private val parser: HidScriptParser
) : ViewModel() {

    private val _scripts = MutableStateFlow<List<HidPayload>>(emptyList())
    val scripts: StateFlow<List<HidPayload>> = _scripts.asStateFlow()

    private val _currentScript = MutableStateFlow("")
    val currentScript: StateFlow<String> = _currentScript.asStateFlow()

    private val _scriptName = MutableStateFlow("New Script")
    val scriptName: StateFlow<String> = _scriptName.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllScripts().collect {
                _scripts.value = it
            }
        }
    }

    fun onScriptChange(newScript: String) {
        _currentScript.value = newScript
    }

    fun onNameChange(newName: String) {
        _scriptName.value = newName
    }

    fun saveScript() {
        viewModelScope.launch {
            val payload = HidPayload(
                name = _scriptName.value,
                script = _currentScript.value
            )
            repository.saveScript(payload)
            addLog("Script saved: ${_scriptName.value}")
        }
    }

    fun deleteScript(payload: HidPayload) {
        viewModelScope.launch {
            repository.deleteScript(payload)
            addLog("Script deleted: ${payload.name}")
        }
    }

    fun loadScript(payload: HidPayload) {
        _scriptName.value = payload.name
        _currentScript.value = payload.script
        addLog("Loaded script: ${payload.name}")
    }

    fun runScript() {
        if (_isExecuting.value) return

        viewModelScope.launch {
            _isExecuting.value = true
            addLog("Starting execution...")
            parser.parseAndExecute(
                script = _currentScript.value,
                onLog = { log -> addLog(log) },
                onRetrieve = { fileName ->
                    val asset = assetRepository.saveAsset(
                        name = fileName,
                        content = "Retrieved content for $fileName\nTimestamp: ${System.currentTimeMillis()}"
                    )
                    addLog("Asset saved to: ${asset.path}")
                }
            )
            addLog("Execution finished.")
            _isExecuting.value = false
        }
    }

    private fun addLog(message: String) {
        _logs.value = listOf(message) + _logs.value.take(49)
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun importScript(uri: Uri, context: Context) {
        viewModelScope.launch {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    val content = reader.readText()
                    
                    // Extract file name from URI
                    val fileName = getFileName(uri, context) ?: "Imported Script"
                    
                    _scriptName.value = fileName.substringBeforeLast(".")
                    _currentScript.value = content
                    addLog("Imported script: $fileName")
                }
            } catch (e: Exception) {
                addLog("Error importing script: ${e.message}")
            }
        }
    }

    private fun getFileName(uri: Uri, context: Context): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = it.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1 && cut != null) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }
}
