package com.fearmikey.rf_reapr.domain.util

object VersionUtils {
    
    /**
     * Compares two version strings.
     * Returns:
     *   -1 if v1 < v2
     *    0 if v1 == v2
     *    1 if v1 > v2
     */
    fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split('.').mapNotNull { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split('.').mapNotNull { it.toIntOrNull() ?: 0 }
        
        val length = maxOf(parts1.size, parts2.size)
        for (i in 0 until length) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 < p2) return -1
            if (p1 > p2) return 1
        }
        return 0
    }

    fun isWithinRange(
        version: String,
        startIncluding: String? = null,
        startExcluding: String? = null,
        endIncluding: String? = null,
        endExcluding: String? = null
    ): Boolean {
        if (version.isBlank()) return false

        // Check start boundaries
        startIncluding?.let { if (compareVersions(version, it) < 0) return false }
        startExcluding?.let { if (compareVersions(version, it) <= 0) return false }

        // Check end boundaries
        endIncluding?.let { if (compareVersions(version, it) > 0) return false }
        endExcluding?.let { if (compareVersions(version, it) >= 0) return false }

        return true
    }
}
