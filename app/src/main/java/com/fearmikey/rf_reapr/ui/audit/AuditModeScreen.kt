package com.fearmikey.rf_reapr.ui.audit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fearmikey.rf_reapr.domain.model.AuditCheck
import com.fearmikey.rf_reapr.domain.model.AuditPreset
import com.fearmikey.rf_reapr.domain.model.AuditRunState
import com.fearmikey.rf_reapr.domain.model.AuditStepState
import com.fearmikey.rf_reapr.domain.model.AuditStepStatus
import com.fearmikey.rf_reapr.domain.model.PortDepth
import com.fearmikey.rf_reapr.domain.model.RiskLevel
import com.fearmikey.rf_reapr.domain.service.AuditReportBuilder
import com.fearmikey.rf_reapr.domain.service.AuditScoring
import com.fearmikey.rf_reapr.domain.service.EngagementContext
import com.fearmikey.rf_reapr.ui.components.exportFileName
import com.fearmikey.rf_reapr.ui.components.writeTextToUri
import com.fearmikey.rf_reapr.ui.scanner.SeverityBadge
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditModeScreen(
    viewModel: AuditModeViewModel,
    onOpenFindings: () -> Unit,
    onOpenEngagement: () -> Unit,
    onOpenHistory: () -> Unit,
    onBack: () -> Unit
) {
    val run by viewModel.run.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val engagement by viewModel.engagement.collectAsStateWithLifecycle()
    val comparison by viewModel.comparison.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null) {
            scope.launch {
                val error = writeTextToUri(context, uri, viewModel.buildReport())
                viewModel.showMessage(error ?: "Audit report exported")
            }
        }
    }
    val exportReport = {
        exportLauncher.launch(exportFileName(run.projectName ?: "rf-reapr", "audit", "md"))
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Audit Mode") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (run.hasRun && !run.isRunning) {
                        IconButton(onClick = exportReport) { Icon(Icons.Default.Description, contentDescription = "Export report") }
                    }
                    IconButton(onClick = onOpenHistory) { Icon(Icons.Default.History, contentDescription = "Past audits") }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when {
                        run.isRunning -> OutlinedButton(onClick = viewModel::cancel, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Cancel audit")
                        }
                        run.hasRun -> {
                            OutlinedButton(onClick = viewModel::newAudit, modifier = Modifier.weight(1f)) { Text("New audit") }
                            Button(onClick = onOpenFindings, modifier = Modifier.weight(1f)) { Text("Triage findings") }
                        }
                        else -> Button(onClick = viewModel::start, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Start audit")
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (run.hasRun) {
            AuditRunContent(run, Modifier.padding(padding), comparison, onExport = exportReport)
        } else {
            AuditSetupContent(viewModel, engagement, onOpenEngagement, Modifier.padding(padding))
        }
    }
}

// ---- Setup ------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AuditSetupContent(
    viewModel: AuditModeViewModel,
    engagement: EngagementContext?,
    onOpenEngagement: () -> Unit,
    modifier: Modifier
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val plan by viewModel.plan.collectAsStateWithLifecycle()
    val parsed by viewModel.parsedTargets.collectAsStateWithLifecycle()
    val isPassive by viewModel.isPassiveMode.collectAsStateWithLifecycle()
    val needsHosts = plan.any { it.check.needsHosts }
    val planByCheck = plan.associateBy { it.check }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "intro") {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Workspaces, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(engagement?.name ?: "No active project", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                when {
                                    engagement == null -> "Findings will be recorded without a project."
                                    engagement.scope.hasScope -> "Findings are filed under this project · ${engagement.scope.ruleCount} scope entries"
                                    else -> "Findings are filed under this project · no scope defined"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = onOpenEngagement) { Text(if (engagement == null) "Choose" else "Change") }
                    }
                    Text(
                        "Runs the selected tools back to back against this network and merges everything into one " +
                            "graded report. Only audit networks you own or are authorized to test.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (isPassive) {
            item(key = "passive") {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Passive Mode is on, so checks that contact other devices will be skipped. " +
                                "Switch to Active Mode from the main menu to run them.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        item(key = "presets") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Presets", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AuditPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = draft.selected == preset.checks,
                            onClick = { viewModel.applyPreset(preset) },
                            label = { Text(preset.label) }
                        )
                    }
                }
                AuditPreset.entries.firstOrNull { it.checks == draft.selected }?.let {
                    Text(it.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item(key = "checks_header") { Text("Checks", style = MaterialTheme.typography.titleMedium) }

        items(AuditCheck.entries, key = { "check_${it.name}" }) { check ->
            val planned = planByCheck[check]
            CheckRow(
                check = check,
                checked = check in draft.selected || planned?.autoAdded == true,
                autoAdded = planned?.autoAdded == true,
                skipReason = planned?.skipReason,
                blockedByPassive = isPassive && check.active,
                onToggle = { viewModel.toggle(check) }
            )
        }

        if (needsHosts) {
            item(key = "targets") {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Targets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = !draft.useTargetList,
                                onClick = { viewModel.setUseTargetList(false) },
                                shape = SegmentedButtonDefaults.itemShape(0, 2)
                            ) { Text("This network") }
                            SegmentedButton(
                                selected = draft.useTargetList,
                                onClick = { viewModel.setUseTargetList(true) },
                                shape = SegmentedButtonDefaults.itemShape(1, 2)
                            ) { Text("Target list") }
                        }
                        if (draft.useTargetList) {
                            OutlinedTextField(
                                value = draft.targetText,
                                onValueChange = viewModel::setTargetText,
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                maxLines = 6,
                                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                                placeholder = { Text("192.168.1.0/24\n10.0.0.5, nas.lan\n192.168.1.20-40") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                                isError = parsed.invalid.isNotEmpty(),
                                supportingText = {
                                    Text(
                                        when {
                                            parsed.invalid.isNotEmpty() -> "Can't parse: ${parsed.invalid.take(4).joinToString(" · ")} (CIDRs must be /24 or smaller)"
                                            parsed.truncated -> "${parsed.hosts.size} hosts (list truncated)"
                                            else -> "${parsed.hosts.size} host${if (parsed.hosts.size == 1) "" else "s"}"
                                        }
                                    )
                                }
                            )
                        } else {
                            Text(
                                "Sweeps the subnet you're connected to and audits every live device (up to 64).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (AuditCheck.PORTS in planByCheck) {
                            Text("Port depth", style = MaterialTheme.typography.labelLarge)
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                PortDepth.entries.forEachIndexed { index, depth ->
                                    SegmentedButton(
                                        selected = draft.portDepth == depth,
                                        onClick = { viewModel.setPortDepth(depth) },
                                        shape = SegmentedButtonDefaults.itemShape(index, PortDepth.entries.size)
                                    ) { Text(depth.label) }
                                }
                            }
                            Text(draft.portDepth.detail + " per host", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        if (engagement?.scope?.hasScope == true) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Stay in scope", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "Skip hosts the project scope marks out-of-scope or excluded",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(checked = draft.respectScope, onCheckedChange = viewModel::setRespectScope)
                            }
                        }
                    }
                }
            }
        }

        item(key = "plan") {
            val runnable = plan.filter { it.skipReason == null }
            Text(
                if (runnable.isEmpty()) "Nothing selected can run."
                else "Will run ${runnable.size} check${if (runnable.size == 1) "" else "s"}: " + runnable.joinToString(" → ") { it.check.label },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CheckRow(
    check: AuditCheck,
    checked: Boolean,
    autoAdded: Boolean,
    skipReason: String?,
    blockedByPassive: Boolean,
    onToggle: () -> Unit
) {
    Card(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        check.label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (blockedByPassive) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified
                    )
                    ModeTag(active = check.active)
                }
                Text(check.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val note = when {
                    checked && skipReason != null -> "Will be skipped: $skipReason"
                    autoAdded -> "Added automatically: another selected check needs it"
                    else -> null
                }
                note?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (skipReason != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeTag(active: Boolean) {
    val color = if (active) Color(0xFFFF9800) else Color(0xFF4CAF50)
    Surface(color = color.copy(alpha = 0.15f), shape = MaterialTheme.shapes.extraSmall) {
        Text(
            if (active) "Active" else "Passive",
            color = color,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
        )
    }
}

// ---- Run / results ----------------------------------------------------------------------

@Composable
private fun AuditRunContent(
    run: AuditRunState,
    modifier: Modifier,
    comparison: com.fearmikey.rf_reapr.domain.service.AuditRunDiff.Result?,
    onExport: () -> Unit
) {
    var expanded by remember(run.startedAt) { mutableStateOf<AuditCheck?>(null) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "header") { RunHeader(run, onExport) }
        comparison?.let { diff -> item(key = "comparison") { AuditComparisonCard(diff) } }

        item(key = "steps_header") { Text("Checks", style = MaterialTheme.typography.titleMedium) }
        items(run.steps, key = { "step_${it.check.name}" }) { step ->
            StepCard(
                step = step,
                expanded = expanded == step.check,
                onToggle = { expanded = if (expanded == step.check) null else step.check }
            )
        }

        if (run.hosts.isNotEmpty()) {
            item(key = "hosts_header") {
                Text("Hosts (${run.hosts.size})", style = MaterialTheme.typography.titleMedium)
            }
            items(run.hosts, key = { "host_${it.address}" }) { host ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(host.address, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            host.snmpCommunity?.let { Text("SNMP \"$it\"", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
                        }
                        listOfNotNull(host.hostname, host.vendor).joinToString(" · ").takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (host.openPorts.isNotEmpty()) {
                            Text(
                                host.openPorts.sortedBy { it.port }.joinToString(", ") { "${it.port}/${it.serviceName}" },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        if (run.outOfScope.isNotEmpty()) {
            item(key = "out_of_scope") {
                Text(
                    "Skipped as out of scope: ${run.outOfScope.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RunHeader(run: AuditRunState, onExport: () -> Unit) {
    val score = remember(run.findings) { AuditScoring.score(run.findings) }
    val runnable = run.steps.filter { it.status != AuditStepStatus.SKIPPED || it.startedAt != null }
    val finished = runnable.count { it.status in setOf(AuditStepStatus.DONE, AuditStepStatus.FAILED, AuditStepStatus.CANCELLED) }
    val running = runnable.firstOrNull { it.status == AuditStepStatus.RUNNING }
    val overall = if (runnable.isEmpty()) 1f else (finished + (running?.progress ?: 0f)) / runnable.size

    // Ticks once a second while running so the elapsed time stays live.
    val now by produceState(System.currentTimeMillis(), run.isRunning) {
        while (run.isRunning) {
            value = System.currentTimeMillis()
            delay(1000)
        }
        value = System.currentTimeMillis()
    }
    val elapsed = (run.finishedAt ?: now) - (run.startedAt ?: now)
    val cancelled = run.steps.any { it.status == AuditStepStatus.CANCELLED }

    Card(Modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GradeBadge(grade = score.grade, dimmed = run.isRunning)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            run.isRunning -> running?.check?.label?.let { "Running: $it" } ?: "Running…"
                            cancelled -> "Audit cancelled"
                            else -> "Audit complete"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        listOfNotNull(run.networkLabel, run.projectName?.let { "Project: $it" }).joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${AuditReportBuilder.formatDuration(elapsed)} · score ${score.score}/100" + if (run.isRunning) " so far" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (run.isRunning) {
                LinearProgressIndicator(progress = { overall.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                RiskLevel.entries.reversed().forEach { level ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SeverityBadge(level)
                        Text(" ${score.counts[level] ?: 0}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            if (!run.isRunning) {
                TextButton(onClick = onExport, contentPadding = PaddingValues(0.dp)) {
                    Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Export Markdown report")
                }
            }
        }
    }
}

@Composable
private fun GradeBadge(grade: String, dimmed: Boolean) {
    val color = when (grade) {
        "A" -> Color(0xFF4CAF50)
        "B" -> Color(0xFF8BC34A)
        "C" -> Color(0xFFFFC107)
        "D" -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }.let { if (dimmed) it.copy(alpha = 0.5f) else it }
    Surface(color = color.copy(alpha = 0.15f), shape = MaterialTheme.shapes.medium, modifier = Modifier.size(64.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text(grade, color = color, fontSize = 34.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun StepCard(step: AuditStepState, expanded: Boolean, onToggle: () -> Unit) {
    val hasFindings = step.findings.isNotEmpty()
    Card(onClick = onToggle, enabled = hasFindings, modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepStatusIcon(step.status)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        step.check.label + if (step.autoAdded) " (dependency)" else "",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    step.summary?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (hasFindings) {
                    val worst = step.findings.maxOf { it.severity }
                    Column(horizontalAlignment = Alignment.End) {
                        SeverityBadge(worst)
                        Text("${step.findings.size} finding${if (step.findings.size == 1) "" else "s"}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (step.status == AuditStepStatus.RUNNING) {
                val p = step.progress
                if (p != null) {
                    LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            if (expanded) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                step.findings.sortedByDescending { it.severity.ordinal }.forEach { f ->
                    Row(Modifier.padding(vertical = 2.dp)) {
                        SeverityBadge(f.severity)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(f.title, style = MaterialTheme.typography.bodyMedium)
                            Text(f.target, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepStatusIcon(status: AuditStepStatus) {
    val (icon: ImageVector?, tint: Color) = when (status) {
        AuditStepStatus.PENDING -> Icons.Default.Schedule to MaterialTheme.colorScheme.onSurfaceVariant
        AuditStepStatus.RUNNING -> null to MaterialTheme.colorScheme.primary
        AuditStepStatus.DONE -> Icons.Default.CheckCircle to Color(0xFF4CAF50)
        AuditStepStatus.SKIPPED -> Icons.Default.Block to MaterialTheme.colorScheme.onSurfaceVariant
        AuditStepStatus.FAILED -> Icons.Default.Error to MaterialTheme.colorScheme.error
        AuditStepStatus.CANCELLED -> Icons.Default.Cancel to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        if (icon == null) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = tint)
        } else {
            Icon(icon, contentDescription = status.name, tint = tint)
        }
    }
}
