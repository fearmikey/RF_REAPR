package com.fearmikey.rf_reapr.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.fearmikey.rf_reapr.data.db.entity.SiteSurveyEntity
import com.fearmikey.rf_reapr.data.db.entity.SiteSurveySampleEntity
import com.fearmikey.rf_reapr.data.db.entity.SurveyObservationEntity
import com.fearmikey.rf_reapr.data.db.entity.SurveySessionEntity
import kotlinx.coroutines.flow.Flow

/** Row for the session list: a session plus how many emitters it logged. */
data class SurveySessionSummary(
    val id: Long,
    val projectId: String,
    val name: String,
    val startedAt: Long,
    val endedAt: Long?,
    val wifiCount: Int,
    val bleCount: Int,
    val cellCount: Int
)

/** Row for the site survey list: a survey plus how many points were measured. */
data class SiteSurveySummary(
    val id: Long,
    val projectId: String,
    val name: String,
    val floorPlanPath: String?,
    val createdAt: Long,
    val pointCount: Int
)

/** Geotagged survey sessions and floor-plan site surveys (both added in DB v18). */
@Dao
interface SurveyDao {

    // ---- Geotagged survey --------------------------------------------------------------

    @Insert
    suspend fun insertSession(session: SurveySessionEntity): Long

    @Query("UPDATE survey_sessions SET endedAt = :endedAt WHERE id = :id")
    suspend fun endSession(id: Long, endedAt: Long)

    /** Sessions left open by a process death are closed at their last observation. */
    @Query(
        "UPDATE survey_sessions SET endedAt = COALESCE(" +
            "(SELECT MAX(lastSeen) FROM survey_observations WHERE sessionId = survey_sessions.id), startedAt) " +
            "WHERE endedAt IS NULL"
    )
    suspend fun closeOrphanedSessions()

    @Query("DELETE FROM survey_sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("DELETE FROM survey_sessions WHERE projectId = :projectId")
    suspend fun deleteSessionsForProject(projectId: String)

    @Query(
        "SELECT s.id, s.projectId, s.name, s.startedAt, s.endedAt, " +
            "(SELECT COUNT(*) FROM survey_observations o WHERE o.sessionId = s.id AND o.kind = 'WIFI') AS wifiCount, " +
            "(SELECT COUNT(*) FROM survey_observations o WHERE o.sessionId = s.id AND o.kind = 'BLE') AS bleCount, " +
            "(SELECT COUNT(*) FROM survey_observations o WHERE o.sessionId = s.id AND o.kind = 'CELL') AS cellCount " +
            "FROM survey_sessions s WHERE s.projectId = :projectId ORDER BY s.startedAt DESC"
    )
    fun observeSessions(projectId: String): Flow<List<SurveySessionSummary>>

    @Query("SELECT * FROM survey_sessions WHERE id = :id")
    suspend fun getSession(id: Long): SurveySessionEntity?

    @Query("SELECT * FROM survey_observations WHERE sessionId = :sessionId ORDER BY firstSeen")
    suspend fun getObservations(sessionId: Long): List<SurveyObservationEntity>

    @Query("SELECT * FROM survey_observations WHERE sessionId = :sessionId AND kind = :kind AND identifier IN (:identifiers)")
    suspend fun findObservations(sessionId: Long, kind: String, identifiers: List<String>): List<SurveyObservationEntity>

    @Insert
    suspend fun insertObservations(observations: List<SurveyObservationEntity>)

    @Update
    suspend fun updateObservations(observations: List<SurveyObservationEntity>)

    @Transaction
    suspend fun upsertObservations(inserts: List<SurveyObservationEntity>, updates: List<SurveyObservationEntity>) {
        if (inserts.isNotEmpty()) insertObservations(inserts)
        if (updates.isNotEmpty()) updateObservations(updates)
    }

    // ---- Floor-plan site survey --------------------------------------------------------

    @Insert
    suspend fun insertSiteSurvey(survey: SiteSurveyEntity): Long

    @Query("SELECT * FROM site_surveys WHERE id = :id")
    suspend fun getSiteSurvey(id: Long): SiteSurveyEntity?

    @Query("SELECT * FROM site_surveys WHERE projectId = :projectId")
    suspend fun getSiteSurveysForProject(projectId: String): List<SiteSurveyEntity>

    @Query(
        "SELECT s.id, s.projectId, s.name, s.floorPlanPath, s.createdAt, " +
            "(SELECT COUNT(DISTINCT pointId) FROM site_survey_samples p WHERE p.surveyId = s.id) AS pointCount " +
            "FROM site_surveys s WHERE s.projectId = :projectId ORDER BY s.createdAt DESC"
    )
    fun observeSiteSurveys(projectId: String): Flow<List<SiteSurveySummary>>

    @Query("DELETE FROM site_surveys WHERE id = :id")
    suspend fun deleteSiteSurvey(id: Long)

    @Query("DELETE FROM site_surveys WHERE projectId = :projectId")
    suspend fun deleteSiteSurveysForProject(projectId: String)

    @Insert
    suspend fun insertSamples(samples: List<SiteSurveySampleEntity>)

    @Query("SELECT * FROM site_survey_samples WHERE surveyId = :surveyId ORDER BY timestamp")
    fun observeSamples(surveyId: Long): Flow<List<SiteSurveySampleEntity>>

    @Query("DELETE FROM site_survey_samples WHERE surveyId = :surveyId AND pointId = :pointId")
    suspend fun deletePoint(surveyId: Long, pointId: Long)
}
