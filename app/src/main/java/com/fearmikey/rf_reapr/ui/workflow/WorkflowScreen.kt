package com.fearmikey.rf_reapr.ui.workflow

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fearmikey.rf_reapr.domain.model.WorkflowStep
import com.fearmikey.rf_reapr.domain.util.ScanTimeEstimator
import com.fearmikey.rf_reapr.ui.theme.NetworkGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowScreen(
    viewModel: WorkflowViewModel,
    onNavigateToReport: (Long?) -> Unit,
    onBack: () -> Unit,
) {
    val currentStepIndex by viewModel.currentStepIndex.collectAsStateWithLifecycle()
    val stepStatuses by viewModel.stepStatuses.collectAsStateWithLifecycle()
    val selectedStepIds by viewModel.selectedStepIds.collectAsStateWithLifecycle()
    val workflowState by viewModel.workflowState.collectAsStateWithLifecycle()
    val executionLogs by viewModel.executionLogs.collectAsStateWithLifecycle()
    val overallProgress by viewModel.overallProgress.collectAsStateWithLifecycle()
    val targetDomain by viewModel.targetDomain.collectAsStateWithLifecycle()
    val workflowStartTime by viewModel.workflowStartTime.collectAsStateWithLifecycle()
    val stepEstimates by viewModel.stepEstimates.collectAsStateWithLifecycle()
    val mode = viewModel.mode

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(mode.title) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (workflowState == WorkflowViewModel.WorkflowState.EXECUTING) {
                                viewModel.cancelWorkflow()
                            } else {
                                onBack()
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            when (workflowState) {
                WorkflowViewModel.WorkflowState.SELECTION -> {
                    SelectionContent(
                        mode = mode,
                        selectedStepIds = selectedStepIds,
                        targetDomain = targetDomain,
                        stepEstimates = stepEstimates,
                        onTargetChange = { viewModel.updateTargetDomain(it) },
                        onToggle = { viewModel.toggleStepSelection(it) }
                    ) { viewModel.startWorkflow() }
                }
                WorkflowViewModel.WorkflowState.EXECUTING -> {
                    AutomatedExecutionContent(
                        mode = mode,
                        currentStepIndex = currentStepIndex,
                        executionLogs = executionLogs,
                        overallProgress = overallProgress,
                        selectedStepIds = selectedStepIds,
                        stepEstimates = stepEstimates
                    ) { viewModel.cancelWorkflow() }
                }
                WorkflowViewModel.WorkflowState.SUMMARY -> {
                    WorkflowSummary(
                        steps = mode.steps,
                        statuses = stepStatuses,
                        onGenerateReport = { onNavigateToReport(workflowStartTime) },
                        onFinish = onBack
                    )
                }
            }
        }
    }
}

@Composable
fun AutomatedExecutionContent(
    mode: com.fearmikey.rf_reapr.domain.model.AppMode,
    currentStepIndex: Int,
    executionLogs: List<String>,
    overallProgress: Float,
    selectedStepIds: Set<String> = mode.steps.map { it.id }.toSet(),
    stepEstimates: Map<String, ScanTimeEstimator.EstimateRange> = emptyMap(),
    onCancel: () -> Unit
) {
    val listState = rememberLazyListState()
    
    // Auto-scroll logs to bottom
    LaunchedEffect(executionLogs.size) {
        if (executionLogs.isNotEmpty()) {
            listState.animateScrollToItem(executionLogs.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Master Progress
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Automated Recon in Progress",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = NetworkGreen
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                LinearProgressIndicator(
                    progress = { overallProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = NetworkGreen,
                    trackColor = NetworkGreen.copy(alpha = 0.1f)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                val currentStep = mode.steps.getOrNull(currentStepIndex)
                Text(
                    text = "Current: ${currentStep?.title ?: "Finalizing..."}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NetworkGreen
                )

                val remainingStepIds = mode.steps.drop(currentStepIndex)
                    .map { it.id }
                    .filter { selectedStepIds.contains(it) }
                if (remainingStepIds.isNotEmpty()) {
                    // Prefer the per-step estimates already computed by the view model
                    // (which factor in real subnet/device counts) when all are available,
                    // falling back to default assumptions otherwise.
                    val fromMap = remainingStepIds.mapNotNull { stepEstimates[it] }
                    val remainingLabel = if (fromMap.size == remainingStepIds.size) {
                        ScanTimeEstimator.EstimateRange(
                            fromMap.sumOf { it.lowSeconds },
                            fromMap.sumOf { it.highSeconds }
                        ).label
                    } else {
                        ScanTimeEstimator.totalEstimate(remainingStepIds).label
                    }
                    Text(
                        text = "Est. remaining: ~$remainingLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "EXECUTION LOG",
            style = MaterialTheme.typography.labelLarge,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Real-time Logs
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(executionLogs) { log ->
                    Text(
                        text = "> $log",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            lineHeight = 16.sp
                        ),
                        color = NetworkGreen
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Cancel Operation")
        }
    }
}

@Composable
fun SelectionContent(
    mode: com.fearmikey.rf_reapr.domain.model.AppMode,
    selectedStepIds: Set<String>,
    targetDomain: String,
    stepEstimates: Map<String, ScanTimeEstimator.EstimateRange> = emptyMap(),
    onTargetChange: (String) -> Unit,
    onToggle: (String) -> Unit,
    onStart: () -> Unit
) {
    var showWarning by remember { mutableStateOf(value = false) }
    val isStartEnabled = !mode.requiresTarget || targetDomain.isNotBlank()

    Column {
        Text(
            text = "Configure Workflow",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Select the tests you want to include in this automated run.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (mode.requiresTarget) {
            OutlinedTextField(
                value = targetDomain,
                onValueChange = onTargetChange,
                label = { Text("Target Domain") },
                placeholder = { Text(mode.targetHint) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    autoCorrect = false
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(mode.steps) { _, step ->
                SelectionItem(
                    step = step,
                    isSelected = selectedStepIds.contains(step.id),
                    estimate = stepEstimates[step.id],
                    onToggle = { onToggle(step.id) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val selectedEstimates = selectedStepIds.mapNotNull { stepEstimates[it] }
        if (selectedEstimates.isNotEmpty()) {
            val totalLow = selectedEstimates.sumOf { it.lowSeconds }
            val totalHigh = selectedEstimates.sumOf { it.highSeconds }
            Text(
                text = "Estimated total time: ~${ScanTimeEstimator.EstimateRange(totalLow, totalHigh).label}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = NetworkGreen
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = { showWarning = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = isStartEnabled,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NetworkGreen)
        ) {
            Text("Review Security Warning", color = Color.Black)
        }
    }

    if (showWarning) {
        AlertDialog(
            onDismissRequest = { showWarning = false },
            title = { Text("Security Warning & Disclaimer") },
            text = {
                Column {
                    Text(
                        text = mode.securityWarning,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "By proceeding, you acknowledge that you have authorization to scan this network and accept all responsibility for the automated actions of this toolkit.",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showWarning = false
                        onStart()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NetworkGreen)
                ) {
                    Text("Acknowledge & Start", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWarning = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SelectionItem(
    step: WorkflowStep,
    isSelected: Boolean,
    estimate: ScanTimeEstimator.EstimateRange? = null,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) NetworkGreen.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
                enabled = !step.isMandatory,
                colors = CheckboxDefaults.colors(checkedColor = NetworkGreen)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = step.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(text = step.description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                if (estimate != null && (estimate.lowSeconds > 0 || estimate.highSeconds > 0)) {
                    Text(
                        text = "~${estimate.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = NetworkGreen
                    )
                }
            }
        }
    }
}

@Composable
fun WorkflowSummary(
    steps: List<WorkflowStep>,
    statuses: Map<String, WorkflowViewModel.StepStatus>,
    onGenerateReport: () -> Unit,
    onFinish: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NetworkGreen.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.TaskAlt, null, tint = NetworkGreen, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Workflow Complete", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            
            val completed = statuses.values.count { it == WorkflowViewModel.StepStatus.COMPLETED }
            Text("$completed of ${steps.size} tests performed", style = MaterialTheme.typography.bodyMedium)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(steps) { step ->
                    SummaryStepItem(step, statuses[step.id] ?: WorkflowViewModel.StepStatus.PENDING)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = onGenerateReport,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("Generate Full Report", color = MaterialTheme.colorScheme.onSecondary)
            }

            Spacer(modifier = Modifier.height(12.dp))
            
            OutlinedButton(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Finish")
            }
        }
    }
}

@Composable
fun SummaryStepItem(step: WorkflowStep, status: WorkflowViewModel.StepStatus) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(step.title, style = MaterialTheme.typography.bodyMedium)
        when (status) {
            WorkflowViewModel.StepStatus.COMPLETED -> Icon(Icons.Default.CheckCircle, null, tint = NetworkGreen, modifier = Modifier.size(20.dp))
            WorkflowViewModel.StepStatus.FAILED -> Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
            WorkflowViewModel.StepStatus.SKIPPED -> Icon(Icons.Default.Block, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
            else -> Icon(Icons.Default.Pending, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
        }
    }
}
