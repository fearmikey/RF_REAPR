package com.fearmikey.rf_reapr.data.repository

import android.content.Context
import com.fearmikey.rf_reapr.domain.model.WolTarget
import com.fearmikey.rf_reapr.domain.repository.WolRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Sends Wake-on-LAN packets and keeps a small list of saved devices in local
 * SharedPreferences (MACs and broadcast addresses only; nothing leaves the device).
 */
class WolRepositoryImpl(context: Context) : WolRepository {

    private val prefs = context.applicationContext.getSharedPreferences("wol_targets", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val listType = object : TypeToken<List<WolTarget>>() {}.type

    private val _savedTargets = MutableStateFlow(load())
    override val savedTargets: StateFlow<List<WolTarget>> = _savedTargets.asStateFlow()

    private fun load(): List<WolTarget> = runCatching {
        gson.fromJson<List<WolTarget>>(prefs.getString(KEY, null) ?: return emptyList(), listType)
    }.getOrNull().orEmpty()

    private fun persist(targets: List<WolTarget>) {
        prefs.edit().putString(KEY, gson.toJson(targets)).apply()
        _savedTargets.value = targets
    }

    override suspend fun sendMagicPacket(packet: ByteArray, broadcast: String, ports: List<Int>, repeat: Int): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                val address = InetAddress.getByName(broadcast)
                var sent = 0
                DatagramSocket().use { socket ->
                    socket.broadcast = true
                    repeat(repeat.coerceIn(1, 10)) { attempt ->
                        ports.distinct().forEach { port ->
                            socket.send(DatagramPacket(packet, packet.size, address, port))
                            sent++
                        }
                        // Small gap: some NICs/switches drop back-to-back duplicates.
                        if (attempt < repeat - 1) delay(100)
                    }
                }
                sent
            }
        }

    override suspend fun saveTarget(target: WolTarget) = withContext(Dispatchers.IO) {
        persist(_savedTargets.value.filterNot { it.id == target.id || it.mac.equals(target.mac, true) } + target)
    }

    override suspend fun deleteTarget(id: String) = withContext(Dispatchers.IO) {
        persist(_savedTargets.value.filterNot { it.id == id })
    }

    override suspend fun isHostUp(ip: String): Boolean = withContext(Dispatchers.IO) {
        val address = runCatching { InetAddress.getByName(ip) }.getOrNull() ?: return@withContext false
        // Windows hosts often block ICMP, so also try common service ports.
        for (port in listOf(445, 3389, 22, 80, 443, 139, 5357)) {
            try {
                Socket().use { it.connect(InetSocketAddress(address, port), 400) }
                return@withContext true
            } catch (_: Exception) {
            }
        }
        runCatching { address.isReachable(1000) }.getOrDefault(false)
    }

    private companion object {
        const val KEY = "targets_json"
    }
}
