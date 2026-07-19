package com.fearmikey.rf_reapr.data.db.dao

import androidx.room.*
import com.fearmikey.rf_reapr.data.db.entity.HidAssetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HidAssetDao {
    @Query("SELECT * FROM hid_assets ORDER BY timestamp DESC")
    fun getAllAssets(): Flow<List<HidAssetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAsset(asset: HidAssetEntity)

    @Delete
    suspend fun deleteAsset(asset: HidAssetEntity)

    @Query("SELECT * FROM hid_assets WHERE id = :id")
    suspend fun getAssetById(id: String): HidAssetEntity?
}
