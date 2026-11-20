package com.fearmikey.rf_reapr.data.repository

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import com.fearmikey.rf_reapr.domain.repository.DiscoveredService
import com.fearmikey.rf_reapr.domain.repository.ServiceDiscoveryRepository
import com.fearmikey.rf_reapr.domain.service.MdnsServiceCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * mDNS / DNS-SD browser built on [NsdManager].
 *
 * - Enumerates every advertised service type via the `_services._dns-sd._udp` meta-query
 *   (plus a bounded default list for stacks that don't answer it) and browses each type.
 * - Resolves services one at a time: before Android 14, NsdManager rejects concurrent
 *   resolves with FAILURE_ALREADY_ACTIVE, which previously dropped most services silently.
 * - Tears down listeners defensively; stopping a listener whose start failed throws.
 */
class ServiceDiscoveryRepositoryImpl(context: Context) : ServiceDiscoveryRepository {

    private val nsdManager = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager

    override fun startDiscovery(): Flow<List<DiscoveredService>> = callbackFlow {
        val lock = Any()
        val services = LinkedHashMap<String, DiscoveredService>()
        val browsedTypes = mutableSetOf<String>()
        val registeredListeners = mutableListOf<NsdManager.DiscoveryListener>()
        val resolveQueue = Channel<Pair<String, NsdServiceInfo>>(Channel.UNLIMITED)

        fun key(name: String, type: String) = "$name|$type"
        fun emitSnapshot() {
            trySend(synchronized(lock) { services.values.toList() })
        }

        fun browse(rawType: String) {
            val type = MdnsServiceCatalog.normalizeType(rawType)
            synchronized(lock) { if (!browsedTypes.add(type)) return }
            val isMetaQuery = type == MdnsServiceCatalog.META_QUERY_TYPE

            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {
                    Log.d(TAG, "Browsing $regType")
                }

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    if (isMetaQuery) {
                        MdnsServiceCatalog.typeFromMetaQueryResult(serviceInfo.serviceName, serviceInfo.serviceType)
                            ?.let { browse(it) }
                    } else {
                        resolveQueue.trySend(type to serviceInfo)
                    }
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    if (isMetaQuery) return
                    val removed = synchronized(lock) { services.remove(key(serviceInfo.serviceName, type)) != null }
                    if (removed) emitSnapshot()
                }

                override fun onDiscoveryStopped(regType: String) {}

                override fun onStartDiscoveryFailed(regType: String, errorCode: Int) {
                    // Don't call stopServiceDiscovery() here: the listener was never registered
                    // and stopping it throws IllegalArgumentException.
                    Log.w(TAG, "Browse failed for $regType: $errorCode")
                }

                override fun onStopDiscoveryFailed(regType: String, errorCode: Int) {
                    Log.w(TAG, "Stop failed for $regType: $errorCode")
                }
            }

            try {
                nsdManager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
                synchronized(lock) { registeredListeners += listener }
            } catch (e: Exception) {
                Log.w(TAG, "discoverServices($type) threw: ${e.message}")
            }
        }

        // Single consumer => at most one resolve in flight.
        val resolver = launch(Dispatchers.IO) {
            for ((type, info) in resolveQueue) {
                val resolved = resolve(info) ?: continue
                val service = resolved.toDiscoveredService(type)
                synchronized(lock) { services[key(service.name, type)] = service }
                emitSnapshot()
            }
        }

        trySend(emptyList())
        browse(MdnsServiceCatalog.META_QUERY_TYPE)
        MdnsServiceCatalog.defaultBrowseTypes.forEach { browse(it) }

        awaitClose {
            resolver.cancel()
            resolveQueue.close()
            val listeners = synchronized(lock) { registeredListeners.toList() }
            listeners.forEach { listener ->
                try {
                    nsdManager.stopServiceDiscovery(listener)
                } catch (_: IllegalArgumentException) {
                    // Already stopped or never started.
                }
            }
        }
    }

    override fun stopDiscovery() {
        // Browsing stops when the startDiscovery() flow collection is cancelled.
    }

    /** Resolves [info], retrying briefly if another resolve is still in flight. */
    private suspend fun resolve(info: NsdServiceInfo): NsdServiceInfo? {
        repeat(RESOLVE_ATTEMPTS) { attempt ->
            val outcome = withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
                suspendCancellableCoroutine<Pair<NsdServiceInfo?, Int>> { cont ->
                    try {
                        @Suppress("DEPRECATION")
                        nsdManager.resolveService(info, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                                if (cont.isActive) cont.resume(null to errorCode)
                            }

                            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                                if (cont.isActive) cont.resume(serviceInfo to 0)
                            }
                        })
                    } catch (_: Exception) {
                        if (cont.isActive) cont.resume(null to NsdManager.FAILURE_INTERNAL_ERROR)
                    }
                }
            } ?: return null // timed out

            val (resolved, errorCode) = outcome
            when {
                resolved != null -> return resolved
                errorCode == NsdManager.FAILURE_ALREADY_ACTIVE -> delay(RETRY_BACKOFF_MS * (attempt + 1))
                else -> {
                    Log.d(TAG, "Resolve failed for ${info.serviceName}: $errorCode")
                    return null
                }
            }
        }
        return null
    }

    private fun NsdServiceInfo.toDiscoveredService(browsedType: String): DiscoveredService {
        val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            hostAddresses.mapNotNull { it.hostAddress }
        } else {
            @Suppress("DEPRECATION")
            listOfNotNull(host?.hostAddress)
        }.map { it.substringBefore('%') }.distinct() // drop IPv6 scope ids

        val txt = try {
            // TXT keys without a value come back as null.
            attributes.entries.associate { (k, v) -> k to (v?.toString(Charsets.UTF_8) ?: "") }
        } catch (_: Exception) {
            emptyMap()
        }

        return DiscoveredService(
            name = serviceName,
            type = browsedType,
            host = addresses.firstOrNull { !it.contains(':') } ?: addresses.firstOrNull(),
            port = port,
            attributes = txt,
            addresses = addresses
        )
    }

    private companion object {
        const val TAG = "ServiceDiscovery"
        const val RESOLVE_ATTEMPTS = 4
        const val RESOLVE_TIMEOUT_MS = 6_000L
        const val RETRY_BACKOFF_MS = 250L
    }
}
