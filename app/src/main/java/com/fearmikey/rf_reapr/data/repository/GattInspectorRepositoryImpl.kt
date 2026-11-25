package com.fearmikey.rf_reapr.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.fearmikey.rf_reapr.domain.repository.GattInspection
import com.fearmikey.rf_reapr.domain.repository.GattInspectorRepository
import com.fearmikey.rf_reapr.domain.repository.GattPhase
import com.fearmikey.rf_reapr.domain.service.GattAnalyzer
import com.fearmikey.rf_reapr.domain.service.GattCatalog
import com.fearmikey.rf_reapr.domain.service.GattCharacteristicInfo
import com.fearmikey.rf_reapr.domain.service.GattServiceInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Read-only GATT enumeration: connect, list services and characteristics, read the standard
 * device-information values, disconnect. It never writes to the device, never enables
 * notifications and never initiates pairing.
 */
class GattInspectorRepositoryImpl(context: Context) : GattInspectorRepository {

    private val appContext = context.applicationContext

    private sealed interface Event {
        data class Connection(val status: Int, val newState: Int) : Event
        data class Discovered(val status: Int) : Event
        class Read(val uuid: String, val status: Int, val value: ByteArray?) : Event
    }

    /** The link dropped while waiting for a GATT callback. */
    private class DeviceDisconnected : Exception()

    @SuppressLint("MissingPermission")
    override fun inspect(address: String): Flow<GattInspection> = flow {
        var result = GattInspection(address = address, phase = GattPhase.CONNECTING)
        emit(result)

        val failure = precheck(address)
        if (failure != null) {
            emit(result.copy(phase = GattPhase.FAILED, error = failure))
            return@flow
        }
        val adapter = (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        val device = adapter.getRemoteDevice(address.uppercase())
        val bonded = device.bondState == BluetoothDevice.BOND_BONDED
        result = result.copy(name = runCatching { device.name }.getOrNull(), bonded = bonded)

        val events = Channel<Event>(Channel.UNLIMITED)
        val closed = CompletableDeferred<Unit>()
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                events.trySend(Event.Connection(status, newState))
                if (newState == BluetoothProfile.STATE_DISCONNECTED) closed.complete(Unit)
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                events.trySend(Event.Discovered(status))
            }

            // API 33+ delivers the value as a parameter...
            override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
                events.trySend(Event.Read(characteristic.uuid.toString(), status, value))
            }

            // ...older releases through the (now deprecated) characteristic.value.
            @Deprecated("Deprecated in Java")
            @Suppress("DEPRECATION")
            override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    events.trySend(Event.Read(characteristic.uuid.toString(), status, characteristic.value?.copyOf()))
                }
            }
        }

        val gatt = device.connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_LE)
        if (gatt == null) {
            emit(result.copy(phase = GattPhase.FAILED, error = "The Bluetooth stack refused the connection"))
            return@flow
        }
        try {
            // 1. Connect
            val connected = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
                var state: Event.Connection
                do {
                    state = nextOf<Event.Connection>(events)
                } while (state.newState != BluetoothProfile.STATE_CONNECTED && state.newState != BluetoothProfile.STATE_DISCONNECTED)
                state
            }
            if (connected == null || connected.newState != BluetoothProfile.STATE_CONNECTED) {
                emit(
                    result.copy(
                        phase = GattPhase.FAILED,
                        error = if (connected == null) "Timed out connecting. The device may be out of range or not connectable."
                        else "Connection failed (status ${connected.status}). The device may not accept connections."
                    )
                )
                return@flow
            }

            // 2. Discover
            result = result.copy(phase = GattPhase.DISCOVERING)
            emit(result)
            val discovered = if (gatt.discoverServices()) withTimeoutOrNull(DISCOVERY_TIMEOUT_MS) { nextOf<Event.Discovered>(events) } else null
            if (discovered == null || discovered.status != BluetoothGatt.GATT_SUCCESS) {
                emit(result.copy(phase = GattPhase.FAILED, error = "Service discovery failed" + (discovered?.let { " (status ${it.status})" } ?: "")))
                return@flow
            }
            var services = gatt.services.map { service ->
                GattServiceInfo(
                    uuid = service.uuid.toString(),
                    characteristics = service.characteristics.map { GattCharacteristicInfo(it.uuid.toString(), it.properties) }
                )
            }
            result = result.copy(phase = GattPhase.READING, services = services)
            emit(result)

            // 3. Read standard information characteristics only (names, versions, battery).
            val readable = gatt.services
                .filter { GattCatalog.shortUuid(it.uuid.toString()) in GattCatalog.INFO_SERVICES }
                .flatMap { it.characteristics }
                .filter { it.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0 }
            val values = mutableMapOf<String, ByteArray>()
            for (characteristic in readable) {
                if (!gatt.readCharacteristic(characteristic)) continue
                val read = withTimeoutOrNull(READ_TIMEOUT_MS) { nextOf<Event.Read>(events) } ?: break
                if (read.status == BluetoothGatt.GATT_SUCCESS && read.value != null) values[read.uuid] = read.value
            }
            services = services.map { s ->
                s.copy(characteristics = s.characteristics.map { c -> values[c.uuid]?.let { c.copy(value = it) } ?: c })
            }
            emit(
                result.copy(
                    phase = GattPhase.DONE,
                    services = services,
                    findings = GattAnalyzer.findings(services, bonded)
                )
            )
        } catch (_: DeviceDisconnected) {
            emit(result.copy(phase = GattPhase.FAILED, error = "The device disconnected before the inspection finished"))
        } finally {
            withContext(NonCancellable) {
                runCatching { gatt.disconnect() }
                withTimeoutOrNull(1_500) { closed.await() }
                runCatching { gatt.close() }
            }
        }
    }

    /** Skips events of other kinds; a disconnect ends the wait with an exception. */
    private suspend inline fun <reified T : Event> nextOf(events: Channel<Event>): T {
        while (true) {
            val event = events.receive()
            if (event is T) return event
            if (event is Event.Connection && event.newState == BluetoothProfile.STATE_DISCONNECTED) {
                throw DeviceDisconnected()
            }
        }
    }

    private fun precheck(address: String): String? {
        if (!BluetoothAdapter.checkBluetoothAddress(address.uppercase())) return "Not a valid Bluetooth address"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            return "The Nearby devices (Bluetooth connect) permission is required"
        }
        val adapter = (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            ?: return "This device has no Bluetooth"
        return if (adapter.isEnabled) null else "Turn Bluetooth on first"
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 12_000L
        const val DISCOVERY_TIMEOUT_MS = 10_000L
        const val READ_TIMEOUT_MS = 3_000L
    }
}
