package com.fearmikey.rf_reapr.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies every schema migration preserves user data. Schemas come from app/schemas/
 * (packaged as androidTest assets in build.gradle.kts).
 *
 * When bumping AppDatabase.VERSION: add the new AutoMigration/Migration, rebuild so the
 * new schema JSON is exported, and extend this test.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val testDb = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate16To17_preservesData() {
        helper.createDatabase(testDb, 16).apply {
            execSQL(
                "INSERT INTO evidence_projects (id, name, createdAt, description, showGps, showDate, showTimestamp, isDeleted, deletedAt) " +
                    "VALUES ('p1', 'Acme audit', 1000, 'desc', 1, 1, 1, 0, NULL)"
            )
            execSQL("INSERT INTO event_logs (id, type, timestamp, summary, detailJson) VALUES (1, 'PORT', 2000, 'scan', '{}')")
            execSQL("INSERT INTO scan_sessions (id, timestamp, networkName, gatewayIp) VALUES (1, 3000, 'lab', '10.0.0.1')")
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 17, true)

        db.query("SELECT name, scope FROM evidence_projects WHERE id = 'p1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Acme audit", c.getString(0))
            assertEquals("", c.getString(1))
        }
        db.query("SELECT summary, projectId FROM event_logs WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("scan", c.getString(0))
            assertTrue(c.isNull(1))
        }
        db.query("SELECT networkName, projectId FROM scan_sessions WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("lab", c.getString(0))
            assertTrue(c.isNull(1))
        }
        db.query("SELECT COUNT(*) FROM findings").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        db.close()
    }

    @Test
    fun migrate17To18_preservesDataAndAddsNewTables() {
        helper.createDatabase(testDb, 17).apply {
            execSQL(
                "INSERT INTO evidence_projects (id, name, createdAt, description, showGps, showDate, showTimestamp, isDeleted, deletedAt, scope) " +
                    "VALUES ('p1', 'Acme audit', 1000, 'desc', 1, 1, 1, 0, NULL, '10.0.0.0/24')"
            )
            execSQL(
                "INSERT INTO evidence (id, filePath, timestamp, latitude, longitude, controlId, projectId, folderId, notes, hasStego, stegoType, isDeleted, deletedAt) " +
                    "VALUES ('e1', '/data/evidence_1.jpg', 2000, 51.5, -0.12, NULL, 'p1', NULL, 'server room', 0, NULL, 0, NULL)"
            )
            execSQL(
                "INSERT INTO findings (id, projectId, fingerprint, tool, target, severity, title, detail, remediation, status, firstSeen, lastSeen, occurrences, notes) " +
                    "VALUES (7, 'p1', 'tls scanner|h:443|tls 1.0 enabled', 'TLS Scanner', 'h:443', 'HIGH', 'TLS 1.0 enabled', 'd', NULL, 'OPEN', 3000, 3000, 1, NULL)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 18, true)

        // Existing rows survive; the new evidence columns start out empty.
        db.query("SELECT notes, sha256, findingId FROM evidence WHERE id = 'e1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("server room", c.getString(0))
            assertTrue(c.isNull(1))
            assertTrue(c.isNull(2))
        }
        db.query("SELECT title FROM findings WHERE id = 7").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("TLS 1.0 enabled", c.getString(0))
        }
        db.query("SELECT scope FROM evidence_projects WHERE id = 'p1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("10.0.0.0/24", c.getString(0))
        }

        // New tables exist and accept rows (including the column defaults and foreign keys).
        db.execSQL("UPDATE evidence SET sha256 = 'abc', findingId = 7 WHERE id = 'e1'")
        db.execSQL(
            "INSERT INTO audit_runs (startedAt, finishedAt, networkLabel, grade, score, completed, checks, hostCount, findingsJson, reportMarkdown) " +
                "VALUES (1, 2, NULL, 'B', 80, 1, 'WIFI', 0, '[]', '# r')"
        )
        db.query("SELECT projectId FROM audit_runs").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("", c.getString(0))
        }
        db.execSQL("INSERT INTO known_devices (projectId, mac, label, lastIp, notes, addedAt) VALUES ('p1', 'AA:BB:CC:DD:EE:FF', 'printer', NULL, NULL, 5)")
        db.execSQL("INSERT INTO survey_sessions (id, projectId, name, startedAt, endedAt) VALUES (1, 'p1', 'walk', 10, NULL)")
        db.execSQL(
            "INSERT INTO survey_observations (sessionId, kind, identifier, name, capabilities, frequencyMhz, channel, bestRssi, latitude, longitude, altitude, accuracyM, firstSeen, lastSeen, sightings) " +
                "VALUES (1, 'WIFI', 'AA:BB:CC:DD:EE:01', 'Corp', NULL, 2437, 6, -60, 51.5, -0.12, NULL, NULL, 10, 10, 1)"
        )
        db.execSQL("INSERT INTO site_surveys (id, projectId, name, floorPlanPath, createdAt) VALUES (1, 'p1', 'floor 2', NULL, 20)")
        db.execSQL(
            "INSERT INTO site_survey_samples (surveyId, pointId, x, y, bssid, ssid, rssi, frequencyMhz, timestamp) " +
                "VALUES (1, 100, 0.5, 0.5, 'AA:BB:CC:DD:EE:01', 'Corp', -55, 5180, 100)"
        )
        db.query("SELECT COUNT(*) FROM survey_observations").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
        db.close()
    }

    /** Opens the migrated file with the real Room database to catch any entity/schema drift. */
    @Test
    fun migrateAll_opensWithRoom() {
        helper.createDatabase(testDb, 16).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val room = Room.databaseBuilder(context, AppDatabase::class.java, testDb)
            .openHelperFactory(FrameworkSQLiteOpenHelperFactory())
            .build()
        try {
            runBlocking {
                assertNull(room.evidenceDao().getProjectById("missing"))
                assertEquals(0, room.findingDao().countForProject("").first())
                // v18 tables
                assertTrue(room.auditRunDao().getForProject("").isEmpty())
                assertTrue(room.knownDeviceDao().getForProject("").isEmpty())
                assertTrue(room.surveyDao().observeSessions("").first().isEmpty())
                assertTrue(room.surveyDao().observeSiteSurveys("").first().isEmpty())
                assertTrue(room.evidenceDao().getUnhashedEvidence().isEmpty())
            }
        } finally {
            room.close()
        }
    }
}
