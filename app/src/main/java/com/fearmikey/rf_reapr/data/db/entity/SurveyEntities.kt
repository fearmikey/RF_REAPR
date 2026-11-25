package com.fearmikey.rf_reapr.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A geotagged survey ("wardriving") session (added in DB v18). */
@Entity(tableName = "survey_sessions", indices = [Index("projectId")])
data class SurveySessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(defaultValue = "")
    val projectId: String = "",
    val name: String,
    val startedAt: Long,
    val endedAt: Long? = null
)

/**
 * One emitter (access point, BLE device or cell) seen during a survey session. There is a
 * single row per emitter per session, holding the position where it was heard strongest,
 * so a long drive doesn't grow the table by one row per scan result.
 */
@Entity(
    tableName = "survey_observations",
    foreignKeys = [
        ForeignKey(
            entity = SurveySessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId", "kind", "identifier"], unique = true)]
)
data class SurveyObservationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    /** WIFI, BLE or CELL (SurveyKind.name). */
    val kind: String,
    /** BSSID, BLE address, or a cell key such as "LTE 310-260-12345-67". */
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

/** A Wi-Fi site survey over a floor plan (added in DB v18). */
@Entity(tableName = "site_surveys", indices = [Index("projectId")])
data class SiteSurveyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(defaultValue = "")
    val projectId: String = "",
    val name: String,
    /** Private copy of the floor plan image in filesDir; null = blank grid. */
    val floorPlanPath: String? = null,
    val createdAt: Long
)

/**
 * One access point's signal at one measurement point. All rows from the same tap share
 * [pointId]; [x]/[y] are normalized (0..1) positions on the floor plan.
 */
@Entity(
    tableName = "site_survey_samples",
    foreignKeys = [
        ForeignKey(
            entity = SiteSurveyEntity::class,
            parentColumns = ["id"],
            childColumns = ["surveyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("surveyId")]
)
data class SiteSurveySampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val surveyId: Long,
    val pointId: Long,
    val x: Float,
    val y: Float,
    val bssid: String,
    val ssid: String,
    val rssi: Int,
    val frequencyMhz: Int,
    val timestamp: Long
)
