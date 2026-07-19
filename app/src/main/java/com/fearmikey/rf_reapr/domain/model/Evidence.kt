package com.fearmikey.rf_reapr.domain.model

import java.util.Date

data class Evidence(
    val id: String,
    val filePath: String,
    val timestamp: Date,
    val latitude: Double?,
    val longitude: Double?,
    val controlId: String? = null,
    val projectId: String? = null,
    val folderId: String? = null,
    val notes: String = "",
    val hasStego: Boolean = false,
    val stegoType: String? = null,
    val isDeleted: Boolean = false,
    val deletedAt: Date? = null
)
