package com.fearmikey.rf_reapr.domain.model

data class RdapDomainInfo(
    val ldhName: String,
    val status: List<String>,
    val events: List<RdapEvent>,
    val entities: List<RdapEntity>,
    val nameservers: List<String>,
    val rawJson: String? = null
)

data class RdapEvent(
    val eventAction: String,
    val eventDate: String
)

data class RdapEntity(
    val roles: List<String>,
    val vcard: String? = null // Simplified vCard representation
)
