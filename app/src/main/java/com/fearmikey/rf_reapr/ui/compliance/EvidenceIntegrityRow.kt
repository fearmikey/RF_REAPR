package com.fearmikey.rf_reapr.ui.compliance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.fearmikey.rf_reapr.domain.model.Evidence
import com.fearmikey.rf_reapr.domain.service.EvidenceHasher
import com.fearmikey.rf_reapr.domain.service.EvidenceIntegrity

/**
 * Capture hash of an evidence item and whether the file still matches it. The file is
 * re-hashed whenever the row is shown; tapping copies the full SHA-256.
 */
@Composable
fun EvidenceIntegrityRow(
    evidence: Evidence,
    verify: suspend (Evidence) -> EvidenceIntegrity,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    var integrity by remember(evidence.id, evidence.sha256, evidence.filePath) { mutableStateOf<EvidenceIntegrity?>(null) }
    LaunchedEffect(evidence.id, evidence.sha256, evidence.filePath) { integrity = verify(evidence) }
    val clipboard = LocalClipboardManager.current

    val (icon, tint) = when (integrity) {
        EvidenceIntegrity.VERIFIED -> Icons.Default.VerifiedUser to Color(0xFF66BB6A)
        EvidenceIntegrity.MODIFIED, EvidenceIntegrity.MISSING -> Icons.Default.ErrorOutline to MaterialTheme.colorScheme.error
        else -> Icons.Default.Fingerprint to contentColor
    }
    Row(
        modifier = modifier.clickable(enabled = evidence.sha256 != null, onClickLabel = "Copy SHA-256") {
            evidence.sha256?.let { clipboard.setText(AnnotatedString(it)) }
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Text(
            "SHA-256 ${EvidenceHasher.short(evidence.sha256)}",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = contentColor
        )
        Text(
            integrity?.label ?: "Checking…",
            style = MaterialTheme.typography.labelSmall,
            color = if (integrity == EvidenceIntegrity.MODIFIED || integrity == EvidenceIntegrity.MISSING) MaterialTheme.colorScheme.error else contentColor
        )
    }
}
