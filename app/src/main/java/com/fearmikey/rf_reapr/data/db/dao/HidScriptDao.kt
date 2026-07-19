package com.fearmikey.rf_reapr.data.db.dao

import androidx.room.*
import com.fearmikey.rf_reapr.data.db.entity.HidScriptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HidScriptDao {
    @Query("SELECT * FROM hid_scripts ORDER BY createdAt DESC")
    fun getAllScripts(): Flow<List<HidScriptEntity>>

    @Query("SELECT * FROM hid_scripts WHERE id = :id")
    suspend fun getScriptById(id: Long): HidScriptEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScript(script: HidScriptEntity): Long

    @Delete
    suspend fun deleteScript(script: HidScriptEntity)
}
