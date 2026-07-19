package com.fearmikey.rf_reapr.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.fearmikey.rf_reapr.domain.model.HidAsset

@Entity(tableName = "hid_assets")
data class HidAssetEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val path: String,
    val timestamp: Long,
    val size: Long
) {
    fun toDomain() = HidAsset(
        id = id,
        name = name,
        path = path,
        timestamp = timestamp,
        size = size
    )

    companion object {
        fun fromDomain(asset: HidAsset) = HidAssetEntity(
            id = asset.id,
            name = asset.name,
            path = asset.path,
            timestamp = asset.timestamp,
            size = asset.size
        )
    }
}
