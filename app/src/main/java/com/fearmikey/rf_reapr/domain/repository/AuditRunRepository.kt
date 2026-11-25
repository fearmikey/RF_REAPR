package com.fearmikey.rf_reapr.domain.repository

import com.fearmikey.rf_reapr.domain.model.AuditRun
import com.fearmikey.rf_reapr.domain.model.AuditRunState
import kotlinx.coroutines.flow.Flow

/** History of finished Audit Mode runs, newest first, per project. */
interface AuditRunRepository {
    /** Runs of whichever project is currently active (follows project switches). */
    fun observeActive(): Flow<List<AuditRun>>

    /**
     * Stores a finished run under the active project. Returns the stored run, or null when
     * nothing ran to completion (so there is nothing worth keeping).
     */
    suspend fun save(state: AuditRunState): AuditRun?

    suspend fun delete(id: Long)
}
