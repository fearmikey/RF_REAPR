package com.fearmikey.rf_reapr.data.scanner

import com.fearmikey.rf_reapr.data.repository.PortScannerRepositoryImpl
import com.fearmikey.rf_reapr.domain.NetworkModule
import com.fearmikey.rf_reapr.domain.scanner.NetworkScanner

class TcpPortScannerModule : NetworkModule {
    override val id: String = "tcp_port_scanner"
    override val name: String = "TCP Port Scanner"
    override val description: String = "Scans a range of TCP ports for a given IP address to identify open services."
    override val permissionsRequired: List<String> = listOf(
        android.Manifest.permission.INTERNET,
        android.Manifest.permission.ACCESS_NETWORK_STATE
    )

    private val repository = PortScannerRepositoryImpl()

    override fun getScanner(): NetworkScanner<*> = repository
    
    // Helper to get typed repository for configuration
    @Suppress("unused")
    fun getRepository(): PortScannerRepositoryImpl = repository
}
