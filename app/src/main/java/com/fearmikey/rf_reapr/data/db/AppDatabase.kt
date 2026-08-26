package com.fearmikey.rf_reapr.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.fearmikey.rf_reapr.data.db.converter.RoomConverters
import com.fearmikey.rf_reapr.data.db.dao.ComplianceDao
import com.fearmikey.rf_reapr.data.db.dao.EvidenceDao
import com.fearmikey.rf_reapr.data.db.dao.EventLogDao
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
    ],
    version = 16,
    exportSchema = false
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

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "reapr_database"
                )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
