package com.fearmikey.rf_reapr.domain.repository

import com.fearmikey.rf_reapr.domain.model.HidAsset
import kotlinx.coroutines.flow.Flow
import java.io.File

interface HidAssetRepository {
    fun getAllAssets(): Flow<List<HidAsset>>
    suspend fun saveAsset(name: String, content: String): HidAsset
    suspend fun deleteAsset(asset: HidAsset)
    fun getAssetFile(asset: HidAsset): File
}
