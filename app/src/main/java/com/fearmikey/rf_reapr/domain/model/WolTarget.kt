package com.fearmikey.rf_reapr.domain.model

/** A saved Wake-on-LAN device. Persisted as JSON (domain.model is kept by R8 rules). */
data class WolTarget(
    val id: String,
    val name: String,
    val mac: String,
    val broadcast: String,
    val port: Int = 9,
    /** Optional device IP, used to confirm it came online after waking. */
    val ip: String? = null
)
