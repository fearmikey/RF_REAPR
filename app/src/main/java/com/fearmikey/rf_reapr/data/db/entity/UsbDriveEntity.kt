package com.fearmikey.rf_reapr.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A USB storage volume the user has previously granted the app persistent
 * SAF access to, so it can be re-flashed without re-prompting each time.
 */
@Entity(tableName = "usb_drives")
data class UsbDriveEntity(
    @PrimaryKey val id: String,
    val name: String,
    val treeUri: String,
    val lastUsed: Long
)
