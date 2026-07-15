package com.fearmikey.rf_reapr.ui.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.rf_reapr.domain.scanner.NetworkScanner
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Base ViewModel for scanning modules.
 */
abstract class ScannerViewModel<T>(
    private val scanner: NetworkScanner<T>
) : ViewModel() {

    val scanResults: StateFlow<NetworkScanner.ScanResult<T>> = scanner.startScan()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = NetworkScanner.ScanResult.Idle
        )

    fun startScanning() {
        scanner.startScan()
    }

    fun stopScanning() {
        scanner.stopScan()
    }
}
