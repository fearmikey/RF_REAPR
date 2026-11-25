package com.fearmikey.rf_reapr.ui.web

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

/** Shared pieces of the OSINT tool screens. */

enum class NoticeKind { INFO, ERROR, RECORDED }

/** A one-line banner: an explanation, an error, or "N findings recorded". */
@Composable
fun OsintNotice(text: String, modifier: Modifier = Modifier, kind: NoticeKind = NoticeKind.INFO) {
    val (container, content, icon) = when (kind) {
        NoticeKind.ERROR -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, Icons.Default.ErrorOutline)
        NoticeKind.RECORDED -> Triple(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, Icons.AutoMirrored.Filled.FactCheck)
        NoticeKind.INFO -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, Icons.Default.Info)
    }
    Surface(color = container, shape = MaterialTheme.shapes.small, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NoticeIcon(icon, content)
            Text(text, style = MaterialTheme.typography.bodySmall, color = content)
        }
    }
}

@Composable
private fun NoticeIcon(icon: ImageVector, tint: androidx.compose.ui.graphics.Color) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
}

/** "Recorded N finding(s)" banner; renders nothing for zero. */
@Composable
fun FindingsRecordedNotice(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    OsintNotice(
        "$count finding${if (count == 1) "" else "s"} recorded to the active engagement's Findings.",
        modifier, NoticeKind.RECORDED
    )
}

/** Returns a function that copies text to the clipboard and confirms with a toast. */
@Suppress("DEPRECATION")
@Composable
fun rememberCopyAction(): (label: String, text: String) -> Unit {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    return remember(clipboard, context) {
        { label, text ->
            clipboard.setText(AnnotatedString(text))
            Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
        }
    }
}
