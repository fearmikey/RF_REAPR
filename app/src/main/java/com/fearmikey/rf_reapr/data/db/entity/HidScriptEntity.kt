package com.fearmikey.rf_reapr.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.fearmikey.rf_reapr.domain.model.HidPayload

@Entity(tableName = "hid_scripts")
data class HidScriptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val script: String,
    val createdAt: Long
) {
    fun toDomain() = HidPayload(
        id = id,
        name = name,
        script = script,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(payload: HidPayload) = HidScriptEntity(
            id = payload.id,
            name = payload.name,
            script = payload.script,
            createdAt = payload.createdAt
        )
    }
}
