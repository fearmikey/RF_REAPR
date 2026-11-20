package com.fearmikey.rf_reapr.domain.model

data class SnmpResult(
    val targetIp: String,
    val sysName: String? = null,
    val sysDescr: String? = null,
    val sysUptime: String? = null,
    val sysContact: String? = null,
    val sysLocation: String? = null,
    val interfaces: List<SnmpInterface> = emptyList(),
    val sysObjectId: String? = null,
    val community: String = "",
    val version: Int = 1,
    val ipAddresses: List<SnmpIpAddress> = emptyList(),
    /** Neighbours the device knows about (ipNetToMediaTable): a free LAN host list. */
    val arpTable: List<SnmpArpEntry> = emptyList(),
    val durationMs: Long = 0
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
    val outOctets: Long,
    val alias: String? = null
)

data class SnmpIpAddress(val address: String, val netmask: String?, val ifIndex: Int?)

data class SnmpArpEntry(val ipAddress: String, val macAddress: String, val ifIndex: Int?)

data class SnmpVarBind(val oid: String, val value: String, val type: String)
