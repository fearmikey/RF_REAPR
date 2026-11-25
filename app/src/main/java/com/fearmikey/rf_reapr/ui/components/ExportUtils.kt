package com.fearmikey.rf_reapr.ui.components

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Text export formats offered by the engagement/findings screens. */
enum class ExportFormat(val label: String, val mimeType: String, val extension: String) {
    MARKDOWN("Markdown report", "text/markdown", "md"),
    HTML("HTML report", "text/html", "html"),
    JSON("JSON (machine-readable)", "application/json", "json"),
    DEFECTDOJO("DefectDojo JSON (Generic Findings Import)", "application/json", "json"),
    CSV("Findings CSV", "text/csv", "csv"),
    HOSTS_CSV("Hosts CSV", "text/csv", "csv");

    /** Renders [report] in this format. */
    fun render(report: com.fearmikey.rf_reapr.domain.service.EngagementReport): String {
        val exporter = com.fearmikey.rf_reapr.domain.service.FindingsExporter
        return when (this) {
            MARKDOWN -> exporter.toMarkdown(report)
            HTML -> exporter.toHtml(report)
            JSON -> exporter.toJson(report)
            DEFECTDOJO -> exporter.toDefectDojoJson(report)
            CSV -> exporter.toCsv(report.findings)
            HOSTS_CSV -> exporter.hostsToCsv(report.hosts)
        }
    }

    /** File-name suffix, e.g. "acme-findings-20261120.csv". */
    val fileSuffix: String
        get() = when (this) {
            HOSTS_CSV -> "hosts"
            DEFECTDOJO -> "defectdojo"
            CSV -> "findings"
            else -> "report"
        }
}

/** Writes [text] to a user-picked SAF document. Returns an error message, or null on success. */
suspend fun writeTextToUri(context: Context, uri: Uri, text: String): String? = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(text) }
            ?: return@withContext "Couldn't open the selected file"
        null
    } catch (e: Exception) {
        e.message ?: "Export failed"
    }
}

/** e.g. "acme-audit-findings-20261120.csv" */
fun exportFileName(base: String, suffix: String, extension: String): String {
    val slug = base.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "rf-reapr" }.take(40)
    val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    return "$slug-$suffix-$date.$extension"
}
