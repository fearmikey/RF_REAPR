package com.fearmikey.rf_reapr.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fearmikey.rf_reapr.data.db.entity.KnownDeviceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KnownDeviceDao {
    @Query("SELECT * FROM known_devices WHERE projectId = :projectId ORDER BY label COLLATE NOCASE")
    fun observeForProject(projectId: String): Flow<List<KnownDeviceEntity>>

    @Query("SELECT * FROM known_devices WHERE projectId = :projectId")
    suspend fun getForProject(projectId: String): List<KnownDeviceEntity>

    /** Keeps the existing row (and its label/notes) when the MAC is already listed. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(devices: List<KnownDeviceEntity>): List<Long>

    @Query("UPDATE known_devices SET label = :label, notes = :notes WHERE id = :id")
    suspend fun update(id: Long, label: String, notes: String?)

    @Query("DELETE FROM known_devices WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM known_devices WHERE projectId = :projectId AND mac = :mac")
    suspend fun deleteByMac(projectId: String, mac: String)

    @Query("DELETE FROM known_devices WHERE projectId = :projectId")
    suspend fun deleteForProject(projectId: String)
}
