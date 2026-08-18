package com.fearmikey.rf_reapr.data.repository

import com.fearmikey.rf_reapr.domain.model.SnmpInterface
import com.fearmikey.rf_reapr.domain.model.SnmpResult
import com.fearmikey.rf_reapr.domain.repository.SnmpRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.snmp4j.CommunityTarget
import org.snmp4j.PDU
import org.snmp4j.Snmp
import org.snmp4j.TransportMapping
import org.snmp4j.event.ResponseEvent
import org.snmp4j.mp.SnmpConstants
import org.snmp4j.smi.*
import org.snmp4j.transport.DefaultUdpTransportMapping
import org.snmp4j.util.DefaultPDUFactory
import org.snmp4j.util.TreeEvent
import org.snmp4j.util.TreeUtils
import java.io.IOException

class SnmpRepositoryImpl : SnmpRepository {

    override suspend fun queryDevice(
        ipAddress: String,
        community: String,
        version: Int
    ): Result<SnmpResult> = withContext(Dispatchers.IO) {
        val transport: TransportMapping<UdpAddress> = DefaultUdpTransportMapping()
        val snmp = Snmp(transport)
        
        try {
            transport.listen()

            val target = CommunityTarget<UdpAddress>().apply {
                this.community = OctetString(community)
                this.address = UdpAddress("$ipAddress/161")
                this.retries = 2
                this.timeout = 1500
                this.version = when (version) {
                    0 -> SnmpConstants.version1
                    else -> SnmpConstants.version2c
                }
            }

            // Get System Info
            val systemPdu = PDU().apply {
                add(VariableBinding(OID(".1.3.6.1.2.1.1.1.0"))) // sysDescr
                add(VariableBinding(OID(".1.3.6.1.2.1.1.3.0"))) // sysUpTime
                add(VariableBinding(OID(".1.3.6.1.2.1.1.4.0"))) // sysContact
                add(VariableBinding(OID(".1.3.6.1.2.1.1.5.0"))) // sysName
                add(VariableBinding(OID(".1.3.6.1.2.1.1.6.0"))) // sysLocation
                type = PDU.GET
            }

            val response = snmp.get(systemPdu, target)
            val responsePdu = response.response

            val result = if (responsePdu != null) {
                SnmpResult(
                    targetIp = ipAddress,
                    sysDescr = responsePdu.get(0).variable.toString(),
                    sysUptime = responsePdu.get(1).variable.toString(),
                    sysContact = responsePdu.get(2).variable.toString(),
                    sysName = responsePdu.get(3).variable.toString(),
                    sysLocation = responsePdu.get(4).variable.toString(),
                    interfaces = queryInterfaces(snmp, target)
                )
            } else {
                return@withContext Result.failure(IOException("SNMP Timeout or Error"))
            }

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            snmp.close()
        }
    }

    private fun queryInterfaces(snmp: Snmp, target: CommunityTarget<UdpAddress>): List<SnmpInterface> {
        val treeUtils = TreeUtils(snmp, DefaultPDUFactory())
        val oids = arrayOf(
            OID(".1.3.6.1.2.1.2.2.1.1"), // ifIndex
            OID(".1.3.6.1.2.1.2.2.1.2"), // ifDescr
            OID(".1.3.6.1.2.1.2.2.1.3"), // ifType
            OID(".1.3.6.1.2.1.2.2.1.4"), // ifMtu
            OID(".1.3.6.1.2.1.2.2.1.5"), // ifSpeed
            OID(".1.3.6.1.2.1.2.2.1.6"), // ifPhysAddress
            OID(".1.3.6.1.2.1.2.2.1.7"), // ifAdminStatus
            OID(".1.3.6.1.2.1.2.2.1.8"), // ifOperStatus
            OID(".1.3.6.1.2.1.2.2.1.10"), // ifInOctets
            OID(".1.3.6.1.2.1.2.2.1.16")  // ifOutOctets
        )

        val events = treeUtils.walk(target, oids)
        val interfaces = mutableListOf<SnmpInterface>()

        for (event in events) {
            if (event == null || event.isError) continue
            val vbs = event.variableBindings ?: continue
            if (vbs.isEmpty()) continue

            interfaces.add(
                SnmpInterface(
                    index = vbs[0].variable.toInt(),
                    name = vbs[1].variable.toString(),
                    type = vbs[2].variable.toString(),
                    mtu = vbs[3].variable.toInt(),
                    speed = vbs[4].variable.toLong(),
                    physAddress = vbs[5].variable.toString(),
                    adminStatus = getStatusString(vbs[6].variable.toInt()),
                    operStatus = getStatusString(vbs[7].variable.toInt()),
                    inOctets = vbs[8].variable.toLong(),
                    outOctets = vbs[9].variable.toLong()
                )
            )
        }

        return interfaces
    }

    private fun getStatusString(status: Int): String {
        return when (status) {
            1 -> "up"
            2 -> "down"
            3 -> "testing"
            4 -> "unknown"
            5 -> "dormant"
            6 -> "notPresent"
            7 -> "lowerLayerDown"
            else -> "unknown($status)"
        }
    }
}
