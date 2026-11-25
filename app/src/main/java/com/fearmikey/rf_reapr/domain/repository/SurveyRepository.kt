package com.fearmikey.rf_reapr.domain.repository

import com.fearmikey.rf_reapr.domain.service.SiteSample
import com.fearmikey.rf_reapr.domain.service.SurveyFix
import com.fearmikey.rf_reapr.domain.service.SurveyKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** ActiveTaskMonitor task name of a running geotagged survey (the foreground service adds the location type for it). */
const val SURVEY_TASK_NAME = "Geotagged Survey"

data class SurveyOptions(
    val name: String,
    val wifi: Boolean = true,
    val ble: Boolean = true,
    val cell: Boolean = true
)

data class SurveyState(
    val isRunning: Boolean = false,
    val sessionId: Long? = null,
    val startedAt: Long? = null,
    /** Most recent GPS fix, if one has arrived. */
    val fix: SurveyFix? = null,
    /** Distinct emitters logged so far in this session. */
    val counts: Map<SurveyKind, Int> = emptyMap(),
    /** Scan batches dropped because there was no recent GPS fix to tag them with. */
    val skippedNoFix: Int = 0,
    val message: String? = null
)

data class SurveySessionInfo(
    val id: Long,
    val name: String,
    val startedAt: Long,
    val endedAt: Long?,
    val counts: Map<SurveyKind, Int>
) {
    val total: Int get() = counts.values.sum()
}

enum class SurveyExportFormat(val label: String, val extension: String, val mimeType: String) {
    WIGLE_CSV("WiGLE CSV", "csv", "text/csv"),
    KML("KML (Google Earth, QGIS)", "kml", "application/vnd.google-earth.kml+xml"),
    GPX("GPX waypoints", "gpx", "application/gpx+xml")
}

/**
 * Geotagged survey ("wardriving"): logs the Wi-Fi, Bluetooth LE and cellular emitters heard
 * while moving, each with the GPS position where it was strongest. Everything stays on the
 * device until the user exports a file.
 */
interface SurveyRepository {
    val state: StateFlow<SurveyState>

    /** Starts a session under the active project. Returns an error message, or null on success. */
    suspend fun start(options: SurveyOptions): String?

    fun stop()

    /** Sessions of whichever project is currently active, newest first. */
    fun observeSessions(): Flow<List<SurveySessionInfo>>

    suspend fun deleteSession(id: Long)

    /** The session rendered in [format], or null if it no longer exists. */
    suspend fun export(sessionId: Long, format: SurveyExportFormat): String?
}

data class SiteSurveyInfo(
    val id: Long,
    val name: String,
    val floorPlanPath: String?,
    val createdAt: Long,
    val pointCount: Int = 0
)

sealed interface MeasureResult {
    /** [accessPoints] networks were recorded at the point. */
    data class Recorded(val accessPoints: Int, val fresh: Boolean) : MeasureResult
    data class Failed(val reason: String) : MeasureResult
}

/** Walk-around Wi-Fi site surveys over a floor plan. */
interface SiteSurveyRepository {
    fun observeSurveys(): Flow<List<SiteSurveyInfo>>
    suspend fun get(id: Long): SiteSurveyInfo?

    /** @param floorPlanUri content URI of an image to copy into app storage, or null for a blank grid. */
    suspend fun create(name: String, floorPlanUri: String?): Long

    suspend fun delete(id: Long)

    fun observeSamples(surveyId: Long): Flow<List<SiteSample>>

    /** Scans Wi-Fi and stores every visible access point's signal at normalized ([x], [y]). */
    suspend fun measure(surveyId: Long, x: Float, y: Float): MeasureResult

    suspend fun deletePoint(surveyId: Long, pointId: Long)
}
