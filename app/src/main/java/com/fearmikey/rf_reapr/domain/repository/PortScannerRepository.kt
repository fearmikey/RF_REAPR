package com.fearmikey.rf_reapr.domain.repository

import com.fearmikey.rf_reapr.domain.model.OpenPort
import com.fearmikey.rf_reapr.domain.scanner.NetworkScanner

interface PortScannerRepository : NetworkScanner<OpenPort> {
    fun setConfig(ipAddress: String, portRange: IntRange)
    fun setConfig(ipAddress: String, ports: List<Int>)
}
