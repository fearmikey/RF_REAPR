package com.fearmikey.rf_reapr.ui.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fearmikey.rf_reapr.data.backup.BackupManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(viewModel: BackupViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val working = state is BackupUiState.Working

    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) viewModel.export(uri, password)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        restoreUri = uri
    }

    val passwordOk = password.length >= BackupManager.MIN_PASSWORD_LENGTH
    val confirmOk = passwordOk && password == confirm

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup & restore") },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !working) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("What's included", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Projects, scope, findings, evidence photos and notes, audit checklists, scan history, tool logs, PCAP captures, HID assets and Wake-on-LAN targets.\n\n" +
                            "Not included: app settings and API keys (re-enter them after restoring).\n\n" +
                            "The file is encrypted with AES-256-GCM using a key derived from your password (PBKDF2, 310,000 rounds). " +
                            "Nothing is uploaded anywhere: you choose where the file goes.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Create backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    PasswordField("Backup password", password, showPassword, { password = it }, { showPassword = !showPassword }, !working,
                        supporting = if (password.isNotEmpty() && !passwordOk) "At least ${BackupManager.MIN_PASSWORD_LENGTH} characters" else null)
                    PasswordField("Confirm password", confirm, showPassword, { confirm = it }, { showPassword = !showPassword }, !working,
                        supporting = if (confirm.isNotEmpty() && confirm != password) "Passwords don't match" else null)
                    Button(
                        onClick = {
                            val date = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                            exportLauncher.launch("rf-reapr-$date.${BackupManager.FILE_EXTENSION}")
                        },
                        enabled = confirmOk && !working,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save encrypted backup")
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Restore", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Replaces the current database with the backup's (files with the same name are overwritten). The app restarts afterwards.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(onClick = { restoreLauncher.launch(arrayOf("*/*")) }, enabled = !working, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Restore, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Choose backup file…")
                    }
                }
            }

            when (val s = state) {
                is BackupUiState.Working -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text(s.message, style = MaterialTheme.typography.bodyMedium)
                }
                is BackupUiState.Done -> StatusCard(s.message, isError = false, onDismiss = viewModel::dismiss)
                is BackupUiState.Failed -> StatusCard(s.message, isError = true, onDismiss = viewModel::dismiss)
                else -> Unit
            }
        }
    }

    restoreUri?.let { uri ->
        RestoreDialog(
            onDismiss = { restoreUri = null },
            onConfirm = { pw ->
                restoreUri = null
                viewModel.restore(uri, pw)
            }
        )
    }

    if (state is BackupUiState.RestartRequired) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Restore complete") },
            text = { Text("RF-REAPR will now restart to load the restored data.") },
            confirmButton = { TextButton(onClick = { restartApp(context) }) { Text("Restart") } }
        )
    }
}

@Composable
private fun PasswordField(
    label: String,
    value: String,
    visible: Boolean,
    onChange: (String) -> Unit,
    onToggle: () -> Unit,
    enabled: Boolean,
    supporting: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = supporting != null,
        supportingText = supporting?.let { { Text(it) } },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        trailingIcon = {
            IconButton(onClick = onToggle) {
                Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = if (visible) "Hide password" else "Show password")
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun StatusCard(message: String, isError: Boolean, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (isError) Icons.Default.ErrorOutline else Icons.Default.CheckCircle, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Dismiss") }
        }
    }
}

@Composable
private fun RestoreDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pw by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, contentDescription = null) },
        title = { Text("Replace current data?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Everything currently in the app's database will be replaced by the backup. Make a backup of the current data first if you might need it.")
                PasswordField("Backup password", pw, visible, { pw = it }, { visible = !visible }, enabled = true)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(pw) }, enabled = pw.isNotEmpty()) { Text("Restore") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Relaunch the main activity in a fresh process (the database was swapped underneath us). */
private fun restartApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    if (intent != null) context.startActivity(intent)
    Process.killProcess(Process.myPid())
}
