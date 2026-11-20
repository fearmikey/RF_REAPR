package com.fearmikey.rf_reapr.data.local

import android.content.Context
import androidx.core.content.edit
import com.fearmikey.rf_reapr.data.db.dao.EvidenceDao
import com.fearmikey.rf_reapr.domain.service.ActiveEngagement
import com.fearmikey.rf_reapr.domain.service.EngagementContext
import com.fearmikey.rf_reapr.domain.service.ScopeMatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Persists which project is the active engagement (plain SharedPreferences - it's an id,
 * not a secret) and keeps [ActiveEngagement] in sync with that project's name and scope.
 *
 * The id is read synchronously at construction so logs written right after launch are
 * tagged correctly, before the Room flow has emitted.
 */
class ActiveProjectStore(
    context: Context,
    private val evidenceDao: EvidenceDao,
    scope: CoroutineScope
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _activeProjectId = MutableStateFlow(prefs.getString(KEY_ACTIVE, null))
    val activeProjectId: StateFlow<String?> = _activeProjectId.asStateFlow()

    init {
        scope.launch {
            @OptIn(ExperimentalCoroutinesApi::class)
            _activeProjectId
                .flatMapLatest { id -> if (id == null) flowOf(null) else evidenceDao.observeProject(id) }
                .collect { project ->
                    if (project == null || project.isDeleted) {
                        // Active project was deleted (or never existed): fall back to "none".
                        if (_activeProjectId.value != null) setActive(null)
                        ActiveEngagement.publish(null)
                    } else {
                        ActiveEngagement.publish(
                            EngagementContext(
                                projectId = project.id,
                                name = project.name,
                                scopeText = project.scope,
                                scope = ScopeMatcher.parse(project.scope)
                            )
                        )
                    }
                }
        }
    }

    fun setActive(projectId: String?) {
        prefs.edit { if (projectId == null) remove(KEY_ACTIVE) else putString(KEY_ACTIVE, projectId) }
        _activeProjectId.value = projectId
    }

    companion object {
        const val PREFS_NAME = "engagement_prefs"
        private const val KEY_ACTIVE = "active_project_id"
    }
}
