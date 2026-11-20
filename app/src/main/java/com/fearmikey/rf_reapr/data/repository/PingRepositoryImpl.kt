package com.fearmikey.rf_reapr.data.repository

import com.fearmikey.rf_reapr.domain.repository.PingOptions
import com.fearmikey.rf_reapr.domain.repository.PingRepository
import com.fearmikey.rf_reapr.domain.repository.PingRepository.PingStatus
import com.fearmikey.rf_reapr.domain.service.HostValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.util.Locale

class PingRepositoryImpl : PingRepository {

    override fun ping(options: PingOptions): Flow<PingStatus> = callbackFlow {
        send(PingStatus.Loading)

        val host = HostValidator.normalize(options.host)
        if (host == null) {
            send(PingStatus.Error("Invalid host: enter a hostname or IP address"))
            close()
            return@callbackFlow
        }

        val process = try {
            ProcessBuilder(buildCommand(options, host)).redirectErrorStream(true).start()
        } catch (e: Exception) {
            send(PingStatus.Error("Could not start ping: ${e.message}"))
            close()
            return@callbackFlow
        }

        // Blocking reads happen on IO; cancelling the flow destroys the process, which
        // unblocks readLine() so the reader can finish.
        launch(Dispatchers.IO) {
            val output = StringBuilder()
            try {
                process.inputStream.bufferedReader().use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        output.append(line).append('\n')
                        send(PingStatus.Progress(line))
                    }
                }
                val exitCode = process.waitFor()
                if (output.isEmpty() && exitCode != 0) {
                    send(PingStatus.Error("Ping failed with exit code $exitCode"))
                } else {
                    send(PingStatus.Success(output.toString(), exitCode))
                }
            } catch (_: Exception) {
                // Stream closed by cancellation.
            } finally {
                close()
            }
        }

        awaitClose { process.destroy() }
    }.flowOn(Dispatchers.IO)

    private fun buildCommand(options: PingOptions, host: String): List<String> = buildList {
        add(if (HostValidator.isIpv6(host)) "/system/bin/ping6" else "/system/bin/ping")
        options.count?.let { add("-c"); add(it.coerceIn(1, 10_000).toString()) }
        if (options.intervalSec != 1f) {
            add("-i"); add(String.format(Locale.US, "%.1f", options.intervalSec.coerceAtLeast(0.2f)))
        }
        if (options.packetSize != 56) {
            add("-s"); add(options.packetSize.coerceIn(0, 65_507).toString())
        }
        add("-W"); add(options.timeoutSec.coerceIn(1, 30).toString())
        if (options.dontFragment) {
            add("-M"); add("do")
        }
        add(host)
    }
}
