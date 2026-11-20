package com.fearmikey.rf_reapr.ui.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.data.backup.BackupCrypto
import com.fearmikey.rf_reapr.data.backup.BackupManager
import com.fearmikey.rf_reapr.domain.service.ActiveTaskMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface BackupUiState {
    data object Idle : BackupUiState
    data class Working(val message: String) : BackupUiState
    data class Done(val message: String) : BackupUiState
    data class Failed(val message: String) : BackupUiState
    /** Restore finished; the app must restart to load the restored data. */
    data object RestartRequired : BackupUiState
}

class BackupViewModel(
    private val backupManager: BackupManager,
    private val appVersion: String
) : ViewModel() {

    private val _state = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    fun export(destination: Uri, password: String) {
        if (_state.value is BackupUiState.Working) return
        _state.value = BackupUiState.Working("Encrypting backup… (large evidence libraries take a while)")
        val task = "Encrypted backup"
        ActiveTaskMonitor.addTask(task)
        viewModelScope.launch {
            try {
                backupManager.export(destination, password.toCharArray(), appVersion)
                    .onSuccess { s ->
                        _state.value = BackupUiState.Done(
                            "Backup saved: database ${formatBytes(s.databaseBytes)}, ${s.fileCount} files, ${formatBytes(s.totalBytes)} total. " +
                                "Keep the password safe: without it the backup can't be opened."
                        )
                    }
                    .onFailure { _state.value = BackupUiState.Failed(it.message ?: "Backup failed") }
            } finally {
                ActiveTaskMonitor.removeTask(task)
            }
        }
    }

    fun restore(source: Uri, password: String) {
        if (_state.value is BackupUiState.Working) return
        _state.value = BackupUiState.Working("Decrypting and verifying backup…")
        viewModelScope.launch {
            backupManager.restore(source, password.toCharArray())
                .onSuccess { _state.value = BackupUiState.RestartRequired }
                .onFailure { e ->
                    _state.value = BackupUiState.Failed(
                        when (e) {
                            is BackupCrypto.WrongPasswordException -> "Wrong password, or the backup file is damaged."
                            is BackupCrypto.NotABackupException -> "That file isn't an RF-REAPR backup."
                            else -> e.message ?: "Restore failed"
                        }
                    )
                }
        }
    }

    fun dismiss() {
        if (_state.value !is BackupUiState.Working && _state.value !is BackupUiState.RestartRequired) _state.value = BackupUiState.Idle
    }

    private fun formatBytes(b: Long): String = when {
        b >= 1L shl 30 -> "%.1f GB".format(b / (1L shl 30).toDouble())
        b >= 1L shl 20 -> "%.1f MB".format(b / (1L shl 20).toDouble())
        b >= 1L shl 10 -> "%.0f KB".format(b / 1024.0)
        else -> "$b B"
    }
}
