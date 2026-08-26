package com.fearmikey.rf_reapr.domain.model

/**
 * Represents a removable USB storage volume ("flash drive") plugged into the
 * phone (typically via an OTG adapter) that the app can write DuckyScript
 * payloads to once the user has granted access to it.
 */
data class UsbDriveInfo(
    val id: String,
    val name: String,
    val isAttached: Boolean,
    val isAuthorized: Boolean,
    val treeUri: String? = null,
    val lastUsed: Long = 0
)
