package com.example.rf_reapr.domain.model

import java.util.Date

data class EvidenceFolder(
    val id: String,
    val projectId: String,
    val name: String,
    val createdAt: Date = Date(),
    val isDeleted: Boolean = false,
    val deletedAt: Date? = null
)
