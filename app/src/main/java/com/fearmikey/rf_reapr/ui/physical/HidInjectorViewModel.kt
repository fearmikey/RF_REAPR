package com.fearmikey.rf_reapr.ui.physical

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.model.HidPayload
import com.fearmikey.rf_reapr.domain.repository.HidAssetRepository
import com.fearmikey.rf_reapr.domain.repository.HidRepository
import com.fearmikey.rf_reapr.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class HidInjectorViewModel(
    private val repository: HidRepository,
    private val assetRepository: HidAssetRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val isPassiveMode: StateFlow<Boolean> = settingsRepository.isPassiveMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _scripts = MutableStateFlow<List<HidPayload>>(emptyList())
    val scripts: StateFlow<List<HidPayload>> = _scripts.asStateFlow()

    private val _currentScript = MutableStateFlow("")
    val currentScript: StateFlow<String> = _currentScript.asStateFlow()

    private val _scriptName = MutableStateFlow("New Script")
    val scriptName: StateFlow<String> = _scriptName.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _isFlashing = MutableStateFlow(false)
    val isFlashing: StateFlow<Boolean> = _isFlashing.asStateFlow()

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

    fun flashScript(uri: Uri, context: Context) {
        if (_isFlashing.value || isPassiveMode.value) {
            if (isPassiveMode.value) addLog("Passive Mode enabled. HID injection inhibited.")
            return
        }
        
        viewModelScope.launch {
            _isFlashing.value = true
            addLog("Flashing script to: ${uri.path}")
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    BufferedWriter(OutputStreamWriter(outputStream)).use { writer ->
                        writer.write(_currentScript.value)
                    }
                    addLog("Flashing successful!")
                } ?: throw Exception("Could not open output stream")
            } catch (e: Exception) {
                addLog("Flashing failed: ${e.message}")
            } finally {
                _isFlashing.value = false
            }
        }
    }

    fun flashSavedScript(payload: HidPayload, uri: Uri, context: Context) {
        if (_isFlashing.value || isPassiveMode.value) {
            if (isPassiveMode.value) addLog("Passive Mode enabled. HID injection inhibited.")
            return
        }

        viewModelScope.launch {
            _isFlashing.value = true
            addLog("Flashing ${payload.name} to: ${uri.path}")
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    BufferedWriter(OutputStreamWriter(outputStream)).use { writer ->
                        writer.write(payload.script)
                    }
                    addLog("Flashing ${payload.name} successful!")
                } ?: throw Exception("Could not open output stream")
            } catch (e: Exception) {
                addLog("Flashing failed: ${e.message}")
            } finally {
                _isFlashing.value = false
            }
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
