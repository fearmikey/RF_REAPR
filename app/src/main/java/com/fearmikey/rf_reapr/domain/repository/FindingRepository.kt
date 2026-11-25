package com.fearmikey.rf_reapr.domain.repository

import com.fearmikey.rf_reapr.domain.model.Finding
import com.fearmikey.rf_reapr.domain.model.FindingDraft
import com.fearmikey.rf_reapr.domain.model.FindingStatus
import com.fearmikey.rf_reapr.domain.service.FindingMerger
import kotlinx.coroutines.flow.Flow

/**
 * Persistent, de-duplicated security findings. Tools call [record]; findings are tagged
 * with the project that is active at that moment.
 */
interface FindingRepository {
    /** Findings of [projectId]; null = findings recorded with no active project. */
    fun observe(projectId: String?): Flow<List<Finding>>

    /** Findings of whichever project is currently active (follows project switches). */
    fun observeActive(): Flow<List<Finding>>

    suspend fun getForProject(projectId: String?): List<Finding>

    /** Merge tool output into the store (see [FindingMerger]). Safe to call repeatedly. */
    suspend fun record(drafts: List<FindingDraft>): FindingMerger.Plan

    suspend fun setStatus(id: Long, status: FindingStatus)
    suspend fun setNotes(id: Long, notes: String?)
    suspend fun delete(id: Long)

    /** Removes every finding of [projectId]; null = findings recorded with no active project. */
    suspend fun deleteAll(projectId: String?)
}
