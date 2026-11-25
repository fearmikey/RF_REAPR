package com.fearmikey.rf_reapr.data.repository

import android.content.Context
import android.net.Uri
import com.fearmikey.rf_reapr.data.db.dao.SurveyDao
import com.fearmikey.rf_reapr.data.db.entity.SiteSurveyEntity
import com.fearmikey.rf_reapr.data.db.entity.SiteSurveySampleEntity
import com.fearmikey.rf_reapr.data.local.ActiveProjectStore
import com.fearmikey.rf_reapr.data.survey.WifiScanSource
import com.fearmikey.rf_reapr.domain.repository.MeasureResult
import com.fearmikey.rf_reapr.domain.repository.SiteSurveyInfo
import com.fearmikey.rf_reapr.domain.repository.SiteSurveyRepository
import com.fearmikey.rf_reapr.domain.service.SiteSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class SiteSurveyRepositoryImpl(
    context: Context,
    private val dao: SurveyDao,
    private val activeProjectStore: ActiveProjectStore
) : SiteSurveyRepository {

    private val appContext = context.applicationContext
    private val wifiSource = WifiScanSource(appContext)

    /** One measurement at a time: overlapping scans would attribute results to the wrong point. */
    private val measureLock = Mutex()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeSurveys(): Flow<List<SiteSurveyInfo>> =
        activeProjectStore.activeProjectId.flatMapLatest { projectId ->
            dao.observeSiteSurveys(projectId.orEmpty()).map { list ->
                list.map { SiteSurveyInfo(it.id, it.name, it.floorPlanPath, it.createdAt, it.pointCount) }
            }
        }

    override suspend fun get(id: Long): SiteSurveyInfo? =
        dao.getSiteSurvey(id)?.let { SiteSurveyInfo(it.id, it.name, it.floorPlanPath, it.createdAt) }

    override suspend fun create(name: String, floorPlanUri: String?): Long {
        val planPath = floorPlanUri?.let { copyFloorPlan(Uri.parse(it)) }
        return dao.insertSiteSurvey(
            SiteSurveyEntity(
                projectId = activeProjectStore.activeProjectId.value.orEmpty(),
                name = name.trim().ifBlank { "Site survey" },
                floorPlanPath = planPath,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    /** Copies the picked image into filesDir (covered by encrypted backups); null if it can't be read. */
    private suspend fun copyFloorPlan(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(appContext.filesDir, "floorplans").apply { mkdirs() }
            val target = File(dir, "plan_${System.currentTimeMillis()}.img")
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching null
            target.absolutePath
        }.getOrNull()
    }

    override suspend fun delete(id: Long) {
        dao.getSiteSurvey(id)?.floorPlanPath?.let { path -> withContext(Dispatchers.IO) { File(path).delete() } }
        dao.deleteSiteSurvey(id)
    }

    override fun observeSamples(surveyId: Long): Flow<List<SiteSample>> =
        dao.observeSamples(surveyId).map { list ->
            list.map { SiteSample(it.pointId, it.x, it.y, it.bssid, it.ssid, it.rssi, it.frequencyMhz) }
        }

    override suspend fun measure(surveyId: Long, x: Float, y: Float): MeasureResult = measureLock.withLock {
        if (!wifiSource.hasPermission()) return@withLock MeasureResult.Failed("Location permission is needed to read Wi-Fi scan results")
        if (!wifiSource.isWifiEnabled) return@withLock MeasureResult.Failed("Turn Wi-Fi on to measure signal strength")

        val (readings, fresh) = wifiSource.freshScan()
        // Throttled requests fall back to cached results; those are only valid for this
        // spot if they are recent (the user has just walked here).
        val usable = if (fresh) readings else readings.filter { it.ageMs <= MAX_CACHED_AGE_MS }
        if (usable.isEmpty()) {
            return@withLock MeasureResult.Failed(
                if (readings.isEmpty()) "No access points were visible here"
                else "Android is limiting Wi-Fi scans (4 per 2 minutes). Wait about 30 seconds, or turn off " +
                    "\"Wi-Fi scan throttling\" in Developer options for faster surveys."
            )
        }
        val now = System.currentTimeMillis()
        dao.insertSamples(
            usable.map { r ->
                SiteSurveySampleEntity(
                    surveyId = surveyId, pointId = now, x = x.coerceIn(0f, 1f), y = y.coerceIn(0f, 1f),
                    bssid = r.bssid, ssid = r.ssid, rssi = r.rssi, frequencyMhz = r.frequencyMhz, timestamp = now
                )
            }
        )
        MeasureResult.Recorded(usable.size, fresh)
    }

    override suspend fun deletePoint(surveyId: Long, pointId: Long) = dao.deletePoint(surveyId, pointId)

    private companion object {
        const val MAX_CACHED_AGE_MS = 10_000L
    }
}
