package com.fearmikey.rf_reapr.ui.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.data.repository.DefaultCredentials
import com.fearmikey.rf_reapr.domain.repository.AuthProtocol
import com.fearmikey.rf_reapr.domain.repository.CredentialTesterRepository
import com.fearmikey.rf_reapr.domain.repository.TestResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class CredentialTesterViewModel(
    private val repository: CredentialTesterRepository
) : ViewModel() {

    private val _testResult = MutableStateFlow<TestResult?>(null)
    val testResult: StateFlow<TestResult?> = _testResult.asStateFlow()

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _progress = MutableStateFlow<TestResult.Progress?>(null)
    val progress: StateFlow<TestResult.Progress?> = _progress.asStateFlow()

    private var testJob: Job? = null

    fun runTest(target: String, protocol: AuthProtocol) {
        val commonCredentials = DefaultCredentials.common
        _logs.value = listOf("Starting $protocol test on $target...")
        _testResult.value = null
        _progress.value = null
        
        testJob?.cancel()
        testJob = viewModelScope.launch {
            _isTesting.value = true
            repository.testCredentials(target, protocol, commonCredentials).collect {
                _testResult.value = it
                when (it) {
                    is TestResult.Progress -> {
                        _progress.value = it
                        addLog("Testing ${it.current.username}:${it.current.password}...")
                    }
                    is TestResult.Success -> {
                        addLog("SUCCESS! Found credentials: ${it.credential.username}:${it.credential.password}")
                        _isTesting.value = false
                    }
                    is TestResult.PairFailed -> {
                        addLog("FAILED: ${it.pair.username}:${it.pair.password}")
                    }
                    is TestResult.Debug -> {
                        addLog("[DEBUG] ${it.message}")
                    }
                    is TestResult.Finished -> {
                        addLog("Test finished. No more credentials to test.")
                        _isTesting.value = false
                    }
                    is TestResult.Failure -> {
                        addLog("ERROR: ${it.message}")
                        _isTesting.value = false
                    }
                }
            }
        }
    }

    private fun addLog(message: String) {
        _logs.value = _logs.value + message
    }

    fun stopTest() {
        testJob?.cancel()
        testJob = null
        _isTesting.value = false
        addLog("Test cancelled by user.")
    }

    fun reset() {
        stopTest()
        _testResult.value = null
        _logs.value = emptyList()
        _progress.value = null
    }
}
