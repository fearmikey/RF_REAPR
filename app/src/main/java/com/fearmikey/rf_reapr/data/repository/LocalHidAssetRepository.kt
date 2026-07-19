package com.fearmikey.rf_reapr.data.repository

import android.content.Context
import com.fearmikey.rf_reapr.data.db.dao.HidAssetDao
import com.fearmikey.rf_reapr.data.db.entity.HidAssetEntity
import com.fearmikey.rf_reapr.domain.model.HidAsset
import com.fearmikey.rf_reapr.domain.repository.HidAssetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.UUID

class LocalHidAssetRepository(
    private val context: Context,
    private val hidAssetDao: HidAssetDao
) : HidAssetRepository {

    private val assetsDir = File(context.filesDir, "hid_retrieved").apply {
        if (!exists()) mkdirs()
    }

    override fun getAllAssets(): Flow<List<HidAsset>> {
        return hidAssetDao.getAllAssets().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun saveAsset(name: String, content: String): HidAsset {
        val id = UUID.randomUUID().toString()
        val file = File(assetsDir, "$id-$name")
        file.writeText(content)
        
        val asset = HidAsset(
            id = id,
            name = name,
            path = file.absolutePath,
            timestamp = System.currentTimeMillis(),
            size = file.length()
        )
        
        hidAssetDao.insertAsset(HidAssetEntity.fromDomain(asset))
        return asset
    }

    override suspend fun deleteAsset(asset: HidAsset) {
        val file = File(asset.path)
        if (file.exists()) file.delete()
        hidAssetDao.deleteAsset(HidAssetEntity.fromDomain(asset))
    }

    override fun getAssetFile(asset: HidAsset): File {
        return File(asset.path)
    }
}
