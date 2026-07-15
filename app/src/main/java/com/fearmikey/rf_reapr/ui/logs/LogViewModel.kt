package com.fearmikey.rf_reapr.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.data.db.entity.EventLogEntity
import com.fearmikey.rf_reapr.domain.repository.LogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LogViewModel(
    private val repository: LogRepository
) : ViewModel() {

    private val _logs = MutableStateFlow<List<EventLogEntity>>(emptyList())
    val logs: StateFlow<List<EventLogEntity>> = _logs.asStateFlow()

    fun loadLogs(type: String) {
        viewModelScope.launch {
            repository.getLogs(type).collect {
                _logs.value = it
            }
        }
    }

    fun deleteLog(id: Long) {
        viewModelScope.launch {
            repository.deleteLog(id)
        }
    }

    fun clearLogs(type: String) {
        viewModelScope.launch {
            repository.clearLogs(type)
        }
    }
}
