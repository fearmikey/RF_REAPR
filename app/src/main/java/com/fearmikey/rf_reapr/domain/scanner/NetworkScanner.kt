package com.fearmikey.rf_reapr.domain.scanner

import kotlinx.coroutines.flow.Flow

interface NetworkScanner<T> {
    fun startScan(): Flow<ScanResult<T>>
    fun stopScan()

    sealed class ScanResult<out T> {
        data object Idle : ScanResult<Nothing>()
        data class Progress<T>(val progress: Float, val foundData: List<T>) : ScanResult<T>()
        data class Error(val message: String, val throwable: Throwable? = null) : ScanResult<Nothing>()
        data class Finished<T>(val foundData: List<T>) : ScanResult<T>()
    }
}
