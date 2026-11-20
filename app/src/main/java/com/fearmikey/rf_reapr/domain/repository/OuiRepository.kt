package com.fearmikey.rf_reapr.domain.repository

/**
 * Offline MAC OUI -> vendor database.
 */
interface OuiRepository {
    /** Looks up the vendor for a 6-hex-digit OUI (case-insensitive), or null if unknown. */
    suspend fun lookupVendor(oui: String): String?

    /** Number of OUI entries available offline. */
    suspend fun entryCount(): Int
}
