package com.example.rf_reapr.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.rf_reapr.data.db.AppDatabase
import com.example.rf_reapr.data.repository.EvidenceRepositoryImpl
import java.util.concurrent.TimeUnit

class RecycleBinWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = EvidenceRepositoryImpl(database.evidenceDao(), applicationContext)
        
        // Items older than 30 days
        val threshold = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
        
        return try {
            repository.purgeExpiredEvidence(threshold)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
