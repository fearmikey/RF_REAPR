package com.fearmikey.rf_reapr.domain.scanner

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Base implementation of [NetworkScanner] to handle common coroutine logic.
 */
abstract class BaseScanner<T> : NetworkScanner<T> {
    
    @Suppress("unused")
    protected val scannerScope = CoroutineScope(Dispatchers.IO + Job())
    
    private val _scanResult = MutableStateFlow<NetworkScanner.ScanResult<T>>(NetworkScanner.ScanResult.Idle)
    val scanResult = _scanResult.asStateFlow()

    protected fun emitResult(result: NetworkScanner.ScanResult<T>) {
        _scanResult.value = result
    }

    override fun stopScan() {
        // Implementation for stopping the scan job if any
    }
}
