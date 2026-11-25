package com.fearmikey.rf_reapr.data.audit

import android.content.Context
import android.net.Uri
import com.fearmikey.rf_reapr.domain.repository.FindingRepository
import com.fearmikey.rf_reapr.domain.repository.LogRepository
import com.fearmikey.rf_reapr.domain.repository.ScanSessionRepository
import com.fearmikey.rf_reapr.domain.service.NmapImporter
import com.fearmikey.rf_reapr.domain.service.NmapParseException
import com.fearmikey.rf_reapr.domain.service.NmapXmlParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Imports an Nmap `-oX` file picked by the user: hosts and open ports go onto the network
 * map (as a scan session of the active project) and exposures into the findings store.
 */
class NmapImportManager(
    context: Context,
    private val scanSessions: ScanSessionRepository,
    private val findings: FindingRepository,
    private val logs: LogRepository
) {
    private val appContext = context.applicationContext

    suspend fun import(uri: Uri): Result<NmapImporter.Summary> = withContext(Dispatchers.IO) {
        runCatching {
            val size = runCatching { appContext.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } }.getOrNull()
            if (size != null && size > MAX_BYTES) throw NmapParseException("The file is larger than ${MAX_BYTES / (1024 * 1024)} MB")

            val scan = appContext.contentResolver.openInputStream(uri)?.use(NmapXmlParser::parse)
                ?: throw NmapParseException("Couldn't open the selected file")
            val nodes = NmapImporter.toNodes(scan)
            if (nodes.isEmpty()) throw NmapParseException("The scan contains no hosts that were up")

            // saveDiscoveredNodes keeps what is already known about a host, so the imported
            // ports are merged in afterwards (the import wins for ports it also saw).
            scanSessions.saveDiscoveredNodes(nodes, "Nmap import", null)
            nodes.forEach { imported ->
                val existing = scanSessions.getNodeByIp(imported.ipAddress) ?: return@forEach
                val ports = (imported.openPorts + existing.openPorts).distinctBy { it.port }.sortedBy { it.port }
                scanSessions.updateNodeDetails(
                    existing.copy(
                        openPorts = ports,
                        riskLevel = maxOf(existing.riskLevel, imported.riskLevel, compareBy { it.ordinal })
                    )
                )
            }
            findings.record(NmapImporter.toFindings(scan))

            val summary = NmapImporter.summarize(scan)
            logs.saveLog(
                type = "TOPOLOGY",
                summary = "Nmap import: ${summary.hosts} hosts, ${summary.openPorts} open ports" + (scan.scanner?.let { " ($it)" } ?: ""),
                detailJson = "{}"
            )
            summary
        }
    }

    private companion object {
        const val MAX_BYTES = 25L * 1024 * 1024
    }
}
