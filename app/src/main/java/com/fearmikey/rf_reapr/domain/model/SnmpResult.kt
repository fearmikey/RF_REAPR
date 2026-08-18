package com.fearmikey.rf_reapr.domain.model

data class SnmpResult(
    val targetIp: String,
    val sysName: String? = null,
    val sysDescr: String? = null,
    val sysUptime: String? = null,
    val sysContact: String? = null,
    val sysLocation: String? = null,
    val interfaces: List<SnmpInterface> = emptyList()
)

data class SnmpInterface(
    val index: Int,
    val name: String,
    val type: String,
    val mtu: Int,
    val speed: Long,
    val physAddress: String,
    val adminStatus: String,
    val operStatus: String,
    val inOctets: Long,
    val outOctets: Long
)
