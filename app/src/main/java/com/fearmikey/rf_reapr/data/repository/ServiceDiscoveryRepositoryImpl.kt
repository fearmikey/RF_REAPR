package com.fearmikey.rf_reapr.data.repository

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.fearmikey.rf_reapr.domain.repository.DiscoveredService
import com.fearmikey.rf_reapr.domain.repository.ServiceDiscoveryRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Collections

class ServiceDiscoveryRepositoryImpl(context: Context) : ServiceDiscoveryRepository {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val services = Collections.synchronizedList(mutableListOf<DiscoveredService>())
    
    // Common service types to scan for
    private val serviceTypes = listOf(
        "_http._tcp.",
        "_https._tcp.",
        "_printer._tcp.",
        "_ipp._tcp.",
        "_ssh._tcp.",
        "_googlecast._tcp.",
        "_spotify-connect._tcp.",
        "_airplay._tcp.",
        "_raop._tcp."
    )

    override fun startDiscovery(): Flow<List<DiscoveredService>> = callbackFlow {
        services.clear()
        
        val listeners = serviceTypes.map { type ->
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {
                    Log.d("NSD", "Service discovery started: $regType")
                }

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    Log.d("NSD", "Service found: ${serviceInfo.serviceName}")
                    @Suppress("DEPRECATION")
                    nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            Log.e("NSD", "Resolve failed: $errorCode")
                        }

                        override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                            val hostAddress = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                resolvedInfo.hostAddresses.firstOrNull()?.hostAddress
                            } else {
                                @Suppress("DEPRECATION")
                                resolvedInfo.host?.hostAddress
                            }

                            val service = DiscoveredService(
                                name = resolvedInfo.serviceName,
                                type = resolvedInfo.serviceType,
                                host = hostAddress,
                                port = resolvedInfo.port,
                                attributes = resolvedInfo.attributes.mapValues { String(it.value) }
                            )
                            synchronized(services) {
                                if (services.none { it.name == service.name && it.type == service.type }) {
                                    services.add(service)
                                    trySend(services.toList())
                                }
                            }
                        }
                    })
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    synchronized(services) {
                        services.removeAll { it.name == serviceInfo.serviceName }
                        trySend(services.toList())
                    }
                }

                override fun onDiscoveryStopped(regType: String) {}
                override fun onStartDiscoveryFailed(regType: String, errorCode: Int) {
                    nsdManager.stopServiceDiscovery(this)
                }
                override fun onStopDiscoveryFailed(regType: String, errorCode: Int) {
                    nsdManager.stopServiceDiscovery(this)
                }
            }
            nsdManager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
            listener
        }

        awaitClose {
            listeners.forEach { nsdManager.stopServiceDiscovery(it) }
        }
    }

    override fun stopDiscovery() {
        // Handled by awaitClose in flow
    }
}
