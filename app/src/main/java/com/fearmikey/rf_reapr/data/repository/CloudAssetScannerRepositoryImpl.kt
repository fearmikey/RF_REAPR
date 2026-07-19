package com.fearmikey.rf_reapr.data.repository

import com.fearmikey.rf_reapr.domain.repository.CloudAssetScannerRepository
import com.fearmikey.rf_reapr.domain.repository.CloudAssetScannerRepository.CloudAsset
import com.fearmikey.rf_reapr.domain.repository.CloudAssetScannerRepository.CloudScanResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

class CloudAssetScannerRepositoryImpl(
    private val client: OkHttpClient
) : CloudAssetScannerRepository {

    override fun scanAssets(domain: String): Flow<CloudScanResult> = channelFlow {
        val baseName = domain.substringBefore(".")
        val patterns = listOf(
            // AWS S3
            "AWS S3" to "https://$domain.s3.amazonaws.com",
            "AWS S3" to "https://$baseName.s3.amazonaws.com",
            "AWS S3" to "https://$baseName-data.s3.amazonaws.com",
            "AWS S3" to "https://$baseName-backup.s3.amazonaws.com",
            "AWS S3" to "https://$baseName-public.s3.amazonaws.com",
            "AWS S3" to "https://s3.amazonaws.com/$domain",
            "AWS S3" to "https://s3.amazonaws.com/$baseName",
            
            // Google Cloud Storage
            "Google Cloud" to "https://storage.googleapis.com/$domain",
            "Google Cloud" to "https://storage.googleapis.com/$baseName",
            "Google Cloud" to "https://$domain.storage.googleapis.com",
            
            // Azure Blobs
            "Azure Blob" to "https://$baseName.blob.core.windows.net",
            "Azure Blob" to "https://$baseName-data.blob.core.windows.net"
        )

        val discovered = Collections.synchronizedList(mutableListOf<CloudAsset>())
        val processed = AtomicInteger(0)
        val total = patterns.size

        coroutineScope {
            patterns.forEach { (platform, url) ->
                launch(Dispatchers.IO) {
                    try {
                        val request = Request.Builder().url(url).head().build()
                        client.newCall(request).execute().use { response ->
                            val status = when (response.code) {
                                200 -> "Publicly Accessible"
                                403 -> "Private (Access Denied)"
                                404 -> "Not Found"
                                else -> "Status: ${response.code}"
                            }
                            
                            if (response.code != 404) {
                                discovered.add(CloudAsset(platform, url, status, response.code == 200))
                            }
                        }
                    } catch (_: Exception) {
                        // Network error or invalid URL
                    } finally {
                        val current = processed.incrementAndGet()
                        val progress = current.toFloat() / total
                        if (current % 2 == 0 || current == total) {
                            send(CloudScanResult(discovered.toList(), progress))
                        }
                    }
                }
            }
        }
        send(CloudScanResult(discovered.toList(), 1f, isFinished = true))
    }
}
