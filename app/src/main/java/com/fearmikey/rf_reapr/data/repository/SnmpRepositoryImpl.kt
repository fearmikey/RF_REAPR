package com.fearmikey.rf_reapr.data.repository

import com.fearmikey.rf_reapr.domain.model.SnmpArpEntry
import com.fearmikey.rf_reapr.domain.model.SnmpInterface
import com.fearmikey.rf_reapr.domain.model.SnmpIpAddress
import com.fearmikey.rf_reapr.domain.model.SnmpResult
import com.fearmikey.rf_reapr.domain.model.SnmpVarBind
import com.fearmikey.rf_reapr.domain.repository.SnmpRepository
import com.fearmikey.rf_reapr.domain.service.SnmpTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.snmp4j.CommunityTarget
import org.snmp4j.PDU
import org.snmp4j.Snmp
import org.snmp4j.mp.SnmpConstants
import org.snmp4j.smi.Counter64
import org.snmp4j.smi.IpAddress
import org.snmp4j.smi.Null
import org.snmp4j.smi.OID
import org.snmp4j.smi.OctetString
import org.snmp4j.smi.UdpAddress
import org.snmp4j.smi.Variable
import org.snmp4j.smi.VariableBinding
import org.snmp4j.transport.DefaultUdpTransportMapping
import org.snmp4j.util.DefaultPDUFactory
import org.snmp4j.util.TableUtils
import org.snmp4j.util.TreeUtils
import java.io.IOException
import java.net.InetAddress

class SnmpRepositoryImpl : SnmpRepository {

    private inline fun <T> withSession(block: (Snmp) -> T): T {
        val transport = DefaultUdpTransportMapping()
        val snmp = Snmp(transport)
        try {
            transport.listen()
            return block(snmp)
        } finally {
            runCatching { snmp.close() }
        }
    }

    private fun target(ipAddress: String, community: String, version: Int, timeoutMs: Long = 1500, retries: Int = 2): CommunityTarget<UdpAddress> {
        // Resolve once here so hostnames work and a bad name fails with a clear error.
        val address = InetAddress.getByName(ipAddress)
        return CommunityTarget<UdpAddress>().apply {
            this.community = OctetString(community)
            this.address = UdpAddress(address, 161)
            this.retries = retries
            this.timeout = timeoutMs
            this.version = if (version == 0) SnmpConstants.version1 else SnmpConstants.version2c
        }
    }

    private fun pduFactory(version: Int) = DefaultPDUFactory(if (version == 0) PDU.GETNEXT else PDU.GETBULK)

    override suspend fun queryDevice(ipAddress: String, community: String, version: Int): Result<SnmpResult> = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        runCatching {
            runInterruptible {
                withSession { snmp ->
                    val target = target(ipAddress, community, version)
                    val systemPdu = PDU().apply {
                        SYSTEM_OIDS.forEach { add(VariableBinding(OID(it))) }
                        type = PDU.GET
                    }
                    val response = snmp.get(systemPdu, target).response
                        ?: throw IOException("No response. The community string may be wrong, SNMP may be disabled, or UDP 161 is filtered.")
                    if (response.errorStatus != PDU.noError) {
                        throw IOException("Agent returned ${response.errorStatusText}")
                    }
                    fun text(i: Int) = response.variableBindings.getOrNull(i)?.let { vb -> vb.variable.textOrNull(vb) }

                    val tables = TableUtils(snmp, pduFactory(version)).apply { setMaxNumRowsPerPDU(10) }
                    SnmpResult(
                        targetIp = ipAddress,
                        sysDescr = text(0),
                        sysObjectId = text(1),
                        sysUptime = text(2),
                        sysContact = text(3),
                        sysName = text(4),
                        sysLocation = text(5),
                        interfaces = runCatching { interfaces(tables, target, version) }.getOrDefault(emptyList()),
                        ipAddresses = runCatching { ipAddresses(tables, target) }.getOrDefault(emptyList()),
                        arpTable = runCatching { arpTable(tables, target) }.getOrDefault(emptyList()),
                        community = community,
                        version = version,
                        durationMs = System.currentTimeMillis() - started
                    )
                }
            }
        }
    }

    override suspend fun probeCommunity(ipAddress: String, community: String, version: Int): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            runInterruptible {
                withSession { snmp ->
                    val pdu = PDU().apply {
                        add(VariableBinding(OID(SYSTEM_OIDS[0])))
                        type = PDU.GET
                    }
                    val response = snmp.get(pdu, target(ipAddress, community, version, timeoutMs = 800, retries = 1)).response
                    response != null && response.errorStatus == PDU.noError
                }
            }
        }.getOrDefault(false)
    }

    override suspend fun walk(ipAddress: String, community: String, version: Int, rootOid: String, limit: Int): Result<List<SnmpVarBind>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val oid = SnmpTools.normalizeOid(rootOid) ?: throw IllegalArgumentException("Not a numeric OID")
                runInterruptible {
                    withSession { snmp ->
                        val target = target(ipAddress, community, version)
                        val tree = TreeUtils(snmp, pduFactory(version)).apply { setMaxRepetitions(20) }
                        val out = mutableListOf<SnmpVarBind>()
                        val events = tree.getSubtree(target, OID(oid))
                        for (event in events) {
                            if (event == null) continue
                            if (event.isError && out.isEmpty()) throw IOException(event.errorMessage ?: "Walk failed")
                            event.variableBindings?.forEach { vb ->
                                if (out.size < limit && vb != null && !vb.isException) {
                                    out += SnmpVarBind(vb.oid.toDottedString(), vb.variable.textOrNull(vb) ?: "", vb.variable.syntaxString)
                                }
                            }
                            if (out.size >= limit) break
                        }
                        if (out.isEmpty()) throw IOException("Nothing under $oid (no such subtree, or no response)")
                        out
                    }
                }
            }
        }

    private fun interfaces(tables: TableUtils, target: CommunityTarget<UdpAddress>, version: Int): List<SnmpInterface> {
        val columns = IF_COLUMNS.map { OID("1.3.6.1.2.1.2.2.1.$it") }.toTypedArray()
        val rows = tables.getTable(target, columns, null, null).filter { !it.isError && it.columns != null }
        // ifXTable gives ifName/ifAlias and 64-bit counters (v2c only for Counter64).
        val xColumns = IFX_COLUMNS.map { OID("1.3.6.1.2.1.31.1.1.1.$it") }.toTypedArray()
        val xRows = runCatching {
            tables.getTable(target, xColumns, null, null).filter { !it.isError && it.columns != null }
                .associateBy { it.index.toString() }
        }.getOrDefault(emptyMap())

        return rows.mapNotNull { row ->
            val c = row.columns
            val index = c.getOrNull(0).intOrNull() ?: row.index.last()
            val x = xRows[row.index.toString()]?.columns
            val ifName = x?.getOrNull(0)?.let { it.variable.textOrNull(it) }
            val hcIn = if (version != 0) x?.getOrNull(1).longOrNull() else null
            val hcOut = if (version != 0) x?.getOrNull(2).longOrNull() else null
            val highSpeedMbps = x?.getOrNull(3).longOrNull()
            val alias = x?.getOrNull(4)?.let { it.variable.textOrNull(it) }
            val speed = c.getOrNull(4).longOrNull() ?: 0
            SnmpInterface(
                index = index,
                name = ifName?.takeIf { it.isNotBlank() } ?: c.getOrNull(1)?.let { it.variable.textOrNull(it) } ?: "if$index",
                type = SnmpTools.ifTypeName(c.getOrNull(2).intOrNull() ?: 0),
                mtu = c.getOrNull(3).intOrNull() ?: 0,
                // ifSpeed saturates at 4.29 Gbps; ifHighSpeed is in Mbps.
                speed = if (speed >= 4_294_967_295L && highSpeedMbps != null) highSpeedMbps * 1_000_000 else speed,
                physAddress = c.getOrNull(5)?.variable.macOrNull() ?: "",
                adminStatus = SnmpTools.ifStatusName(c.getOrNull(6).intOrNull() ?: 4),
                operStatus = SnmpTools.ifStatusName(c.getOrNull(7).intOrNull() ?: 4),
                inOctets = hcIn ?: c.getOrNull(8).longOrNull() ?: -1,
                outOctets = hcOut ?: c.getOrNull(9).longOrNull() ?: -1,
                alias = alias?.takeIf { it.isNotBlank() }
            )
        }
    }

    private fun ipAddresses(tables: TableUtils, target: CommunityTarget<UdpAddress>): List<SnmpIpAddress> {
        val columns = arrayOf(OID("1.3.6.1.2.1.4.20.1.1"), OID("1.3.6.1.2.1.4.20.1.2"), OID("1.3.6.1.2.1.4.20.1.3"))
        return tables.getTable(target, columns, null, null)
            .filter { !it.isError && it.columns != null }
            .mapNotNull { row ->
                val addr = row.columns.getOrNull(0)?.variable?.toString() ?: return@mapNotNull null
                SnmpIpAddress(addr, row.columns.getOrNull(2)?.variable?.toString(), row.columns.getOrNull(1).intOrNull())
            }
    }

    private fun arpTable(tables: TableUtils, target: CommunityTarget<UdpAddress>): List<SnmpArpEntry> {
        val columns = arrayOf(OID("1.3.6.1.2.1.4.22.1.1"), OID("1.3.6.1.2.1.4.22.1.2"), OID("1.3.6.1.2.1.4.22.1.3"))
        return tables.getTable(target, columns, null, null)
            .filter { !it.isError && it.columns != null }
            .mapNotNull { row ->
                val ip = (row.columns.getOrNull(2)?.variable as? IpAddress)?.toString()
                    ?: row.columns.getOrNull(2)?.variable?.toString()
                    ?: return@mapNotNull null
                val mac = row.columns.getOrNull(1)?.variable.macOrNull() ?: return@mapNotNull null
                SnmpArpEntry(ip, mac, row.columns.getOrNull(0).intOrNull())
            }
            .take(1024)
    }

    private companion object {
        val SYSTEM_OIDS = listOf(
            "1.3.6.1.2.1.1.1.0", // sysDescr
            "1.3.6.1.2.1.1.2.0", // sysObjectID
            "1.3.6.1.2.1.1.3.0", // sysUpTime
            "1.3.6.1.2.1.1.4.0", // sysContact
            "1.3.6.1.2.1.1.5.0", // sysName
            "1.3.6.1.2.1.1.6.0"  // sysLocation
        )
        // ifIndex, ifDescr, ifType, ifMtu, ifSpeed, ifPhysAddress, ifAdminStatus, ifOperStatus, ifInOctets, ifOutOctets
        val IF_COLUMNS = listOf(1, 2, 3, 4, 5, 6, 7, 8, 10, 16)
        // ifName, ifHCInOctets, ifHCOutOctets, ifHighSpeed, ifAlias
        val IFX_COLUMNS = listOf(1, 6, 10, 15, 18)
    }
}

private fun Variable?.textOrNull(vb: VariableBinding): String? {
    if (this == null || this is Null || vb.isException) return null
    return when (this) {
        is OctetString -> if (isPrintable) toString() else toHexString(':')
        else -> toString()
    }.trim().takeIf { it.isNotEmpty() }
}

private fun Variable?.macOrNull(): String? =
    (this as? OctetString)?.takeIf { it.length() == 6 }?.toHexString(':')?.uppercase()

private fun VariableBinding?.intOrNull(): Int? = runCatching {
    this?.takeUnless { it.isException || it.variable is Null }?.variable?.toInt()
}.getOrNull()

private fun VariableBinding?.longOrNull(): Long? = runCatching {
    val v = this?.takeUnless { it.isException || it.variable is Null }?.variable ?: return@runCatching null
    if (v is Counter64) v.value else v.toLong()
}.getOrNull()
