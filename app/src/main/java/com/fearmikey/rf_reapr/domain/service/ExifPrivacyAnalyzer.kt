package com.fearmikey.rf_reapr.domain.service

import com.fearmikey.rf_reapr.domain.model.ExifMetadata
import com.fearmikey.rf_reapr.domain.model.RiskLevel
import java.util.Locale
import kotlin.math.abs

data class ExifPrivacyFinding(val severity: RiskLevel, val title: String, val detail: String)

/**
 * Judges what an image's metadata gives away about where, when, by whom and with what it
 * was taken. Works on tag names and values only, so it is independent of how they were read.
 */
object ExifPrivacyAnalyzer {

    /** Tags that identify a specific physical device. */
    private val serialTags = listOf("BodySerialNumber", "LensSerialNumber", "ImageUniqueID", "CameraSerialNumber")

    /** Tags that name a person or organisation. */
    private val identityTags = listOf("CameraOwnerName", "Artist", "Copyright", "XPAuthor")

    private val freeTextTags = listOf("UserComment", "ImageDescription", "XPComment", "XPTitle", "XPSubject", "XPKeywords")

    private val editors = listOf(
        "photoshop", "lightroom", "gimp", "snapseed", "picsart", "affinity", "pixelmator", "paint.net",
        "capture one", "darktable", "facetune", "canva", "luminar"
    )

    fun analyze(metadata: ExifMetadata): List<ExifPrivacyFinding> = buildList {
        val tags = metadata.rawTags
        fun present(names: List<String>) = names.mapNotNull { name -> tags[name]?.takeIf { it.isNotBlank() }?.let { name to it } }

        if (metadata.hasGps) {
            val coords = formatDecimal(metadata.latitude!!, metadata.longitude!!)
            val extras = listOfNotNull(
                metadata.altitude?.let { "altitude ${"%.0f".format(Locale.US, it)} m" },
                gpsTimestampUtc(tags)?.let { "GPS time $it" },
                tags["GPSImgDirection"]?.let { parseRational(it) }?.let { "camera facing ${"%.0f".format(Locale.US, it)}°" }
            )
            add(
                ExifPrivacyFinding(
                    RiskLevel.HIGH, "Exact location embedded",
                    "GPS coordinates $coords" + (if (extras.isEmpty()) "" else " (${extras.joinToString(", ")})") +
                        " pinpoint where the photo was taken, often a home or workplace."
                )
            )
        } else if (tags.keys.any { it.startsWith("GPS") && it != "GPSVersionID" }) {
            add(
                ExifPrivacyFinding(
                    RiskLevel.LOW, "Partial GPS data",
                    "Location tags are present without usable coordinates (${tags.keys.filter { it.startsWith("GPS") }.sorted().joinToString(", ")})."
                )
            )
        }

        present(serialTags).takeIf { it.isNotEmpty() }?.let { serials ->
            add(
                ExifPrivacyFinding(
                    RiskLevel.MEDIUM, "Device serial number",
                    serials.joinToString("; ") { "${it.first}: ${it.second}" } +
                        ". A serial links every photo from the same camera, including ones posted under other identities."
                )
            )
        }

        present(identityTags).takeIf { it.isNotEmpty() }?.let { owners ->
            add(
                ExifPrivacyFinding(
                    RiskLevel.MEDIUM, "Owner or author named",
                    owners.joinToString("; ") { "${it.first}: ${it.second}" }
                )
            )
        }

        if (metadata.hasThumbnail) {
            add(
                ExifPrivacyFinding(
                    RiskLevel.MEDIUM, "Embedded thumbnail",
                    "The file carries a preview image. If the photo was cropped, blurred or redacted afterwards, " +
                        "the thumbnail may still show the original."
                )
            )
        }

        val timestamp = metadata.dateTimeOriginal ?: tags["DateTime"]
        if (timestamp != null) {
            val zone = tags["OffsetTimeOriginal"] ?: tags["OffsetTime"]
            add(
                ExifPrivacyFinding(
                    RiskLevel.LOW, "Capture time recorded",
                    "Taken $timestamp" + (zone?.let { " (UTC$it, which also hints at the time zone)" } ?: "") + "."
                )
            )
        }

        val device = listOfNotNull(metadata.make, metadata.model).joinToString(" ").trim()
        if (device.isNotEmpty()) {
            add(
                ExifPrivacyFinding(
                    RiskLevel.LOW, "Device model identified",
                    device + (metadata.software?.let { ", software \"$it\"" } ?: "") +
                        ". The firmware / OS version narrows down which exploits apply to the device."
                )
            )
        }

        present(freeTextTags).takeIf { it.isNotEmpty() }?.let { texts ->
            add(
                ExifPrivacyFinding(
                    RiskLevel.LOW, "Free-text fields",
                    texts.joinToString("; ") { "${it.first}: ${it.second.take(120)}" }
                )
            )
        }

        if (metadata.hasXmp) {
            add(
                ExifPrivacyFinding(
                    RiskLevel.LOW, "XMP metadata block",
                    "XMP can hold edit history, document IDs and creator-tool details that plain EXIF viewers do not show."
                )
            )
        }
        if (tags.containsKey("MakerNote")) {
            add(
                ExifPrivacyFinding(
                    RiskLevel.LOW, "Manufacturer MakerNote",
                    "A proprietary data block from the camera maker; it can include serials, shutter count and face-detection data."
                )
            )
        }
    }.sortedByDescending { it.severity }

    fun overallRisk(findings: List<ExifPrivacyFinding>): RiskLevel? = findings.maxOfOrNull { it.severity }

    /** Signs the file was processed after capture. Not proof of manipulation, but worth noting for evidence. */
    fun editingIndicators(metadata: ExifMetadata): List<String> = buildList {
        val tags = metadata.rawTags
        metadata.software?.let { software ->
            editors.firstOrNull { software.contains(it, ignoreCase = true) }?.let {
                add("Software tag names an editor: \"$software\".")
            }
        }
        val original = tags["DateTimeOriginal"]
        val modified = tags["DateTime"]
        if (original != null && modified != null && original != modified) {
            add("File modified ($modified) after capture ($original).")
        }
        val digitized = tags["DateTimeDigitized"]
        if (original != null && digitized != null && original != digitized) {
            add("Digitised time ($digitized) differs from capture time ($original), typical of scans or re-photographs.")
        }
        if (metadata.make == null && metadata.model == null && tags.isNotEmpty() && !metadata.hasGps) {
            add("No camera make or model: the metadata was probably stripped or the image was generated or screenshotted.")
        }
    }

    fun formatDecimal(latitude: Double, longitude: Double): String =
        "%.6f, %.6f".format(Locale.US, latitude, longitude)

    /** Degrees, minutes and seconds with hemisphere letters, as used on maps and in reports. */
    fun formatDms(latitude: Double, longitude: Double): String =
        "${dms(latitude, 'N', 'S')} ${dms(longitude, 'E', 'W')}"

    private fun dms(value: Double, positive: Char, negative: Char): String {
        val absolute = abs(value)
        var degrees = absolute.toInt()
        val minutesFull = (absolute - degrees) * 60
        var minutes = minutesFull.toInt()
        var seconds = (minutesFull - minutes) * 60
        // Guard against 59.999… rounding up to 60.0 in the formatted output.
        if ("%.1f".format(Locale.US, seconds) == "60.0") { seconds = 0.0; minutes++ }
        if (minutes == 60) { minutes = 0; degrees++ }
        return "%d°%02d'%04.1f\"%c".format(Locale.US, degrees, minutes, seconds, if (value < 0) negative else positive)
    }

    /** Combines GPSDateStamp ("2024:03:09") and GPSTimeStamp ("14/1,5/1,30/1" or "14:05:30") into UTC. */
    fun gpsTimestampUtc(tags: Map<String, String>): String? {
        val date = tags["GPSDateStamp"]?.trim()?.replace(':', '-')?.takeIf { it.length == 10 } ?: return null
        val rawTime = tags["GPSTimeStamp"]?.trim() ?: return "$date UTC"
        val parts = rawTime.split(',', ':').mapNotNull { parseRational(it.trim()) }
        if (parts.size != 3) return "$date UTC"
        return "%s %02d:%02d:%02d UTC".format(Locale.US, date, parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
    }

    /** Parses an EXIF rational ("28/10") or plain number. */
    fun parseRational(value: String): Double? {
        val text = value.trim()
        if ('/' !in text) return text.toDoubleOrNull()
        val numerator = text.substringBefore('/').trim().toDoubleOrNull() ?: return null
        val denominator = text.substringAfter('/').trim().toDoubleOrNull() ?: return null
        return if (denominator == 0.0) null else numerator / denominator
    }

    fun mapUrl(latitude: Double, longitude: Double): String =
        "https://www.openstreetmap.org/?mlat=%.6f&mlon=%.6f#map=16/%.6f/%.6f".format(Locale.US, latitude, longitude, latitude, longitude)

    /** Plain-text report for the clipboard or case notes. */
    fun report(metadata: ExifMetadata): String = buildString {
        appendLine("Image metadata report: ${metadata.fileName ?: "image"}")
        metadata.sha256?.let { appendLine("SHA-256: $it") }
        listOfNotNull(metadata.mimeType, metadata.fileSizeFormatted, metadata.dimensionsFormatted)
            .joinToString(" · ").takeIf { it.isNotBlank() }?.let { appendLine(it) }
        if (metadata.hasGps) {
            appendLine("GPS: ${formatDecimal(metadata.latitude!!, metadata.longitude!!)} (${formatDms(metadata.latitude, metadata.longitude)})")
        }
        val findings = analyze(metadata)
        if (findings.isEmpty()) {
            appendLine("No privacy-relevant metadata found.")
        } else {
            appendLine("Privacy findings:")
            findings.forEach { appendLine("  [${it.severity}] ${it.title}: ${it.detail}") }
        }
        editingIndicators(metadata).forEach { appendLine("Editing indicator: $it") }
        if (metadata.rawTags.isNotEmpty()) {
            appendLine("Tags:")
            metadata.rawTags.toSortedMap().forEach { (tag, value) -> appendLine("  $tag: ${value.take(200)}") }
        }
    }.trim()
}
