package com.fearmikey.rf_reapr.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fearmikey.rf_reapr.domain.service.ActiveEngagement
import com.fearmikey.rf_reapr.domain.service.ScopeMatcher

/**
 * Warns when [target] falls outside the active project's authorized scope. Renders
 * nothing when no project/scope is set, the target is blank, or it is in scope.
 * Drop it under any target input field.
 */
@Composable
fun ScopeWarning(target: String, modifier: Modifier = Modifier) {
    val engagement by ActiveEngagement.current.collectAsStateWithLifecycle()
    val context = engagement ?: return
    if (target.isBlank()) return
    val verdict = remember(target, context) { context.scope.check(target) }
    val message = when (verdict) {
        ScopeMatcher.Verdict.OUT_OF_SCOPE -> "Not in the authorized scope of \"${context.name}\". Confirm you have permission before scanning."
        ScopeMatcher.Verdict.EXCLUDED -> "Explicitly excluded from the scope of \"${context.name}\"."
        else -> return
    }
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.GppMaybe,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp)
            )
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}
