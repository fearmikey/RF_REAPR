package com.fearmikey.rf_reapr.domain.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object ActiveTaskMonitor {
    private val _activeTasks = MutableStateFlow<Set<String>>(emptySet())
    val activeTasks = _activeTasks.asStateFlow()

    private val _stopAllSignal = MutableSharedFlow<Unit>(replay = 0)
    val stopAllSignal = _stopAllSignal.asSharedFlow()

    fun addTask(taskName: String) {
        _activeTasks.update { it + taskName }
    }

    fun removeTask(taskName: String) {
        _activeTasks.update { it - taskName }
    }

    suspend fun stopAll() {
        _stopAllSignal.emit(Unit)
        _activeTasks.value = emptySet()
    }

    fun hasActiveTasks(): Boolean = _activeTasks.value.isNotEmpty()
}
