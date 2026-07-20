package com.fearmikey.rf_reapr.ui.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.repository.AuthProtocol
import com.fearmikey.rf_reapr.domain.repository.CredentialPair
import com.fearmikey.rf_reapr.domain.repository.CredentialTesterRepository
import com.fearmikey.rf_reapr.domain.repository.TestResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CredentialTesterViewModel(
    private val repository: CredentialTesterRepository
) : ViewModel() {

    private val _testResult = MutableStateFlow<TestResult?>(null)
    val testResult: StateFlow<TestResult?> = _testResult.asStateFlow()

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    fun runTest(target: String, protocol: AuthProtocol) {
        val commonCredentials = listOf(
            CredentialPair("admin", "admin"),
            CredentialPair("admin", "password"),
            CredentialPair("root", "root"),
            CredentialPair("root", "1234"),
            CredentialPair("user", "user"),
            CredentialPair("guest", "guest")
        )
        
        viewModelScope.launch {
            _isTesting.value = true
            repository.testCredentials(target, protocol, commonCredentials).collect {
                _testResult.value = it
                if (it is TestResult.Finished || it is TestResult.Success) {
                    _isTesting.value = false
                }
            }
        }
    }

    fun reset() {
        _testResult.value = null
        _isTesting.value = false
    }
}
