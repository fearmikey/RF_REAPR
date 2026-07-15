package com.fearmikey.rf_reapr.domain.model

import java.util.Date

data class EvidenceProject(
    val id: String,
    val name: String,
    val createdAt: Date = Date(),
    val description: String = "",
    val showGps: Boolean = true,
    val showDate: Boolean = true,
    val showTimestamp: Boolean = true,
    val isDeleted: Boolean = false,
    val deletedAt: Date? = null
)
