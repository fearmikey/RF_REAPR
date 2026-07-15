package com.fearmikey.rf_reapr.data.repository

import com.fearmikey.rf_reapr.domain.repository.PingRepository
import com.fearmikey.rf_reapr.domain.repository.PingRepository.PingStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.BufferedReader
import java.io.InputStreamReader

class PingRepositoryImpl : PingRepository {

    override fun ping(host: String, count: Int?): Flow<PingStatus> = flow {
        emit(PingStatus.Loading)
        var process: Process? = null
        try {
            val command = if (count != null) {
                "/system/bin/ping -c $count $host"
            } else {
                "/system/bin/ping $host"
            }
            
            process = Runtime.getRuntime().exec(command)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            
            while (currentCoroutineContext().isActive) {
                if (reader.ready()) {
                    line = reader.readLine()
                    if (line == null) break
                    output.append(line).append("\n")
                    emit(PingStatus.Progress(line!!))
                } else {
                    // Check if process has exited
                    try {
                        val exitVal = process.exitValue()
                        // Process finished, read remaining lines
                        while (reader.readLine().also { line = it } != null) {
                            output.append(line).append("\n")
                            emit(PingStatus.Progress(line!!))
                        }
                        break
                    } catch (e: IllegalThreadStateException) {
                        // Process still running, wait a bit
                        kotlinx.coroutines.delay(100)
                    }
                }
            }
            
            if (!currentCoroutineContext().isActive) {
                process.destroy()
                emit(PingStatus.Error("Ping cancelled"))
                return@flow
            }

            val exitCode = process.waitFor()
            if (exitCode == 0 || output.isNotEmpty()) {
                emit(PingStatus.Success(output.toString()))
            } else {
                emit(PingStatus.Error("Ping failed with exit code $exitCode"))
            }
        } catch (e: Exception) {
            emit(PingStatus.Error(e.message ?: "Unknown error occurred during ping"))
        } finally {
            process?.destroy()
        }
    }.flowOn(Dispatchers.IO)
}
