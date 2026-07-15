package com.fearmikey.rf_reapr.ui.physical

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MagnetometerScreen(
    viewModel: MagnetometerViewModel,
    onBack: () -> Unit
) {
    val data by viewModel.magnetometerData.collectAsState()
    val peak by viewModel.peakStrength.collectAsState()
    val status by viewModel.fieldStatus.collectAsState()
    
    var showAccuracyTip by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        viewModel.startScanning()
        onDispose {
            viewModel.stopScanning()
        }
    }

    if (showAccuracyTip) {
        AlertDialog(
            onDismissRequest = { showAccuracyTip = false },
            icon = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Accuracy Tip") },
            text = {
                Text(
                    "For the most accurate results, avoid covering the back of your phone with your hand, as this can interfere with the internal magnetometer sensor.",
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                TextButton(onClick = { showAccuracyTip = false }) {
                    Text("Got it")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Magnetometer") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.resetPeak() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset Peak")
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
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Magnetic Flux Density",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary
            )

            Spacer(modifier = Modifier.height(24.dp))

            MagnetometerGauge(
                strength = data?.totalStrength ?: 0f,
                modifier = Modifier.size(250.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricItem("Total Strength", "${"%.2f".format(data?.totalStrength ?: 0f)} μT", isHighlight = true)
                        MetricItem("Peak Observed", "${"%.2f".format(peak)} μT")
                    }
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricItem("Axis X", "${"%.2f".format(data?.x ?: 0f)} μT")
                        MetricItem("Axis Y", "${"%.2f".format(data?.y ?: 0f)} μT")
                        MetricItem("Axis Z", "${"%.2f".format(data?.z ?: 0f)} μT")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            DetectionStatus(status = status)

            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                "Use this tool to detect magnetic fields from hidden electronics, speakers, or high-voltage wiring.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
fun MagnetometerGauge(strength: Float, modifier: Modifier = Modifier) {
    val maxStrength = 200f
    val progress = (strength / maxStrength).coerceIn(0f, 1f)
    val color = when {
        strength < 50 -> MaterialTheme.colorScheme.primary
        strength < 100 -> Color(0xFFFFA000)
        else -> MaterialTheme.colorScheme.error
    }

    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawArc(
                color = Color.Gray.copy(alpha = 0.2f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = color,
                startAngle = 135f,
                sweepAngle = 270f * progress,
                useCenter = false,
                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "%.1f".format(strength),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 48.sp
                ),
                color = color
            )
            Text(
                text = "μT",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MetricItem(label: String, value: String, isHighlight: Boolean = false) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value, 
            style = if (isHighlight) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
            fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Normal,
            color = if (isHighlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun DetectionStatus(status: FieldStatus) {
    val (text, color) = when (status) {
        FieldStatus.HIGH -> "High Magnetic Field Detected" to MaterialTheme.colorScheme.error
        FieldStatus.SIGNIFICANT -> "Significant Field Detected" to Color(0xFFFFA000)
        FieldStatus.NORMAL -> "Normal Ambient Field" to MaterialTheme.colorScheme.primary
        FieldStatus.WEAK -> "Weak Field" to MaterialTheme.colorScheme.secondary
    }

    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(color, CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = color)
        }
    }
}
