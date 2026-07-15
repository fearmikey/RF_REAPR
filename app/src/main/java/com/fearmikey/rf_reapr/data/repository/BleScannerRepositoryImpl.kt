package com.fearmikey.rf_reapr.data.repository

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.os.Build
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.fearmikey.rf_reapr.domain.model.BleDevice
import com.fearmikey.rf_reapr.domain.repository.BleScannerRepository
import com.fearmikey.rf_reapr.domain.scanner.NetworkScanner
import com.fearmikey.rf_reapr.domain.service.DeviceIdentificationService
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

import kotlin.time.Duration.Companion.milliseconds

class BleScannerRepositoryImpl(
    context: Context,
) : BleScannerRepository {

    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    
    private val _discoveredDevices = MutableStateFlow<List<BleDevice>>(emptyList())

    override fun getDiscoveredDevices(): Flow<List<BleDevice>> = _discoveredDevices.asStateFlow()

    @SuppressLint("MissingPermission")
    override fun startScan(): Flow<NetworkScanner.ScanResult<BleDevice>> = startScan(active = false)

    @SuppressLint("MissingPermission")
    override fun startScan(active: Boolean): Flow<NetworkScanner.ScanResult<BleDevice>> = callbackFlow {
        val deviceMap = mutableMapOf<String, BleDevice>()
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            trySend(NetworkScanner.ScanResult.Error("BLE Scanner not available. Ensure Bluetooth is enabled."))
            close()
            return@callbackFlow
        }

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val existingDevice = deviceMap[result.device.address]
                val macManufacturer = DeviceIdentificationService.getManufacturer(result.device.address)
                
                // Extract Bluetooth SIG Manufacturer ID from raw scan record
                var sigManufacturer: String? = null
                result.scanRecord?.manufacturerSpecificData?.let { data ->
                    for (i in 0 until data.size()) {
                        val id = data.keyAt(i)
                        sigManufacturer = DeviceIdentificationService.getBluetoothManufacturer(id)
                        if (sigManufacturer != null) break
                    }
                }

                val finalManufacturer = sigManufacturer ?: macManufacturer
                
                // Prioritize Scan Record Name > Device Name > Existing Name > SIG/MAC Manufacturer > MAC Suffix
                val resolvedName = result.scanRecord?.deviceName 
                    ?: result.device.name 
                    ?: existingDevice?.name
                    ?: finalManufacturer?.let { "$it Device" }
                    ?: "Generic Device (${result.device.address.takeLast(5)})"

                val device = BleDevice(
                    name = resolvedName,
                    address = result.device.address,
                    rssi = result.rssi,
                    serviceUuids = result.scanRecord?.serviceUuids?.map { it.toString() } ?: existingDevice?.serviceUuids ?: emptyList(),
                    manufacturer = finalManufacturer
                )
                
                deviceMap[device.address] = device
            }

            override fun onScanFailed(errorCode: Int) {
                trySend(NetworkScanner.ScanResult.Error("BLE Scan failed with error code: $errorCode"))
            }
        }

        // Classic Bluetooth Discovery (only in active mode)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        val rssi: Short = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                        val name: String? = intent.getStringExtra(BluetoothDevice.EXTRA_NAME)
                        
                        device?.let {
                            val address = it.address
                            val existing = deviceMap[address]
                            val manufacturer = DeviceIdentificationService.getManufacturer(address)
                            
                            val resolvedName = name 
                                ?: existing?.name 
                                ?: manufacturer?.let { m -> "$m Device" }
                                ?: "Generic Device (${address.takeLast(5)})"

                            deviceMap[address] = BleDevice(
                                name = resolvedName,
                                address = address,
                                rssi = if (rssi != Short.MIN_VALUE) rssi.toInt() else (existing?.rssi ?: -100),
                                serviceUuids = existing?.serviceUuids ?: emptyList(),
                                manufacturer = manufacturer
                            )
                        }
                    }
                }
            }
        }

        if (active) {
            val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
            appContext.registerReceiver(receiver, filter)
            bluetoothAdapter?.startDiscovery()
        }

        // Periodic update job
        val tickerJob = launch {
            var lastSortTime = 0L
            val sortInterval = 10000L // Re-sort every 10 seconds
            var currentOrder = emptyList<String>()

            while (isActive) {
                val allDevices = deviceMap.values.toList()
                val now = System.currentTimeMillis()
                
                if ((now - lastSortTime >= sortInterval) || currentOrder.isEmpty()) {
                    // Perform a fresh sort
                    val sortedDevices = allDevices.sortedByDescending { it.rssi }
                    currentOrder = sortedDevices.map { it.address }
                    _discoveredDevices.value = sortedDevices
                    trySend(NetworkScanner.ScanResult.Progress(0f, sortedDevices))
                    lastSortTime = now
                } else {
                    // Keep the current order but update the values (RSSI, names, etc.)
                    val orderedDevices = currentOrder.mapNotNull { address ->
                        deviceMap[address]
                    }
                    // Also include any NEW devices that aren't in the current order yet (at the end)
                    val newDevices = allDevices.filter { it.address !in currentOrder }
                        .sortedByDescending { it.rssi }
                    
                    val combined = orderedDevices + newDevices
                    // Update current order to include new devices for next time
                    currentOrder = combined.map { it.address }
                    
                    _discoveredDevices.value = combined
                    trySend(NetworkScanner.ScanResult.Progress(0f, combined))
                }

                delay(if (active) 1000.milliseconds else 5000.milliseconds)
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(if (active) ScanSettings.SCAN_MODE_LOW_LATENCY else ScanSettings.SCAN_MODE_LOW_POWER)
            .build()

        scanner.startScan(null, settings, scanCallback)
        trySend(NetworkScanner.ScanResult.Progress(0f, emptyList()))

        awaitClose {
            tickerJob.cancel()
            scanner.stopScan(scanCallback)
            if (active) {
                bluetoothAdapter?.cancelDiscovery()
                try {
                    appContext.unregisterReceiver(receiver)
                } catch (e: Exception) {
                    // Ignore if not registered
                }
            }
            _discoveredDevices.value = emptyList()
        }
    }

    override fun stopScan() {
        // Handled by Flow cancellation in startScan's awaitClose
    }
}
