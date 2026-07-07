package com.example.rf_reapr.data.db.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class ScanSessionWithNodes(
    @Embedded val session: ScanSessionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "ipAddress",
        associateBy = Junction(SessionNodeCrossRef::class, parentColumn = "sessionId", entityColumn = "ipAddress")
    )
    val nodes: List<NetworkNodeEntity>
)
