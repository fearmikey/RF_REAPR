package com.fearmikey.rf_reapr.domain.service

import com.fearmikey.rf_reapr.domain.model.RiskLevel
import com.fearmikey.rf_reapr.domain.repository.UpnpDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpnpToolsTest {

    private val ssdp = "HTTP/1.1 200 OK\r\n" +
        "CACHE-CONTROL: max-age=120\r\n" +
        "LOCATION: http://192.168.1.1:5000/rootDesc.xml\r\n" +
        "SERVER: Linux/4.4 UPnP/1.1 MiniUPnPd/2.1\r\n" +
        "ST: urn:schemas-upnp-org:device:InternetGatewayDevice:1\r\n" +
        "USN: uuid:abc-123::urn:schemas-upnp-org:device:InternetGatewayDevice:1\r\n\r\n"

    private val description = """
        <?xml version="1.0"?>
        <root xmlns="urn:schemas-upnp-org:device-1-0">
          <device>
            <deviceType>urn:schemas-upnp-org:device:InternetGatewayDevice:1</deviceType>
            <friendlyName>Home Router &amp; Co</friendlyName>
            <manufacturer>Acme</manufacturer>
            <modelName>R1000</modelName>
            <presentationURL>/</presentationURL>
            <deviceList>
              <device>
                <deviceType>urn:schemas-upnp-org:device:WANConnectionDevice:1</deviceType>
                <friendlyName>WAN Connection</friendlyName>
                <serviceList>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:WANIPConnection:1</serviceType>
                    <serviceId>urn:upnp-org:serviceId:WANIPConn1</serviceId>
                    <controlURL>/ctl/IPConn</controlURL>
                    <eventSubURL>/evt/IPConn</eventSubURL>
                    <SCPDURL>/WANIPCn.xml</SCPDURL>
                  </service>
                </serviceList>
              </device>
            </deviceList>
          </device>
        </root>
    """.trimIndent()

    @Test
    fun `parses ssdp reply`() {
        val r = UpnpTools.parseSsdpResponse(ssdp)!!
        assertEquals("http://192.168.1.1:5000/rootDesc.xml", r.location)
        assertEquals("abc-123", r.uuid)
        assertTrue(r.server!!.contains("MiniUPnPd"))
        assertNull(UpnpTools.parseSsdpResponse("HTTP/1.1 200 OK\r\nST: x\r\n"))
    }

    @Test
    fun `parses description and resolves urls`() {
        val d = UpnpTools.parseDescription(description, "http://192.168.1.1:5000/rootDesc.xml")
        assertEquals("Home Router & Co", d.friendlyName)
        assertEquals("Acme", d.manufacturer)
        assertEquals("http://192.168.1.1:5000/", d.presentationUrl)
        val wan = UpnpTools.wanConnectionServices(d.services).single()
        assertEquals("http://192.168.1.1:5000/ctl/IPConn", wan.controlUrl)
    }

    @Test
    fun `parses port mapping and nat-pmp`() {
        val soap = "<s:Envelope><s:Body><u:GetGenericPortMappingEntryResponse>" +
            "<NewRemoteHost></NewRemoteHost><NewExternalPort>3389</NewExternalPort><NewProtocol>TCP</NewProtocol>" +
            "<NewInternalPort>3389</NewInternalPort><NewInternalClient>192.168.1.50</NewInternalClient>" +
            "<NewEnabled>1</NewEnabled><NewPortMappingDescription>RDP</NewPortMappingDescription><NewLeaseDuration>0</NewLeaseDuration>" +
            "</u:GetGenericPortMappingEntryResponse></s:Body></s:Envelope>"
        val m = UpnpTools.parsePortMapping(soap)!!
        assertEquals(3389, m.externalPort)
        assertEquals("192.168.1.50", m.internalClient)

        val pmp = byteArrayOf(0, 128.toByte(), 0, 0, 0, 0, 1, 0, 203.toByte(), 0, 113, 7)
        val result = UpnpTools.parseNatPmpResponse(pmp)!!
        assertEquals("203.0.113.7", result.externalIp)
        assertEquals(256L, result.epochSeconds)
        assertNull(UpnpTools.parseNatPmpResponse(byteArrayOf(0, 0, 0)))
    }

    @Test
    fun `flags exposed rdp mapping and old miniupnpd`() {
        val soap = "<NewExternalPort>3389</NewExternalPort><NewProtocol>TCP</NewProtocol><NewInternalPort>3389</NewInternalPort>" +
            "<NewInternalClient>192.168.1.50</NewInternalClient><NewEnabled>1</NewEnabled>"
        val gw = UpnpDevice(
            ipAddress = "192.168.1.1", friendlyName = "Router", manufacturer = "Acme", modelName = "R1",
            location = "http://192.168.1.1:5000/rootDesc.xml",
            deviceType = "urn:schemas-upnp-org:device:InternetGatewayDevice:1",
            server = "Linux UPnP/1.0 miniupnpd/1.9",
            portMappings = listOfNotNull(UpnpTools.parsePortMapping(soap))
        )
        val findings = UpnpTools.findings(listOf(gw), NatPmpResult("203.0.113.7", 0, 1), "192.168.1.1")
        assertEquals(RiskLevel.HIGH, findings.first().severity)
        assertNotNull(findings.find { it.title.contains("3389") && it.severity == RiskLevel.HIGH })
        assertNotNull(findings.find { it.title.contains("MiniUPnPd") })
        assertNotNull(findings.find { it.title.contains("NAT-PMP") })
    }
}
