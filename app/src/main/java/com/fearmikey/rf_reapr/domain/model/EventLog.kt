package com.fearmikey.rf_reapr.domain.model

data class EventLog(
    val id: Long,
    val type: String,
    val timestamp: Long,
    val summary: String,
    val detailJson: String
)
