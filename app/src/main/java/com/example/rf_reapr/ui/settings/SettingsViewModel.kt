package com.example.rf_reapr.ui.settings

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rf_reapr.domain.model.ThemePreference
import com.example.rf_reapr.domain.repository.LogRepository
import com.example.rf_reapr.domain.repository.SettingsRepository
import com.google.gson.GsonBuilder
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val logRepository: LogRepository
) : ViewModel() {

    private val _exportStatus = MutableSharedFlow<String>()
    val exportStatus: SharedFlow<String> = _exportStatus

    val themePreference: StateFlow<ThemePreference> = repository.themePreference
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ThemePreference.SYSTEM
        )

    fun setThemePreference(preference: ThemePreference) {
        viewModelScope.launch {
            repository.setThemePreference(preference)
        }
    }

    fun exportLogs(context: Context) {
        viewModelScope.launch {
            try {
                val logs = logRepository.getAllLogs().first()
                if (logs.isEmpty()) {
                    _exportStatus.emit("No logs found to export.")
                    return@launch
                }

                val gson = GsonBuilder().setPrettyPrinting().create()
                val json = gson.toJson(logs)
                
                val fileName = "rf_reapr_logs_${System.currentTimeMillis()}.json"
                val file = File(context.cacheDir, fileName)
                file.writeText(json)

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooser = Intent.createChooser(intent, "Export Debug Logs")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
                
                _exportStatus.emit("Exporting logs...")
            } catch (e: Exception) {
                _exportStatus.emit("Failed to export logs: ${e.message}")
            }
        }
    }
}
