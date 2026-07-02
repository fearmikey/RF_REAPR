package com.example.rf_reapr.data.repository

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import com.example.rf_reapr.domain.model.BleDevice
import com.example.rf_reapr.domain.repository.BleScannerRepository
import com.example.rf_reapr.domain.scanner.NetworkScanner
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

class BleScannerRepositoryImpl(
    private val context: Context
) : BleScannerRepository {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    
    private val _discoveredDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    private val deviceMap = mutableMapOf<String, BleDevice>()

    override fun getDiscoveredDevices(): Flow<List<BleDevice>> = _discoveredDevices.asStateFlow()

    @SuppressLint("MissingPermission")
    override fun startScan(): Flow<NetworkScanner.ScanResult<BleDevice>> = callbackFlow {
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            trySend(NetworkScanner.ScanResult.Error("BLE Scanner not available. Ensure Bluetooth is enabled."))
            close()
            return@callbackFlow
        }

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = BleDevice(
                    name = result.device.name,
                    address = result.device.address,
                    rssi = result.rssi,
                    serviceUuids = result.scanRecord?.serviceUuids?.map { it.toString() } ?: emptyList()
                )
                
                deviceMap[device.address] = device
                _discoveredDevices.value = deviceMap.values.toList().sortedByDescending { it.rssi }
                
                trySend(NetworkScanner.ScanResult.Progress(0f, deviceMap.values.toList()))
            }

            override fun onScanFailed(errorCode: Int) {
                trySend(NetworkScanner.ScanResult.Error("BLE Scan failed with error code: $errorCode"))
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(null, settings, scanCallback)
        trySend(NetworkScanner.ScanResult.Progress(0f, emptyList()))

        awaitClose {
            scanner.stopScan(scanCallback)
        }
    }

    override fun stopScan() {
        // Handled by Flow cancellation in startScan's awaitClose
    }
}
