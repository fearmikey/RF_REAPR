package com.fearmikey.rf_reapr.data.repository

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.fearmikey.rf_reapr.data.db.dao.*
import com.fearmikey.rf_reapr.data.db.entity.EventLogEntity
import com.fearmikey.rf_reapr.domain.model.*
import com.fearmikey.rf_reapr.domain.repository.ReportRepository
import com.fearmikey.rf_reapr.util.ReportGraphRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import androidx.core.graphics.withTranslation
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.common.usermodel.PictureType
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.util.Units
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ReportRepositoryImpl(
    private val context: Context,
    private val scanSessionDao: ScanSessionDao,
    private val evidenceDao: EvidenceDao,
    private val complianceDao: ComplianceDao,
    private val eventLogDao: EventLogDao,
) : ReportRepository {
    private val gson = Gson()

    override fun getAvailableScanSessions(): Flow<List<ReportScanSession>> {
        return scanSessionDao.getSessionsWithNodes().map { sessions ->
            sessions.map { sessionWithNodes ->
                ReportScanSession(
                    id = sessionWithNodes.session.id,
                    timestamp = sessionWithNodes.session.timestamp,
                    networkName = sessionWithNodes.session.networkName,
                    gatewayIp = sessionWithNodes.session.gatewayIp,
                    nodes = sessionWithNodes.nodes.map { nodeEntity ->
                        NetworkNode(
                            id = nodeEntity.ipAddress,
                            ipAddress = nodeEntity.ipAddress,
                            macAddress = nodeEntity.macAddress,
                            hostname = nodeEntity.hostname,
                            riskLevel = nodeEntity.riskLevel,
                            deviceType = nodeEntity.deviceType,
                            openPorts = nodeEntity.openPorts
                        )
                    }
                )
            }
        }
    }

    override fun getAvailableEvidenceProjects(): Flow<List<ReportEvidenceProject>> {
        return evidenceDao.getAllProjects().map { projects ->
            projects.map { projectEntity ->
                val project = EvidenceProject(
                    id = projectEntity.id,
                    name = projectEntity.name,
                    createdAt = Date(projectEntity.createdAt),
                    description = projectEntity.description
                )
                ReportEvidenceProject(project, emptyList())
            }
        }
    }
    
    override suspend fun getFullEvidenceProject(projectId: String): ReportEvidenceProject {
        val projectEntity = evidenceDao.getProjectById(projectId) ?: throw Exception("Project not found")
        val items = evidenceDao.getEvidenceForProject(projectId).first().map { entity ->
            Evidence(
                id = entity.id,
                filePath = entity.filePath,
                timestamp = Date(entity.timestamp),
                latitude = entity.latitude,
                longitude = entity.longitude,
                notes = entity.notes,
                hasStego = entity.hasStego
            )
        }
        return ReportEvidenceProject(
            EvidenceProject(
                id = projectEntity.id,
                name = projectEntity.name,
                createdAt = Date(projectEntity.createdAt),
                description = projectEntity.description
            ),
            items
        )
    }

    override fun getAvailableComplianceFrameworks(): Flow<List<ReportComplianceFramework>> = flow {
        val frameworks = ComplianceFramework.entries.map { framework ->
            ReportComplianceFramework(framework, emptyList())
        }
        emit(frameworks)
    }

    override suspend fun getFullComplianceFindings(frameworkId: String): List<ComplianceControl> {
        val staticControls = getStaticControls(frameworkId)
        val findings = complianceDao.getFindingsForFramework(frameworkId).first()
        return staticControls.map { control ->
            val finding = findings.find { it.controlId == control.id }
            control.copy(
                status = ComplianceStatus.valueOf(finding?.status ?: "NONE"),
                notes = finding?.notes ?: ""
            )
        }
    }

    private fun getStaticControls(frameworkId: String): List<ComplianceControl> {
        return when (frameworkId) {
            "NIST" -> listOf(
                ComplianceControl("NIST-3.1.1", "NIST", "Authorized User Access", "Limit system access to authorized users, processes acting on behalf of authorized users, and devices (including other systems)."),
                ComplianceControl("NIST-3.1.2", "NIST", "Transaction/Function Limiting", "Limit system access to the types of transactions and functions that authorized users are permitted to execute."),
                ComplianceControl("NIST-3.5.3", "NIST", "Multifactor Authentication", "Use multifactor authentication for local and network access to privileged accounts and for network access to non-privileged accounts."),
                ComplianceControl("NIST-3.12.1", "NIST", "Security Control Assessment", "Periodically assess the security controls in organizational systems to determine if the controls are effective in their application."),
                ComplianceControl("NIST-3.12.3", "NIST", "Continuous Monitoring", "Monitor organizational systems on an ongoing basis and notify designated organizational officials when a predetermined set of security events occurs.")
            )
            "ISO27001" -> listOf(
                ComplianceControl("ISO-A.5.1", "ISO27001", "InfoSec Policies", "Information security policies shall be defined, approved by management, published and communicated to employees and relevant external parties."),
                ComplianceControl("ISO-A.9.2.1", "ISO27001", "User Registration", "A formal user registration and de-registration process shall be implemented to enable assignment of access rights."),
                ComplianceControl("ISO-A.9.4.2", "ISO27001", "Secure Log-on", "Where required by the access control policy, access to systems and applications shall be controlled by a secure log-on procedure."),
                ComplianceControl("ISO-A.12.4.1", "ISO27001", "Event Logging", "Event logs recording user activities, exceptions, faults and information security events shall be produced, kept and regularly reviewed."),
                ComplianceControl("ISO-A.18.1.1", "ISO27001", "Legal Identification", "All relevant legislative statutory, regulatory, contractual requirements and the organization’s approach to meet these requirements shall be explicitly identified.")
            )
            "SOC2" -> listOf(
                ComplianceControl("SOC2-CC1.1", "SOC2", "Integrity & Ethics", "The organization demonstrates a commitment to integrity and ethical values through directives, actions, and behavior."),
                ComplianceControl("SOC2-CC6.1", "SOC2", "Logical Access Restriction", "The entity restricts logical access to relevant information assets to authorized users, processes, and devices."),
                ComplianceControl("SOC2-CC6.7", "SOC2", "Data Transmission Control", "The entity restricts the transmission, movement, and removal of information to authorized users and processes, and protects it during transmission."),
                ComplianceControl("SOC2-CC7.1", "SOC2", "Vulnerability Management", "The entity identifies and manages system vulnerabilities to maintain the security of information and systems."),
                ComplianceControl("SOC2-CC7.2", "SOC2", "Security Monitoring", "The entity monitors its system for security events and vulnerabilities, and evaluates the impact of any identified events.")
            )
            else -> emptyList()
        }
    }

    override fun getAvailableLogs(): Flow<List<EventLog>> {
        return eventLogDao.getAllLogs().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun generatePdf(data: ReportData): Result<Uri> = withContext(Dispatchers.IO) {
        generatePdfViaPdfDocument(data)
    }

    private fun generatePdfViaPdfDocument(data: ReportData): Result<Uri> {
        return try {
            val pdfDocument = android.graphics.pdf.PdfDocument()
            val pageWidth = 595
            val pageHeight = 842
            val margin = 50f
            val contentWidth = (pageWidth - (margin * 2)).toInt()
            
            var pageNumber = 1
            var pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas
            val paint = Paint()
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
            var y = margin

            fun getRiskColor(risk: RiskLevel): Int {
                return when (risk) {
                    RiskLevel.CRITICAL -> Color.RED
                    RiskLevel.HIGH -> Color.rgb(255, 128, 0)
                    RiskLevel.MEDIUM -> Color.rgb(200, 160, 0)
                    RiskLevel.LOW -> Color.rgb(46, 125, 50)
                }
            }

            fun startNewPage() {
                pdfDocument.finishPage(page)
                pageNumber++
                pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                y = margin
            }

            fun checkNewPage(neededHeight: Float) {
                if (y + neededHeight > pageHeight - margin) {
                    startNewPage()
                }
            }

            fun drawWrappedText(text: String, size: Float, isBold: Boolean = false, color: Int = Color.BLACK, indent: Float = 0f): Float {
                textPaint.textSize = size
                textPaint.isFakeBoldText = isBold
                textPaint.color = color
                
                val layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, (contentWidth - indent).toInt())
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1.1f)
                    .setIncludePad(false)
                    .build()

                checkNewPage(layout.height.toFloat())
                
                canvas.withTranslation(margin + indent, y) {
                    layout.draw(this)
                }
                
                val height = layout.height.toFloat()
                y += height
                return height
            }

            fun drawTableHeader(columns: List<Pair<String, Float>>) {
                val headerHeight = 25f
                checkNewPage(headerHeight + 20f)
                
                paint.color = Color.LTGRAY
                canvas.drawRect(margin, y, pageWidth - margin, y + headerHeight, paint)
                
                paint.color = Color.BLACK
                paint.isFakeBoldText = true
                paint.textSize = 10f
                columns.forEach { (text, offset) ->
                    canvas.drawText(text, margin + offset, y + 17f, paint)
                }
                paint.isFakeBoldText = false
                y += headerHeight + 5f
            }

            // Header
            paint.textSize = 24f
            paint.isFakeBoldText = true
            canvas.drawText(data.title, margin, y, paint)
            y += 40f
            
            paint.textSize = 12f
            paint.isFakeBoldText = false
            canvas.drawText("Auditor: ${data.auditorName}", margin, y, paint)
            y += 20f
            canvas.drawText("Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(data.timestamp)}", margin, y, paint)
            y += 40f
            
            // Executive Summary
            checkNewPage(40f)
            paint.textSize = 16f
            paint.isFakeBoldText = true
            canvas.drawText("Executive Summary", margin, y, paint)
            y += 25f
            drawWrappedText(data.executiveSummary, 11f)
            y += 20f

            // Network Scans
            if (data.networkScans.isNotEmpty()) {
                checkNewPage(40f)
                paint.textSize = 16f
                paint.isFakeBoldText = true
                canvas.drawText("Network Scans", margin, y, paint)
                y += 30f
                
                data.networkScans.forEach { scan ->
                    checkNewPage(60f)
                    paint.textSize = 13f
                    paint.isFakeBoldText = true
                    canvas.drawText("${scan.networkName ?: "Unknown"} (${scan.gatewayIp ?: ""})", margin, y, paint)
                    y += 20f
                    
                    val cols = listOf("IP Address" to 10f, "Hostname" to 130f, "Ports" to 280f, "Risk" to 450f)
                    drawTableHeader(cols)

                    paint.textSize = 10f
                    scan.nodes.forEach { node ->
                        if (y > pageHeight - margin - 30f) {
                            startNewPage()
                            drawTableHeader(cols)
                        }
                        
                        canvas.drawText(node.ipAddress, margin + 10f, y + 15f, paint)
                        canvas.drawText(node.hostname ?: "N/A", margin + 130f, y + 15f, paint)
                        
                        val portStr = node.openPorts.joinToString(",") { it.port.toString() }
                        canvas.drawText(if (portStr.length > 30) portStr.take(27) + "..." else portStr, margin + 280f, y + 15f, paint)
                        
                        val prevColor = paint.color
                        paint.color = getRiskColor(node.riskLevel)
                        paint.isFakeBoldText = true
                        canvas.drawText(node.riskLevel.name, margin + 450f, y + 15f, paint)
                        paint.color = prevColor
                        paint.isFakeBoldText = false
                        
                        y += 20f
                    }
                    y += 10f
                }
            }

            // WiFi Scans
            if (data.wifiScans.isNotEmpty()) {
                checkNewPage(40f)
                paint.textSize = 16f
                paint.isFakeBoldText = true
                canvas.drawText("WiFi Spectrum Analysis", margin, y, paint)
                y += 30f

                data.wifiScans.forEach { log ->
                    checkNewPage(40f)
                    paint.textSize = 13f
                    paint.isFakeBoldText = true
                    canvas.drawText(log.summary, margin, y, paint)
                    y += 20f

                    try {
                        val type = object : TypeToken<List<WifiAccessPoint>>() {}.type
                        val aps: List<WifiAccessPoint> = gson.fromJson(log.detailJson, type)
                        
                        val cols = listOf("SSID" to 10f, "BSSID" to 150f, "Ch" to 350f, "RSSI" to 400f)
                        drawTableHeader(cols)

                        paint.textSize = 10f
                        aps.forEach { ap ->
                            if (y > pageHeight - margin - 30f) {
                                startNewPage()
                                drawTableHeader(cols)
                            }
                            canvas.drawText(ap.ssid.take(20), margin + 10f, y + 15f, paint)
                            canvas.drawText(ap.bssid, margin + 150f, y + 15f, paint)
                            canvas.drawText(ap.getChannel().toString(), margin + 350f, y + 15f, paint)
                            canvas.drawText("${ap.signalLevel}dBm", margin + 400f, y + 15f, paint)
                            y += 20f
                        }
                        y += 10f

                        val bands = listOf(
                            Triple(2400f, 2484f, "2.4 GHz Spectrum"),
                            Triple(5145f, 5840f, "5 GHz Spectrum"),
                            Triple(5900f, 7150f, "6 GHz Spectrum")
                        )

                        bands.forEach { (min, max, title) ->
                            val bandAps = aps.filter { it.frequency.toFloat() in min..max }
                            if (bandAps.isNotEmpty()) {
                                val spectrumBitmap = ReportGraphRenderer.renderWifiSpectrum(bandAps, min, max, title)
                                val drawWidth = 500f
                                val scale = drawWidth / spectrumBitmap.width
                                val drawHeight = spectrumBitmap.height * scale
                                
                                checkNewPage(drawHeight + 20f)
                                val rect = RectF(margin, y, margin + drawWidth, y + drawHeight)
                                canvas.drawBitmap(spectrumBitmap, null, rect, null)
                                y += drawHeight + 10f
                                spectrumBitmap.recycle()
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            // Compliance
            if (data.complianceFindings.isNotEmpty()) {
                checkNewPage(40f)
                paint.textSize = 16f
                paint.isFakeBoldText = true
                canvas.drawText("Compliance Audit", margin, y, paint)
                y += 30f

                data.complianceFindings.forEach { framework ->
                    checkNewPage(60f)
                    paint.textSize = 13f
                    paint.isFakeBoldText = true
                    canvas.drawText(framework.framework.title, margin, y, paint)
                    y += 18f
                    drawWrappedText(framework.framework.description, 10f)
                    y += 10f

                    framework.controls.forEach { control ->
                        checkNewPage(20f)
                        paint.textSize = 11f
                        paint.isFakeBoldText = true
                        canvas.drawText("${control.id}:", margin + 20f, y, paint)
                        val statusX = margin + 20f + paint.measureText("${control.id}: ")
                        
                        val prevColor = paint.color
                        paint.color = when(control.status) {
                            ComplianceStatus.COMPLIANT -> Color.rgb(46, 125, 50)
                            ComplianceStatus.NON_COMPLIANT -> Color.RED
                            else -> Color.DKGRAY
                        }
                        canvas.drawText(control.status.name, statusX, y, paint)
                        paint.color = prevColor
                        y += 18f
                        
                        if (control.notes.isNotBlank()) {
                            drawWrappedText("Auditor Notes: ${control.notes}", 9f, color = Color.DKGRAY, indent = 40f)
                            y += 5f
                        }
                    }
                    y += 10f
                }
            }

            // Evidence Photos
            if (data.evidenceProjects.isNotEmpty()) {
                checkNewPage(40f)
                paint.textSize = 16f
                paint.isFakeBoldText = true
                canvas.drawText("Physical Evidence Gallery", margin, y, paint)
                y += 30f

                data.evidenceProjects.forEach { projectWithItems ->
                    checkNewPage(30f)
                    paint.textSize = 13f
                    paint.isFakeBoldText = true
                    canvas.drawText("Project: ${projectWithItems.project.name}", margin, y, paint)
                    y += 20f
                    
                    projectWithItems.items.forEach { evidence ->
                        val imgFile = File(evidence.filePath)
                        if (imgFile.exists()) {
                            val bitmap = BitmapFactory.decodeFile(evidence.filePath)
                            if (bitmap != null) {
                                val maxWidth = 450f
                                val scale = maxWidth / bitmap.width
                                val drawHeight = bitmap.height * scale
                                
                                checkNewPage(drawHeight + 60f)
                                
                                val rect = RectF(margin + 20f, y, margin + 20f + maxWidth, y + drawHeight)
                                canvas.drawBitmap(bitmap, null, rect, null)
                                y += drawHeight + 10f
                                
                                paint.textSize = 9f
                                paint.isFakeBoldText = false
                                canvas.drawText("Captured: ${SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(evidence.timestamp)}", margin + 20f, y, paint)
                                y += 15f
                                if (evidence.notes.isNotBlank()) {
                                    drawWrappedText("Notes: ${evidence.notes}", 10f, isBold = true, indent = 20f)
                                }
                                y += 15f
                                bitmap.recycle()
                            }
                        }
                    }
                }
            }

            // Event Logs
            if (data.eventLogs.isNotEmpty()) {
                checkNewPage(40f)
                paint.textSize = 16f
                paint.isFakeBoldText = true
                canvas.drawText("Detailed Audit Logs", margin, y, paint)
                y += 30f

                data.eventLogs.forEach { log ->
                    checkNewPage(40f)
                    paint.textSize = 12f
                    paint.isFakeBoldText = true
                    canvas.drawText("${log.summary} [${log.type}]", margin, y, paint)
                    y += 18f
                    
                    try {
                        when (log.type) {
                            "PORT" -> {
                                val dataMap: Map<String, Any> = gson.fromJson(log.detailJson, object : TypeToken<Map<String, Any>>() {}.type)
                                val ports = (dataMap["ports"] as? List<*>) ?: emptyList<Any>()
                                if (ports.isNotEmpty()) {
                                    val cols = listOf("Port" to 10f, "Service" to 100f, "Banner" to 250f)
                                    drawTableHeader(cols)
                                    paint.textSize = 9f
                                    ports.forEach { portObj ->
                                        if (y > pageHeight - margin - 20f) { startNewPage(); drawTableHeader(cols) }
                                        val p = portObj as Map<*, *>
                                        canvas.drawText(p["port"].toString().substringBefore("."), margin + 10f, y + 15f, paint)
                                        canvas.drawText(p["serviceName"]?.toString() ?: "unknown", margin + 100f, y + 15f, paint)
                                        canvas.drawText(p["banner"]?.toString()?.take(40) ?: "N/A", margin + 250f, y + 15f, paint)
                                        y += 18f
                                    }
                                }
                            }
                            "WEB_CLOUD", "WEB" -> {
                                try {
                                    val dataMap: Map<String, Any> = gson.fromJson(log.detailJson, object : TypeToken<Map<String, Any>>() {}.type)
                                    val assets = (dataMap["assets"] as? List<*>) ?: emptyList<Any>()
                                    if (assets.isNotEmpty()) {
                                        val cols = listOf("Platform" to 10f, "URL" to 100f, "Status" to 400f)
                                        drawTableHeader(cols)
                                        paint.textSize = 9f
                                        assets.forEach { assetObj ->
                                            if (y > pageHeight - margin - 20f) { startNewPage(); drawTableHeader(cols) }
                                            val a = assetObj as Map<*, *>
                                            canvas.drawText(a["platform"]?.toString() ?: "", margin + 10f, y + 15f, paint)
                                            canvas.drawText(a["url"]?.toString()?.take(50) ?: "", margin + 100f, y + 15f, paint)
                                            canvas.drawText(a["status"]?.toString() ?: "", margin + 400f, y + 15f, paint)
                                            y += 18f
                                        }
                                    } else {
                                        // Try WebsiteInspectorStatus
                                        if (dataMap.containsKey("httpStatus")) {
                                            drawWrappedText("Website Security Audit Details:", 10f, isBold = true)
                                            drawWrappedText("HTTP Status: ${dataMap["httpStatus"]}", 9f, indent = 10f)
                                            drawWrappedText("TLS Status: ${dataMap["tlsStatus"]}", 9f, indent = 10f)
                                            drawWrappedText("DNS Status: ${dataMap["dnsStatus"]}", 9f, indent = 10f)
                                        } else {
                                            drawWrappedText(log.detailJson, 10f, color = Color.DKGRAY)
                                        }
                                    }
                                } catch (_: Exception) {
                                    drawWrappedText(log.detailJson, 10f, color = Color.DKGRAY)
                                }
                            }
                            "WEB_TLS" -> {
                                val dataMap: Map<String, Any> = gson.fromJson(log.detailJson, object : TypeToken<Map<String, Any>>() {}.type)
                                val ciphers = (dataMap["ciphers"] as? List<*>) ?: emptyList<Any>()
                                if (ciphers.isNotEmpty()) {
                                    val cols = listOf("Protocol" to 10f, "Cipher Suite" to 100f, "Risk" to 450f)
                                    drawTableHeader(cols)
                                    paint.textSize = 8f
                                    ciphers.forEach { cipherObj ->
                                        if (y > pageHeight - margin - 20f) { startNewPage(); drawTableHeader(cols) }
                                        val c = cipherObj as Map<*, *>
                                        canvas.drawText(c["protocol"]?.toString() ?: "", margin + 10f, y + 15f, paint)
                                        canvas.drawText(c["cipherSuite"]?.toString() ?: "", margin + 100f, y + 15f, paint)
                                        canvas.drawText(c["riskLevel"]?.toString() ?: "", margin + 450f, y + 15f, paint)
                                        y += 18f
                                    }
                                }
                            }
                            "WEB_SUBDOMAIN" -> {
                                val dataMap: Map<String, Any> = gson.fromJson(log.detailJson, object : TypeToken<Map<String, Any>>() {}.type)
                                val subdomains = (dataMap["subdomains"] as? List<*>) ?: emptyList<Any>()
                                if (subdomains.isNotEmpty()) {
                                    drawWrappedText("Discovered Subdomains: " + subdomains.joinToString { (it as Map<*, *>)["hostname"]?.toString() ?: "" }, 10f)
                                }
                            }
                            "PING" -> {
                                val dataMap: Map<String, Any> = gson.fromJson(log.detailJson, object : TypeToken<Map<String, Any>>() {}.type)
                                val lines = (dataMap["lines"] as? List<*>) ?: emptyList<Any>()
                                if (lines.isNotEmpty()) {
                                    drawWrappedText("Ping results for ${dataMap["host"]}:", 10f, isBold = true)
                                    lines.forEach { line ->
                                        drawWrappedText(line.toString(), 9f, indent = 20f)
                                    }
                                }
                            }
                            "TOPOLOGY" -> {
                                val dataMap: Map<String, Any> = gson.fromJson(log.detailJson, object : TypeToken<Map<String, Any>>() {}.type)
                                if (dataMap.containsKey("nodes") && dataMap.containsKey("edges")) {
                                    // It's a MappedGraph
                                    val nodes = (dataMap["nodes"] as? List<*>) ?: emptyList<Any>()
                                    if (nodes.isNotEmpty()) {
                                        drawWrappedText("Network Topology Map:", 11f, isBold = true)
                                        val cols = listOf("IP Address" to 10f, "Hostname" to 120f, "Device" to 280f, "Risk" to 420f)
                                        drawTableHeader(cols)
                                        paint.textSize = 9f
                                        nodes.forEach { nodeContainer ->
                                            if (y > pageHeight - margin - 20f) { startNewPage(); drawTableHeader(cols) }
                                            val container = nodeContainer as Map<*, *>
                                            val n = container["node"] as Map<*, *>
                                            canvas.drawText(n["ipAddress"]?.toString() ?: "", margin + 10f, y + 15f, paint)
                                            canvas.drawText(n["hostname"]?.toString() ?: "N/A", margin + 120f, y + 15f, paint)
                                            canvas.drawText(n["deviceType"]?.toString() ?: "UNKNOWN", margin + 280f, y + 15f, paint)
                                            canvas.drawText(n["riskLevel"]?.toString() ?: "LOW", margin + 420f, y + 15f, paint)
                                            y += 18f
                                        }
                                    }
                                    val edges = (dataMap["edges"] as? List<*>) ?: emptyList<Any>()
                                    if (edges.isNotEmpty()) {
                                        y += 5f
                                        drawWrappedText("Connections:", 10f, isBold = true)
                                        edges.forEach { edgeObj ->
                                            val e = edgeObj as Map<*, *>
                                            drawWrappedText("${e["fromNodeId"]} -> ${e["toNodeId"]} (${e["connectionType"]})", 9f, indent = 15f)
                                        }
                                    }
                                } else if (dataMap.containsKey("ipAddress")) {
                                    // It's a single NetworkNode
                                    drawWrappedText("Node Audit: ${dataMap["ipAddress"]} (${dataMap["hostname"] ?: "N/A"})", 11f, isBold = true)
                                    drawWrappedText("Risk: ${dataMap["riskLevel"]}, Type: ${dataMap["deviceType"]}", 10f, indent = 10f)
                                    val ports = (dataMap["openPorts"] as? List<*>) ?: emptyList<Any>()
                                    if (ports.isNotEmpty()) {
                                        drawWrappedText("Open Ports:", 10f, isBold = true, indent = 10f)
                                        ports.forEach { portObj ->
                                            val p = portObj as Map<*, *>
                                            drawWrappedText("Port ${p["port"].toString().substringBefore(".")}: ${p["serviceName"]}", 9f, indent = 20f)
                                        }
                                    }
                                }
                            }
                            else -> {
                                // Try to see if it's JSON but not handled
                                if (log.detailJson.startsWith("{") || log.detailJson.startsWith("[")) {
                                    drawWrappedText("Structured Log Data (JSON):", 10f, isBold = true)
                                    drawWrappedText(log.detailJson.take(500), 8f, color = Color.DKGRAY, indent = 10f)
                                } else {
                                    drawWrappedText(log.detailJson, 10f, color = Color.DKGRAY)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        drawWrappedText(log.detailJson, 10f, color = Color.DKGRAY)
                    }
                    y += 15f
                }
            }

            pdfDocument.finishPage(page)
            
            val fileName = "RF_REAPR_Report_${System.currentTimeMillis()}.pdf"
            val file = File(context.cacheDir, fileName)
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()
            
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            Result.success(uri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun generateDocx(data: ReportData): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val document = XWPFDocument()
            
            // Title
            val title = document.createParagraph()
            val titleRun = title.createRun()
            titleRun.isBold = true
            titleRun.fontSize = 20
            titleRun.setText(data.title)
            
            // Metadata
            val meta = document.createParagraph()
            val metaRun = meta.createRun()
            metaRun.setText("Auditor: ${data.auditorName}")
            meta.createRun().addBreak()
            metaRun.setText("Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(data.timestamp)}")
            
            // Executive Summary
            val summaryTitle = document.createParagraph()
            summaryTitle.createRun().apply {
                isBold = true
                fontSize = 14
                setText("Executive Summary")
            }
            val summary = document.createParagraph()
            summary.createRun().setText(data.executiveSummary)
            
            // Network Scans
            if (data.networkScans.isNotEmpty()) {
                val scanSection = document.createParagraph()
                scanSection.createRun().apply {
                    addBreak()
                    isBold = true
                    fontSize = 14
                    setText("Network Scans")
                }
                
                data.networkScans.forEach { scan ->
                    document.createParagraph().createRun().apply {
                        isBold = true
                        setText("${scan.networkName ?: "Unknown"} - ${scan.gatewayIp ?: ""}")
                    }
                    
                    val table = document.createTable(scan.nodes.size + 1, 4)
                    val header = table.getRow(0)
                    header.getCell(0).text = "IP Address"
                    header.getCell(1).text = "Hostname"
                    header.getCell(2).text = "Ports"
                    header.getCell(3).text = "Risk"
                    
                    scan.nodes.forEachIndexed { index, node ->
                        val row = table.getRow(index + 1)
                        row.getCell(0).text = node.ipAddress
                        row.getCell(1).text = node.hostname ?: ""
                        row.getCell(2).text = node.openPorts.joinToString(", ") { it.port.toString() }
                        val riskCell = row.getCell(3)
                        riskCell.text = node.riskLevel.name
                        
                        // Word Color Coding
                        val color = when(node.riskLevel) {
                            RiskLevel.CRITICAL -> "FF0000"
                            RiskLevel.HIGH -> "FF8000"
                            RiskLevel.MEDIUM -> "C8A000"
                            RiskLevel.LOW -> "2E7D32"
                        }
                        riskCell.paragraphs[0].runs[0].color = color
                    }
                }
            }

            // WiFi Scans
            if (data.wifiScans.isNotEmpty()) {
                val wifiSection = document.createParagraph()
                wifiSection.createRun().apply {
                    addBreak()
                    isBold = true
                    fontSize = 14
                    setText("WiFi Spectrum Analysis")
                }

                data.wifiScans.forEach { log ->
                    document.createParagraph().createRun().apply {
                        isBold = true
                        setText(log.summary)
                    }

                    try {
                        val type = object : TypeToken<List<WifiAccessPoint>>() {}.type
                        val aps: List<WifiAccessPoint> = gson.fromJson(log.detailJson, type)
                        
                        val table = document.createTable(aps.size + 1, 4)
                        val header = table.getRow(0)
                        header.getCell(0).text = "SSID"
                        header.getCell(1).text = "BSSID"
                        header.getCell(2).text = "Channel"
                        header.getCell(3).text = "Signal"
                        
                        aps.forEachIndexed { index, ap ->
                            val row = table.getRow(index + 1)
                            row.getCell(0).text = ap.ssid
                            row.getCell(1).text = ap.bssid
                            row.getCell(2).text = ap.getChannel().toString()
                            row.getCell(3).text = "${ap.signalLevel} dBm"
                        }

                        // Graphical Spectrum
                        val bands = listOf(
                            Triple(2400f, 2484f, "2.4 GHz Spectrum"),
                            Triple(5145f, 5840f, "5 GHz Spectrum"),
                            Triple(5900f, 7150f, "6 GHz Spectrum")
                        )

                        bands.forEach { (min, max, title) ->
                            val bandAps = aps.filter { it.frequency.toFloat() in min..max }
                            if (bandAps.isNotEmpty()) {
                                val spectrumBitmap = ReportGraphRenderer.renderWifiSpectrum(bandAps, min, max, title)
                                val stream = java.io.ByteArrayOutputStream()
                                spectrumBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                                val bis = java.io.ByteArrayInputStream(stream.toByteArray())
                                
                                val pImg = document.createParagraph()
                                pImg.alignment = ParagraphAlignment.CENTER
                                val runImg = pImg.createRun()
                                runImg.addPicture(
                                    bis,
                                    PictureType.PNG,
                                    "spectrum_$min.png",
                                    Units.toEMU(450.0),
                                    Units.toEMU(225.0)
                                )
                                spectrumBitmap.recycle()
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            // Compliance
            if (data.complianceFindings.isNotEmpty()) {
                val complianceSection = document.createParagraph()
                complianceSection.createRun().apply {
                    addBreak()
                    isBold = true
                    fontSize = 14
                    setText("Compliance Audit")
                }

                data.complianceFindings.forEach { framework ->
                    document.createParagraph().createRun().apply {
                        isBold = true
                        setText(framework.framework.title)
                    }
                    document.createParagraph().createRun().setText(framework.framework.description)

                    val table = document.createTable(framework.controls.size + 1, 3)
                    val header = table.getRow(0)
                    header.getCell(0).text = "Control"
                    header.getCell(1).text = "Status"
                    header.getCell(2).text = "Notes"

                    framework.controls.forEachIndexed { index, control ->
                        val row = table.getRow(index + 1)
                        row.getCell(0).text = "${control.id}: ${control.name}"
                        val statusCell = row.getCell(1)
                        statusCell.text = control.status.name
                        
                        val color = when(control.status) {
                            ComplianceStatus.COMPLIANT -> "2E7D32"
                            ComplianceStatus.NON_COMPLIANT -> "FF0000"
                            else -> "808080"
                        }
                        statusCell.paragraphs[0].runs[0].color = color
                        
                        row.getCell(2).text = control.notes
                    }
                }
            }

            // Other Logs
            if (data.eventLogs.isNotEmpty()) {
                val logSection = document.createParagraph()
                logSection.createRun().apply {
                    addBreak()
                    isBold = true
                    fontSize = 14
                    setText("System Event Logs")
                }

                data.eventLogs.forEach { log ->
                    val p = document.createParagraph()
                    p.createRun().apply {
                        isBold = true
                        setText("${log.summary} [${log.type}]")
                    }
                    
                    try {
                        when (log.type) {
                            "PORT" -> {
                                val dataMap: Map<String, Any> = gson.fromJson(log.detailJson, object : TypeToken<Map<String, Any>>() {}.type)
                                val ports = (dataMap["ports"] as? List<*>) ?: emptyList<Any>()
                                if (ports.isNotEmpty()) {
                                    val table = document.createTable(ports.size + 1, 3)
                                    table.getRow(0).apply {
                                        getCell(0).text = "Port"
                                        getCell(1).text = "Service"
                                        getCell(2).text = "Banner"
                                    }
                                    ports.forEachIndexed { i, portObj ->
                                        val row = table.getRow(i + 1)
                                        val po = portObj as Map<*, *>
                                        row.getCell(0).text = po["port"].toString().substringBefore(".")
                                        row.getCell(1).text = po["serviceName"]?.toString() ?: ""
                                        row.getCell(2).text = po["banner"]?.toString() ?: ""
                                    }
                                }
                            }
                            "WEB_CLOUD", "WEB" -> {
                                try {
                                    val dataMap: Map<String, Any> = gson.fromJson(log.detailJson, object : TypeToken<Map<String, Any>>() {}.type)
                                    val assets = (dataMap["assets"] as? List<*>) ?: emptyList<Any>()
                                    if (assets.isNotEmpty()) {
                                        val table = document.createTable(assets.size + 1, 3)
                                        table.getRow(0).apply {
                                            getCell(0).text = "Platform"
                                            getCell(1).text = "URL"
                                            getCell(2).text = "Status"
                                        }
                                        assets.forEachIndexed { i, assetObj ->
                                            val row = table.getRow(i + 1)
                                            val ao = assetObj as Map<*, *>
                                            row.getCell(0).text = ao["platform"]?.toString() ?: ""
                                            row.getCell(1).text = ao["url"]?.toString() ?: ""
                                            row.getCell(2).text = ao["status"]?.toString() ?: ""
                                        }
                                    } else {
                                        document.createParagraph().createRun().setText(log.detailJson)
                                    }
                                } catch (e: Exception) {
                                    document.createParagraph().createRun().setText(log.detailJson)
                                }
                            }
                            "WEB_SUBDOMAIN" -> {
                                try {
                                    val dataMap: Map<String, Any> = gson.fromJson(log.detailJson, object : TypeToken<Map<String, Any>>() {}.type)
                                    val subdomains = (dataMap["subdomains"] as? List<*>) ?: emptyList<Any>()
                                    if (subdomains.isNotEmpty()) {
                                        document.createParagraph().createRun().apply { isBold = true; setText("Discovered Subdomains:") }
                                        val table = document.createTable(subdomains.size + 1, 2)
                                        table.getRow(0).apply {
                                            getCell(0).text = "Hostname"
                                            getCell(1).text = "IP Address"
                                        }
                                        subdomains.forEachIndexed { i, subObj ->
                                            val row = table.getRow(i + 1)
                                            val so = subObj as Map<*, *>
                                            row.getCell(0).text = so["hostname"]?.toString() ?: ""
                                            row.getCell(1).text = so["ip"]?.toString() ?: "N/A"
                                        }
                                    }
                                } catch (e: Exception) {
                                    document.createParagraph().createRun().setText(log.detailJson)
                                }
                            }
                            "WEB_TLS" -> {
                                val dataMap: Map<String, Any> = gson.fromJson(log.detailJson, object : TypeToken<Map<String, Any>>() {}.type)
                                val ciphers = (dataMap["ciphers"] as? List<*>) ?: emptyList<Any>()
                                if (ciphers.isNotEmpty()) {
                                    val table = document.createTable(ciphers.size + 1, 3)
                                    table.getRow(0).apply {
                                        getCell(0).text = "Protocol"
                                        getCell(1).text = "Cipher Suite"
                                        getCell(2).text = "Risk"
                                    }
                                    ciphers.forEachIndexed { i, cipherObj ->
                                        val row = table.getRow(i + 1)
                                        val co = cipherObj as Map<*, *>
                                        row.getCell(0).text = co["protocol"]?.toString() ?: ""
                                        row.getCell(1).text = co["cipherSuite"]?.toString() ?: ""
                                        row.getCell(2).text = co["riskLevel"]?.toString() ?: ""
                                    }
                                }
                            }
                            "PING" -> {
                                val dataMap: Map<String, Any> = gson.fromJson(log.detailJson, object : TypeToken<Map<String, Any>>() {}.type)
                                val lines = (dataMap["lines"] as? List<*>) ?: emptyList<Any>()
                                if (lines.isNotEmpty()) {
                                    document.createParagraph().createRun().apply {
                                        isBold = true
                                        setText("Ping results for ${dataMap["host"]}:")
                                    }
                                    lines.forEach { line ->
                                        document.createParagraph().createRun().setText(line.toString())
                                    }
                                }
                            }
                            "TOPOLOGY" -> {
                                val dataMap: Map<String, Any> = gson.fromJson(log.detailJson, object : TypeToken<Map<String, Any>>() {}.type)
                                if (dataMap.containsKey("nodes") && dataMap.containsKey("edges")) {
                                    val nodes = (dataMap["nodes"] as? List<*>) ?: emptyList<Any>()
                                    if (nodes.isNotEmpty()) {
                                        document.createParagraph().createRun().apply { isBold = true; setText("Network Topology Map:") }
                                        val table = document.createTable(nodes.size + 1, 4)
                                        table.getRow(0).apply {
                                            getCell(0).text = "IP Address"
                                            getCell(1).text = "Hostname"
                                            getCell(2).text = "Device"
                                            getCell(3).text = "Risk"
                                        }
                                        nodes.forEachIndexed { i, nodeContainer ->
                                            val row = table.getRow(i + 1)
                                            val n = (nodeContainer as Map<*, *>)["node"] as Map<*, *>
                                            row.getCell(0).text = n["ipAddress"]?.toString() ?: ""
                                            row.getCell(1).text = n["hostname"]?.toString() ?: ""
                                            row.getCell(2).text = n["deviceType"]?.toString() ?: ""
                                            row.getCell(3).text = n["riskLevel"]?.toString() ?: ""
                                        }
                                    }
                                    val edges = (dataMap["edges"] as? List<*>) ?: emptyList<Any>()
                                    if (edges.isNotEmpty()) {
                                        document.createParagraph().createRun().apply { isBold = true; setText("Connections:") }
                                        edges.forEach { edgeObj ->
                                            val e = edgeObj as Map<*, *>
                                            document.createParagraph().createRun().setText("${e["fromNodeId"]} -> ${e["toNodeId"]} (${e["connectionType"]})")
                                        }
                                    }
                                } else if (dataMap.containsKey("ipAddress")) {
                                    document.createParagraph().createRun().apply { isBold = true; setText("Node Audit: ${dataMap["ipAddress"]}") }
                                    document.createParagraph().createRun().setText("Type: ${dataMap["deviceType"]}, Risk: ${dataMap["riskLevel"]}")
                                    val ports = (dataMap["openPorts"] as? List<*>) ?: emptyList<Any>()
                                    if (ports.isNotEmpty()) {
                                        document.createParagraph().createRun().apply { isBold = true; setText("Open Ports:") }
                                        ports.forEach { portObj ->
                                            val p = portObj as Map<*, *>
                                            document.createParagraph().createRun().setText("Port ${p["port"].toString().substringBefore(".")}: ${p["serviceName"]}")
                                        }
                                    }
                                }
                            }
                            else -> {
                                document.createParagraph().createRun().setText(log.detailJson)
                            }
                        }
                    } catch (e: Exception) {
                        document.createParagraph().createRun().setText(log.detailJson)
                    }
                }
            }

            // Evidence Photos
            if (data.evidenceProjects.isNotEmpty()) {
                val evidenceSection = document.createParagraph()
                evidenceSection.createRun().apply {
                    addBreak()
                    isBold = true
                    fontSize = 14
                    setText("Physical Evidence Gallery")
                }

                data.evidenceProjects.forEach { projectWithItems ->
                    document.createParagraph().createRun().apply {
                        isBold = true
                        setText("Project: ${projectWithItems.project.name}")
                    }
                    
                    projectWithItems.items.forEach { evidence ->
                        val p = document.createParagraph()
                        p.alignment = ParagraphAlignment.CENTER
                        val run = p.createRun()
                        
                        val imgFile = File(evidence.filePath)
                        if (imgFile.exists()) {
                            try {
                                run.addPicture(
                                    FileInputStream(imgFile),
                                    PictureType.JPEG,
                                    imgFile.name,
                                    Units.toEMU(400.0),
                                    Units.toEMU(300.0)
                                )
                                run.addBreak()
                                run.setText("Captured: ${SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(evidence.timestamp)}")
                                if (evidence.notes.isNotBlank()) {
                                    run.addBreak()
                                    val notesRun = p.createRun()
                                    notesRun.isBold = true
                                    notesRun.setText("Notes: ${evidence.notes}")
                                }
                                run.addBreak()
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
            
            val fileName = "RF_REAPR_Report_${System.currentTimeMillis()}.docx"
            val file = File(context.cacheDir, fileName)
            FileOutputStream(file).use { out ->
                document.write(out)
            }
            
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            Result.success(uri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun EventLogEntity.toDomain(): EventLog {
        return EventLog(id, type, timestamp, summary, detailJson)
    }
}
