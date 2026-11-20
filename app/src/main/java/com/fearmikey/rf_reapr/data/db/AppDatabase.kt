package com.fearmikey.rf_reapr.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.fearmikey.rf_reapr.data.db.converter.RoomConverters
import com.fearmikey.rf_reapr.data.db.dao.ComplianceDao
import com.fearmikey.rf_reapr.data.db.dao.EvidenceDao
import com.fearmikey.rf_reapr.data.db.dao.EventLogDao
import com.fearmikey.rf_reapr.data.db.dao.FindingDao
import com.fearmikey.rf_reapr.data.db.dao.HidAssetDao
import com.fearmikey.rf_reapr.data.db.dao.HidScriptDao
import com.fearmikey.rf_reapr.data.db.dao.NetworkDao
import com.fearmikey.rf_reapr.data.db.dao.ScanSessionDao
import com.fearmikey.rf_reapr.data.db.dao.UsbDriveDao
import com.fearmikey.rf_reapr.data.db.dao.VulnerabilityDao
import com.fearmikey.rf_reapr.data.db.entity.VulnerabilityEntity
import com.fearmikey.rf_reapr.data.db.entity.ComplianceFindingEntity
import com.fearmikey.rf_reapr.data.db.entity.EvidenceEntity
import com.fearmikey.rf_reapr.data.db.entity.EvidenceFolderEntity
import com.fearmikey.rf_reapr.data.db.entity.EvidenceProjectEntity
import com.fearmikey.rf_reapr.data.db.entity.EventLogEntity
import com.fearmikey.rf_reapr.data.db.entity.FindingEntity
import com.fearmikey.rf_reapr.data.db.entity.HidAssetEntity
import com.fearmikey.rf_reapr.data.db.entity.HidScriptEntity
import com.fearmikey.rf_reapr.data.db.entity.NetworkNodeEntity
import com.fearmikey.rf_reapr.data.db.entity.ScanSessionEntity
import com.fearmikey.rf_reapr.data.db.entity.SessionNodeCrossRef
import com.fearmikey.rf_reapr.data.db.entity.UsbDriveEntity

@Database(
    entities = [
        ScanSessionEntity::class,
        NetworkNodeEntity::class,
        SessionNodeCrossRef::class,
        EventLogEntity::class,
        ComplianceFindingEntity::class,
        EvidenceEntity::class,
        EvidenceProjectEntity::class,
        EvidenceFolderEntity::class,
        VulnerabilityEntity::class,
        HidScriptEntity::class,
        HidAssetEntity::class,
        UsbDriveEntity::class,
        FindingEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
    autoMigrations = [
        // v17: findings table; projectId on event_logs/scan_sessions; scope on evidence_projects.
        AutoMigration(from = 16, to = 17),
    ]
)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun networkDao(): NetworkDao
    abstract fun scanSessionDao(): ScanSessionDao
    abstract fun eventLogDao(): EventLogDao
    abstract fun complianceDao(): ComplianceDao
    abstract fun evidenceDao(): EvidenceDao
    abstract fun vulnerabilityDao(): VulnerabilityDao
    abstract fun hidScriptDao(): HidScriptDao
    abstract fun hidAssetDao(): HidAssetDao
    abstract fun usbDriveDao(): UsbDriveDao
    abstract fun findingDao(): FindingDao

    companion object {
        /**
         * Current schema version. Every version from 16 on is exported to app/schemas/ and
         * MUST ship with a migration (prefer AutoMigration) - user data is local-only, so a
         * destructive migration would silently wipe evidence, checklists and findings.
         */
        const val VERSION = 17
        const val NAME = "reapr_database"

        /** Versions released before schemas were exported; these cannot be migrated. */
        private val UNMIGRATABLE_LEGACY_VERSIONS = (1..15).toList().toIntArray()

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    NAME
                )
                // Only pre-v16 databases (no exported schema) are reset. Any other missing
                // migration is a bug and must crash in testing rather than wipe user data.
                .fallbackToDestructiveMigrationFrom(dropAllTables = true, *UNMIGRATABLE_LEGACY_VERSIONS)
                .build()
                INSTANCE = instance
                instance
            }
        }

        /** Closes and forgets the singleton (used by backup restore before swapping files). */
        fun closeInstance() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}
