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
            }
        } finally {
            room.close()
        }
    }
}
