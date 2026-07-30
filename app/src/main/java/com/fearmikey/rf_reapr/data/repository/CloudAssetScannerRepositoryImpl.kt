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
        val patterns = generatePatterns(domain)
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
                        if (current % 10 == 0 || current == total) {
                            send(CloudScanResult(discovered.toList(), progress))
                        }
                    }
                }
            }
        }
        send(CloudScanResult(discovered.toList(), 1f, isFinished = true))
    }

    private fun generatePatterns(domain: String): List<Pair<String, String>> {
        val baseName = domain.substringBefore(".")
        val keywords = listOf(
            "dev", "prod", "staging", "test", "assets", "backup", "data", "logs",
            "config", "static", "public", "private", "internal", "files", "media", "web", "storage"
        )
        val awsRegions = listOf("us-east-1", "us-west-1", "us-west-2", "eu-west-1", "eu-central-1")
        val doRegions = listOf("nyc3", "ams3", "sgp1", "sfo2")
        val linodeRegions = listOf("us-east-1", "eu-central-1", "ap-south-1")

        val patterns = mutableListOf<Pair<String, String>>()

        fun addPermutations(platform: String, format: (String) -> String) {
            patterns.add(platform to format(baseName))
            patterns.add(platform to format(domain))
            keywords.forEach { kw ->
                patterns.add(platform to format("$baseName-$kw"))
                patterns.add(platform to format("$kw-$baseName"))
                patterns.add(platform to format("$baseName.$kw"))
                patterns.add(platform to format("$kw.$baseName"))
            }
        }

        // AWS S3
        addPermutations("AWS S3") { "https://$it.s3.amazonaws.com" }
        awsRegions.forEach { region ->
            addPermutations("AWS S3 ($region)") { "https://$it.s3.$region.amazonaws.com" }
        }

        // Google Cloud Storage
        addPermutations("Google Cloud") { "https://storage.googleapis.com/$it" }
        addPermutations("Google Cloud") { "https://$it.storage.googleapis.com" }

        // Azure Blobs
        addPermutations("Azure Blob") { "https://$it.blob.core.windows.net" }

        // DigitalOcean Spaces
        doRegions.forEach { region ->
            addPermutations("DigitalOcean ($region)") { "https://$it.$region.digitaloceanspaces.com" }
        }

        // Linode Objects
        linodeRegions.forEach { region ->
            addPermutations("Linode ($region)") { "https://$it.$region.linodeobjects.com" }
        }

        return patterns.distinctBy { it.second }
    }
}
