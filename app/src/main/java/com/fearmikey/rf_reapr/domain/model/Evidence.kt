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
    val deletedAt: Date? = null,
    /** SHA-256 of the file recorded at capture/import (see EvidenceHasher); null until hashed. */
    val sha256: String? = null,
    /** Id of the finding this item documents, if linked. */
    val findingId: Long? = null
)
