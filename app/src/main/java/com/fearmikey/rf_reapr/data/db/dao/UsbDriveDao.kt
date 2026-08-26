package com.fearmikey.rf_reapr.data.db.dao

import androidx.room.*
import com.fearmikey.rf_reapr.data.db.entity.UsbDriveEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UsbDriveDao {
    @Query("SELECT * FROM usb_drives ORDER BY lastUsed DESC")
    fun getAllDrives(): Flow<List<UsbDriveEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDrive(drive: UsbDriveEntity)

    @Query("DELETE FROM usb_drives WHERE id = :id")
    suspend fun deleteDrive(id: String)

    @Query("SELECT * FROM usb_drives WHERE id = :id")
    suspend fun getDriveById(id: String): UsbDriveEntity?
}
