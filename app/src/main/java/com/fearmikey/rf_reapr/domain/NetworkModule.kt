package com.fearmikey.rf_reapr.domain

import com.fearmikey.rf_reapr.domain.scanner.NetworkScanner

/**
 * Base definition for a security auditing module.
 */
interface NetworkModule {
    val id: String
    val name: String
    val description: String
    val permissionsRequired: List<String>

    /**
     * Provides the scanner implementation for this module.
     */
    fun getScanner(): NetworkScanner<*>
}
