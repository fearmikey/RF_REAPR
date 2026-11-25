package com.fearmikey.rf_reapr.domain.service

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class SurveyKind(val label: String) {
    WIFI("Wi-Fi"),
    BLE("Bluetooth LE"),
    CELL("Cellular")
}

/**
 * One emitter logged during a geotagged survey, at the position where it was strongest.
 *
 * For [SurveyKind.CELL], [identifier] is "MCCMNC_AREA_CELLID" and [capabilities] holds the
 * radio technology ("LTE", "NR", "GSM", "WCDMA").
 */
data class SurveyObservation(
    val kind: SurveyKind,
    val identifier: String,
    val name: String? = null,
    val capabilities: String? = null,
    val frequencyMhz: Int? = null,
    val channel: Int? = null,
    val bestRssi: Int,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val accuracyM: Float? = null,
    val firstSeen: Long,
    val lastSeen: Long,
    val sightings: Int = 1
)

/** A scan result waiting to be merged into a session at the current position. */
data class SurveySighting(
    val kind: SurveyKind,
    val identifier: String,
    val name: String? = null,
    val capabilities: String? = null,
    val frequencyMhz: Int? = null,
    val channel: Int? = null,
    val rssi: Int
)

data class SurveyFix(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val accuracyM: Float? = null,
    val timestamp: Long
)

/** Merge rule for survey sessions: one row per emitter, positioned where it was heard best. */
object SurveyMerger {

    fun key(kind: SurveyKind, identifier: String): String = "${kind.name}|${identifier.uppercase()}"

    /**
     * @param existing current rows for the sighted emitters, keyed by [key].
     * @return (new rows, updated rows)
     */
    fun merge(
        sightings: List<SurveySighting>,
        fix: SurveyFix,
        existing: Map<String, SurveyObservation>
    ): Pair<List<SurveyObservation>, List<SurveyObservation>> {
        val inserts = mutableListOf<SurveyObservation>()
        val updates = mutableListOf<SurveyObservation>()
        sightings
            .filter { it.identifier.isNotBlank() }
            .groupBy { key(it.kind, it.identifier) }
            .forEach { (k, group) ->
                val s = group.maxBy { it.rssi }
                val prior = existing[k]
                if (prior == null) {
                    inserts += SurveyObservation(
                        kind = s.kind, identifier = s.identifier, name = s.name, capabilities = s.capabilities,
                        frequencyMhz = s.frequencyMhz, channel = s.channel, bestRssi = s.rssi,
                        latitude = fix.latitude, longitude = fix.longitude, altitude = fix.altitude,
                        accuracyM = fix.accuracyM, firstSeen = fix.timestamp, lastSeen = fix.timestamp
                    )
                } else {
                    val stronger = s.rssi > prior.bestRssi
                    updates += prior.copy(
                        // A hidden network may reveal its name on a later sighting.
                        name = prior.name?.takeIf { it.isNotBlank() } ?: s.name,
                        capabilities = s.capabilities ?: prior.capabilities,
                        bestRssi = if (stronger) s.rssi else prior.bestRssi,
                        latitude = if (stronger) fix.latitude else prior.latitude,
                        longitude = if (stronger) fix.longitude else prior.longitude,
                        altitude = if (stronger) fix.altitude else prior.altitude,
                        accuracyM = if (stronger) fix.accuracyM else prior.accuracyM,
                        lastSeen = fix.timestamp,
                        sightings = prior.sightings + 1
                    )
                }
            }
        return inserts to updates
    }
}

/**
 * Exports survey observations to open interchange formats. Nothing is uploaded: the user
 * saves the file and decides what to do with it.
 */
object SurveyExporter {

    /**
     * WiGLE CSV 1.4 (https://api.wigle.net/csvFormat.html): a metadata line, a header line,
     * then one row per observation.
     */
    fun wigleCsv(observations: List<SurveyObservation>, appVersion: String, deviceModel: String = "Android"): String = buildString {
        fun meta(value: String) = value.replace(Regex("[,\\r\\n]"), "_")
        appendLine(
            "WigleWifi-1.4,appRelease=${meta(appVersion)},model=${meta(deviceModel)},release=${meta(appVersion)}," +
                "device=RF-REAPR,display=RF-REAPR,board=android,brand=RF-REAPR"
        )
        appendLine("MAC,SSID,AuthMode,FirstSeen,Channel,RSSI,CurrentLatitude,CurrentLongitude,AltitudeMeters,AccuracyMeters,Type")
        val time = utcFormat("yyyy-MM-dd HH:mm:ss")
        observations.sortedBy { it.firstSeen }.forEach { o ->
            val (authMode, type) = when (o.kind) {
                SurveyKind.WIFI -> o.capabilities.orEmpty() to "WIFI"
                SurveyKind.BLE -> "Misc [LE]" to "BLE"
                SurveyKind.CELL -> "${o.capabilities.orEmpty()};" to (o.capabilities?.takeIf { it.isNotBlank() } ?: "GSM")
            }
            appendLine(
                listOf(
                    if (o.kind == SurveyKind.CELL) o.identifier else o.identifier.lowercase(),
                    // The format has no quoting, so separators inside a name are replaced.
                    wigleField(o.name.orEmpty()),
                    wigleField(authMode),
                    time.format(Date(o.firstSeen)),
                    (o.channel ?: 0).toString(),
                    o.bestRssi.toString(),
                    coordinate(o.latitude),
                    coordinate(o.longitude),
                    "%.1f".format(Locale.US, o.altitude ?: 0.0),
                    "%.1f".format(Locale.US, o.accuracyM ?: 0f),
                    type
                ).joinToString(",")
            )
        }
    }

    fun kml(sessionName: String, observations: List<SurveyObservation>): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine("""<kml xmlns="http://www.opengis.net/kml/2.2">""")
        appendLine("<Document>")
        appendLine("  <name>${xml(sessionName)}</name>")
        SurveyKind.entries.forEach { kind ->
            val items = observations.filter { it.kind == kind }.sortedBy { it.firstSeen }
            if (items.isEmpty()) return@forEach
            appendLine("  <Folder>")
            appendLine("    <name>${kind.label} (${items.size})</name>")
            items.forEach { o ->
                appendLine("    <Placemark>")
                appendLine("      <name>${xml(displayName(o))}</name>")
                appendLine("      <description>${xml(description(o))}</description>")
                appendLine("      <TimeStamp><when>${iso(o.firstSeen)}</when></TimeStamp>")
                appendLine("      <Point><coordinates>${coordinate(o.longitude)},${coordinate(o.latitude)},${"%.1f".format(Locale.US, o.altitude ?: 0.0)}</coordinates></Point>")
                appendLine("    </Placemark>")
            }
            appendLine("  </Folder>")
        }
        appendLine("</Document>")
        appendLine("</kml>")
    }

    fun gpx(sessionName: String, observations: List<SurveyObservation>, appVersion: String): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine("""<gpx version="1.1" creator="RF-REAPR ${xml(appVersion)}" xmlns="http://www.topografix.com/GPX/1/1">""")
        appendLine("  <metadata><name>${xml(sessionName)}</name></metadata>")
        observations.sortedBy { it.firstSeen }.forEach { o ->
            appendLine("""  <wpt lat="${coordinate(o.latitude)}" lon="${coordinate(o.longitude)}">""")
            o.altitude?.let { appendLine("    <ele>${"%.1f".format(Locale.US, it)}</ele>") }
            appendLine("    <time>${iso(o.firstSeen)}</time>")
            appendLine("    <name>${xml(displayName(o))}</name>")
            appendLine("    <desc>${xml(description(o))}</desc>")
            appendLine("    <type>${o.kind.name}</type>")
            appendLine("  </wpt>")
        }
        appendLine("</gpx>")
    }

    private fun displayName(o: SurveyObservation): String =
        o.name?.takeIf { it.isNotBlank() } ?: if (o.kind == SurveyKind.WIFI) "(hidden network)" else o.identifier

    private fun description(o: SurveyObservation): String = listOfNotNull(
        o.identifier,
        "${o.bestRssi} dBm",
        o.channel?.let { "channel $it" },
        o.frequencyMhz?.let { "$it MHz" },
        o.capabilities?.takeIf { it.isNotBlank() },
        "seen ${o.sightings}×"
    ).joinToString(" · ")

    private fun wigleField(value: String): String = value.replace(Regex("[,\\r\\n]"), "_")

    private fun coordinate(value: Double): String = "%.6f".format(Locale.US, value)

    private fun iso(epochMs: Long): String = utcFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(Date(epochMs))

    private fun utcFormat(pattern: String) = SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

    /** XML text escaping; also drops control characters that are illegal in XML 1.0 (SSIDs are arbitrary bytes). */
    fun xml(text: String): String = buildString {
        text.forEach { c ->
            when {
                c == '&' -> append("&amp;")
                c == '<' -> append("&lt;")
                c == '>' -> append("&gt;")
                c == '"' -> append("&quot;")
                c == '\'' -> append("&apos;")
                c < ' ' && c != '\n' && c != '\t' -> Unit
                else -> append(c)
            }
        }
    }
}
