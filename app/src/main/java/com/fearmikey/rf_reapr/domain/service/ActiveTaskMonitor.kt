package com.fearmikey.rf_reapr.domain.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object ActiveTaskMonitor {
    private val _activeTasks = MutableStateFlow<Set<String>>(emptySet())
    val activeTasks = _activeTasks.asStateFlow()

    // Optional short human-readable progress line (e.g. "Network Discovery: 45%") that a
    // running task can push so the background notification shows live status instead of
    // just a static list of task names.
    private val _statusText = MutableStateFlow<String?>(null)
    val statusText = _statusText.asStateFlow()

    private val _stopAllSignal = MutableSharedFlow<Unit>(replay = 0)
    val stopAllSignal = _stopAllSignal.asSharedFlow()

    fun addTask(taskName: String) {
        _activeTasks.update { it + taskName }
    }

    fun removeTask(taskName: String) {
        _activeTasks.update { it - taskName }
        if (_activeTasks.value.isEmpty()) {
            _statusText.value = null
        }
    }

    fun updateStatus(text: String?) {
        _statusText.value = text
    }

    suspend fun stopAll() {
        _stopAllSignal.emit(Unit)
        _activeTasks.value = emptySet()
        _statusText.value = null
    }

    fun hasActiveTasks(): Boolean = _activeTasks.value.isNotEmpty()
}
