package com.fearmikey.rf_reapr.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A device the user has marked as expected on a project's network (added in DB v18).
 * [mac] is normalized (upper-case, colon-separated). [projectId] is "" for the no-project list.
 */
@Entity(
    tableName = "known_devices",
    indices = [Index(value = ["projectId", "mac"], unique = true)]
)
data class KnownDeviceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(defaultValue = "")
    val projectId: String = "",
    val mac: String,
    val label: String,
    val lastIp: String? = null,
    val notes: String? = null,
    val addedAt: Long
)
