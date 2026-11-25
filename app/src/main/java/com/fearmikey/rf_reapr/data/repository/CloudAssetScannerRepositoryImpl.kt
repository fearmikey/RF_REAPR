package com.fearmikey.rf_reapr.data.repository

import com.fearmikey.rf_reapr.domain.repository.CloudAssetScannerRepository
import com.fearmikey.rf_reapr.domain.repository.CloudAssetScannerRepository.CloudAsset
import com.fearmikey.rf_reapr.domain.repository.CloudAssetScannerRepository.CloudScanOptions
import com.fearmikey.rf_reapr.domain.repository.CloudAssetScannerRepository.CloudScanResult
import com.fearmikey.rf_reapr.domain.service.BucketProbe
import com.fearmikey.rf_reapr.domain.service.BucketState
import com.fearmikey.rf_reapr.domain.service.BucketVerdict
import com.fearmikey.rf_reapr.domain.service.CloudBucketClassifier
import com.fearmikey.rf_reapr.domain.service.CloudBucketPlanner
import com.fearmikey.rf_reapr.domain.service.CloudProvider
import com.fearmikey.rf_reapr.domain.service.ProbeResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.UnknownHostException
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

class CloudAssetScannerRepositoryImpl(
    client: OkHttpClient
) : CloudAssetScannerRepository {

    // Redirects are an answer in themselves here (S3 uses 301 to say "wrong region"), and a
    // missing bucket must fail fast instead of holding a connection for the default 10 s.
    private val probeClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    override fun scanAssets(domain: String, options: CloudScanOptions): Flow<CloudScanResult> = channelFlow {
        val target = CloudBucketPlanner.normalizeDomain(domain)
        if (target == null) {
            send(CloudScanResult(isFinished = true, error = "Enter a domain such as example.com."))
            return@channelFlow
        }
        val keywords = (CloudBucketPlanner.defaultKeywords + options.extraKeywords).distinct()
        val probes = CloudBucketPlanner.probes(CloudBucketPlanner.candidateNames(target, keywords), options.providers)
        if (probes.isEmpty()) {
            send(CloudScanResult(isFinished = true, error = "Select at least one provider."))
            return@channelFlow
        }

        val discovered = Collections.synchronizedList(mutableListOf<CloudAsset>())
        val processed = AtomicInteger(0)
        val failed = AtomicInteger(0)
        val total = probes.size
        val permits = Semaphore(MAX_CONCURRENT)

        fun snapshot(finished: Boolean = false) = CloudScanResult(
            discoveredAssets = synchronized(discovered) { discovered.toList() }.sortedWith(assetOrder),
            progress = if (finished) 1f else processed.get().toFloat() / total,
            isFinished = finished,
            checked = processed.get(),
            total = total,
            failedProbes = failed.get()
        )

        send(snapshot())
        coroutineScope {
            probes.forEach { probe ->
                launch {
                    permits.withPermit {
                        var found = false
                        try {
                            found = runProbe(probe, discovered, failed)
                        } finally {
                            val done = processed.incrementAndGet()
                            if (found || done % 8 == 0 || done == total) send(snapshot())
                        }
                    }
                }
            }
        }
        send(snapshot(finished = true))
    }.flowOn(Dispatchers.Default)

    /** Runs one probe plus its follow-ups; returns true when it added an asset. */
    private suspend fun runProbe(probe: BucketProbe, discovered: MutableList<CloudAsset>, failed: AtomicInteger): Boolean {
        var effective = probe
        var verdict = CloudBucketClassifier.classify(effective, fetch(effective))

        // S3 told us which region the bucket lives in: ask that region whether it is public.
        verdict.redirectRegion?.let { region ->
            effective = CloudBucketPlanner.s3Probe(probe.name, region)
            verdict = CloudBucketClassifier.classify(effective, fetch(effective))
        }

        var added = false
        when (verdict.state) {
            BucketState.NOT_FOUND -> Unit
            BucketState.UNKNOWN -> failed.incrementAndGet()
            else -> {
                discovered.add(asset(effective, verdict))
                added = true
            }
        }

        // An existing Azure storage account never lists anonymously; its containers might.
        if (probe.provider == CloudProvider.AZURE_BLOB && probe.qualifier == null && verdict.state == BucketState.EXISTS_PRIVATE) {
            CloudBucketPlanner.azureContainerProbes(probe.name).forEach { containerProbe ->
                val containerVerdict = CloudBucketClassifier.classify(containerProbe, fetch(containerProbe))
                if (containerVerdict.state == BucketState.PUBLIC_LISTING) {
                    discovered.add(asset(containerProbe, containerVerdict))
                    added = true
                }
            }
        }
        return added
    }

    private fun asset(probe: BucketProbe, verdict: BucketVerdict) = CloudAsset(
        platform = probe.provider.label + (probe.qualifier?.let { " ($it)" } ?: ""),
        url = probe.url.substringBefore('?'),
        status = verdict.note,
        isPublic = verdict.state == BucketState.PUBLIC_LISTING || verdict.state == BucketState.PUBLIC_READ,
        state = verdict.state,
        name = probe.name,
        host = probe.host,
        sampleKeys = verdict.sampleKeys,
        draft = CloudBucketClassifier.toDraft(probe, verdict)
    )

    /** One anonymous GET. Never throws: failures become a [ProbeResponse] the classifier understands. */
    private suspend fun fetch(probe: BucketProbe): ProbeResponse = suspendCancellableCoroutine { continuation ->
        val call = probeClient.newCall(Request.Builder().url(probe.url).header("User-Agent", "RF-REAPR cloud asset check").build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resume(ProbeResponse(null, dnsFailed = e is UnknownHostException))
            }

            override fun onResponse(call: Call, response: Response) {
                val result = response.use {
                    // Only the first few kilobytes matter (the listing header and a handful of keys).
                    val body = runCatching { it.peekBody(BODY_LIMIT).string() }.getOrDefault("")
                    ProbeResponse(it.code, body, it.header("x-amz-bucket-region"))
                }
                if (continuation.isActive) continuation.resume(result)
            }
        })
    }

    private companion object {
        const val MAX_CONCURRENT = 10
        const val BODY_LIMIT = 16L * 1024

        /** Public first, then private, alphabetical within each group. */
        val assetOrder: Comparator<CloudAsset> = compareBy<CloudAsset> { it.state.ordinal }.thenBy { it.host }
    }
}
