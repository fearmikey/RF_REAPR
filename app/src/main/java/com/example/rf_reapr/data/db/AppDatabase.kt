package com.example.rf_reapr.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.rf_reapr.data.db.converter.RoomConverters
import com.example.rf_reapr.data.db.dao.ComplianceDao
import com.example.rf_reapr.data.db.dao.EvidenceDao
import com.example.rf_reapr.data.db.dao.EventLogDao
import com.example.rf_reapr.data.db.dao.NetworkDao
import com.example.rf_reapr.data.db.dao.ScanSessionDao
import com.example.rf_reapr.data.db.entity.ComplianceFindingEntity
import com.example.rf_reapr.data.db.entity.EvidenceEntity
import com.example.rf_reapr.data.db.entity.EvidenceFolderEntity
import com.example.rf_reapr.data.db.entity.EvidenceProjectEntity
import com.example.rf_reapr.data.db.entity.EventLogEntity
import com.example.rf_reapr.data.db.entity.NetworkNodeEntity
import com.example.rf_reapr.data.db.entity.ScanSessionEntity
import com.example.rf_reapr.data.db.entity.SessionNodeCrossRef

@Database(
    entities = [
        ScanSessionEntity::class,
        NetworkNodeEntity::class,
        SessionNodeCrossRef::class,
        EventLogEntity::class,
        ComplianceFindingEntity::class,
        EvidenceEntity::class,
        EvidenceProjectEntity::class,
        EvidenceFolderEntity::class
    ],
    version = 8,
    exportSchema = false
)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun networkDao(): NetworkDao
    abstract fun scanSessionDao(): ScanSessionDao
    abstract fun eventLogDao(): EventLogDao
    abstract fun complianceDao(): ComplianceDao
    abstract fun evidenceDao(): EvidenceDao

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
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
