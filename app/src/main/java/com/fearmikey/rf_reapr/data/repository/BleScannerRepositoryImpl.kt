package com.fearmikey.rf_reapr.data.repository

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import com.fearmikey.rf_reapr.domain.model.BleDevice
import com.fearmikey.rf_reapr.domain.repository.BleScannerRepository
import com.fearmikey.rf_reapr.domain.scanner.NetworkScanner
import com.fearmikey.rf_reapr.domain.service.BleAdvertisementAnalyzer
import com.fearmikey.rf_reapr.domain.service.BleCategory
import com.fearmikey.rf_reapr.domain.service.DeviceIdentificationService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class BleScannerRepositoryImpl(
    context: Context,
) : BleScannerRepository {

    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private val _discoveredDevices = MutableStateFlow<List<BleDevice>>(emptyList())

    override fun getDiscoveredDevices(): Flow<List<BleDevice>> = _discoveredDevices.asStateFlow()

    override fun startScan(): Flow<NetworkScanner.ScanResult<BleDevice>> = startScan(active = false)

    @SuppressLint("MissingPermission")
    override fun startScan(active: Boolean): Flow<NetworkScanner.ScanResult<BleDevice>> = callbackFlow {
        // Written from the Bluetooth binder thread and read by the ticker coroutine.
        val deviceMap = ConcurrentHashMap<String, BleDevice>()
        val adapter = bluetoothAdapter
        if (adapter == null) {
            trySend(NetworkScanner.ScanResult.Error("This device has no Bluetooth adapter."))
            close()
            return@callbackFlow
        }
        if (!adapter.isEnabled) {
            trySend(NetworkScanner.ScanResult.Error("Bluetooth is off. Turn it on to scan."))
            close()
            return@callbackFlow
        }
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            trySend(NetworkScanner.ScanResult.Error("BLE scanner not available. Ensure Bluetooth is enabled."))
            close()
            return@callbackFlow
        }

        fun merge(address: String, update: (BleDevice?) -> BleDevice) {
            deviceMap.compute(address) { _, existing -> update(existing) }
        }

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val address = result.device.address
                val record = result.scanRecord
                val now = System.currentTimeMillis()
                val macManufacturer = DeviceIdentificationService.getManufacturer(address)

                val manufacturerData = buildMap {
                    record?.manufacturerSpecificData?.let { data ->
                        for (i in 0 until data.size()) put(data.keyAt(i), data.valueAt(i) ?: ByteArray(0))
                    }
                }
                val sigManufacturer = manufacturerData.keys.firstNotNullOfOrNull { DeviceIdentificationService.getBluetoothManufacturer(it) }
                val serviceUuids = record?.serviceUuids?.map { it.toString() }.orEmpty()
                val serviceData = record?.serviceData?.mapKeys { it.key.toString() }.orEmpty()
                val classification = BleAdvertisementAnalyzer.classify(manufacturerData, serviceUuids, serviceData.keys.toList(), serviceData)
                val txPower = record?.txPowerLevel?.takeIf { it in -100..20 }
                    ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) result.txPower.takeIf { it != ScanResult.TX_POWER_NOT_PRESENT } else null
                val connectable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) result.isConnectable else null

                merge(address) { existing ->
                    val finalManufacturer = sigManufacturer ?: macManufacturer ?: existing?.manufacturer
                    val category = if (classification.category != BleCategory.UNKNOWN) classification.category else existing?.category ?: BleCategory.UNKNOWN
                    val resolvedName = record?.deviceName
                        ?: runCatching { result.device.name }.getOrNull()
                        ?: existing?.name?.takeUnless { it.startsWith("Generic Device") || it.endsWith(" Device") }
                        ?: category.takeIf { it != BleCategory.UNKNOWN }?.label
                        ?: finalManufacturer?.let { "$it Device" }
                        ?: "Generic Device (${address.takeLast(5)})"
                    BleDevice(
                        name = resolvedName,
                        address = address,
                        rssi = result.rssi,
                        serviceUuids = serviceUuids.ifEmpty { existing?.serviceUuids.orEmpty() },
                        manufacturer = finalManufacturer,
                        timestamp = now,
                        firstSeen = existing?.firstSeen ?: now,
                        lastSeen = now,
                        txPower = txPower ?: existing?.txPower,
                        connectable = connectable ?: existing?.connectable,
                        category = category,
                        categoryDetail = classification.categoryDetailOr(existing),
                        addressType = BleAdvertisementAnalyzer.addressType(address, macManufacturer != null),
                        packetCount = (existing?.packetCount ?: 0) + 1,
                        isClassic = existing?.isClassic ?: false
                    )
                }
            }

            override fun onScanFailed(errorCode: Int) {
                val reason = when (errorCode) {
                    SCAN_FAILED_ALREADY_STARTED -> "a scan is already running"
                    SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "the app couldn't register (toggle Bluetooth and retry)"
                    SCAN_FAILED_FEATURE_UNSUPPORTED -> "this scan mode isn't supported"
                    SCAN_FAILED_INTERNAL_ERROR -> "internal Bluetooth error"
                    else -> "error $errorCode (scanning too often? Android limits apps to 5 scans per 30 s)"
                }
                trySend(NetworkScanner.ScanResult.Error("BLE scan failed: $reason"))
            }
        }

        // Classic Bluetooth discovery (only in active mode).
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != BluetoothDevice.ACTION_FOUND) return
                val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                val rssi: Short = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                val name: String? = intent.getStringExtra(BluetoothDevice.EXTRA_NAME)
                val address = device?.address ?: return
                val now = System.currentTimeMillis()
                val manufacturer = DeviceIdentificationService.getManufacturer(address)
                merge(address) { existing ->
                    (existing ?: BleDevice(name = null, address = address, rssi = -100, serviceUuids = emptyList(), firstSeen = now)).copy(
                        name = name ?: existing?.name ?: manufacturer?.let { m -> "$m Device" } ?: "Generic Device (${address.takeLast(5)})",
                        rssi = if (rssi != Short.MIN_VALUE) rssi.toInt() else (existing?.rssi ?: -100),
                        manufacturer = existing?.manufacturer ?: manufacturer,
                        timestamp = now,
                        lastSeen = now,
                        packetCount = (existing?.packetCount ?: 0) + 1,
                        isClassic = true
                    )
                }
            }
        }

        if (active) {
            ContextCompat.registerReceiver(appContext, receiver, IntentFilter(BluetoothDevice.ACTION_FOUND), ContextCompat.RECEIVER_EXPORTED)
            runCatching { adapter.startDiscovery() }
        }

        // Publishes a stable-ordered list. Re-sorts every 10 s so rows don't jump around.
        val tickerJob = launch {
            var lastSortTime = 0L
            var currentOrder = emptyList<String>()
            var lastDiscovery = System.currentTimeMillis()
            while (isActive) {
                val now = System.currentTimeMillis()
                // Age out devices not heard from for a while.
                deviceMap.entries.removeIf { now - it.value.lastSeen > STALE_MS }
                val all = deviceMap.values.toList()
                val ordered = if (now - lastSortTime >= SORT_INTERVAL_MS || currentOrder.isEmpty()) {
                    lastSortTime = now
                    all.sortedByDescending { it.rssi }
                } else {
                    val kept = currentOrder.mapNotNull { deviceMap[it] }
                    kept + all.filter { it.address !in currentOrder }.sortedByDescending { it.rssi }
                }
                currentOrder = ordered.map { it.address }
                _discoveredDevices.value = ordered
                trySend(NetworkScanner.ScanResult.Progress(0f, ordered))

                // Classic discovery stops by itself after ~12 s; restart it periodically.
                if (active && now - lastDiscovery > 15_000) {
                    lastDiscovery = now
                    runCatching { if (!adapter.isDiscovering) adapter.startDiscovery() }
                }
                delay(if (active) 1000 else 2000)
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(if (active) ScanSettings.SCAN_MODE_LOW_LATENCY else ScanSettings.SCAN_MODE_BALANCED)
            .build()

        try {
            scanner.startScan(null, settings, scanCallback)
        } catch (e: SecurityException) {
            trySend(NetworkScanner.ScanResult.Error("Bluetooth scan permission was denied."))
            close()
        }
        trySend(NetworkScanner.ScanResult.Progress(0f, emptyList()))

        awaitClose {
            tickerJob.cancel()
            runCatching { scanner.stopScan(scanCallback) }
            if (active) {
                runCatching { adapter.cancelDiscovery() }
                runCatching { appContext.unregisterReceiver(receiver) }
            }
        }
    }

    override fun stopScan() {
        // Handled by Flow cancellation in startScan's awaitClose
    }

    private fun com.fearmikey.rf_reapr.domain.service.BleClassification.categoryDetailOr(existing: BleDevice?): String? =
        detail ?: existing?.categoryDetail

    private companion object {
        const val STALE_MS = 90_000L
        const val SORT_INTERVAL_MS = 10_000L
    }
}
