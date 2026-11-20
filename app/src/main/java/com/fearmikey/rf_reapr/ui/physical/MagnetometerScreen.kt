package com.fearmikey.rf_reapr.ui.physical

import android.hardware.SensorManager
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fearmikey.rf_reapr.ui.components.MiniLineChart
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MagnetometerScreen(
    viewModel: MagnetometerViewModel,
    onBack: () -> Unit
) {
    val data by viewModel.magnetometerData.collectAsStateWithLifecycle()
    val peak by viewModel.peakStrength.collectAsStateWithLifecycle()
    val status by viewModel.fieldStatus.collectAsStateWithLifecycle()
    val baseline by viewModel.baseline.collectAsStateWithLifecycle()
    val deviation by viewModel.deviation.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val calibrating by viewModel.isCalibrating.collectAsStateWithLifecycle()
    val soundEnabled by viewModel.soundEnabled.collectAsStateWithLifecycle()
    val accuracy by viewModel.accuracy.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        viewModel.startScanning()
        onDispose { viewModel.stopScanning() }
    }

    // "Geiger counter" clicks: faster as the deviation grows.
    if (soundEnabled) {
        val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 60) }.getOrNull() }
        DisposableEffect(tone) { onDispose { tone?.release() } }
        LaunchedEffect(tone) {
            while (true) {
                val dev = viewModel.deviation.value
                if (dev > 3f) {
                    tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 20)
                    delay((800 - dev * 8).toLong().coerceIn(40, 800))
                } else delay(200)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Magnetometer") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { viewModel.setSoundEnabled(!soundEnabled) }) {
                        Icon(
                            if (soundEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = if (soundEnabled) "Mute detector sound" else "Enable detector sound"
                        )
                    }
                    IconButton(onClick = viewModel::resetPeak) { Icon(Icons.Default.Refresh, contentDescription = "Reset peak") }
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!viewModel.isAvailable) {
                Text("This phone has no magnetic field sensor.", color = MaterialTheme.colorScheme.error)
                return@Column
            }

            if (accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW) {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.medium) {
                    Text(
                        "Sensor needs calibration: wave the phone in a figure-8 a few times, away from metal.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Text(
                if (baseline != null) "Deviation from ambient" else "Field strength",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary
            )

            MagnetometerGauge(
                strength = if (baseline != null) deviation else data?.totalStrength ?: 0f,
                maxStrength = if (baseline != null) 100f else 200f,
                modifier = Modifier.size(230.dp)
            )

            DetectionStatus(status = status)

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Deviation over time", style = MaterialTheme.typography.labelLarge)
                    MiniLineChart(
                        values = history,
                        height = 100.dp,
                        contentDescription = "Magnetic deviation history",
                        maxLabel = { String.format(Locale.US, "%.0f µT", it) }
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        MetricItem("Total", "${"%.1f".format(data?.totalStrength ?: 0f)} μT", isHighlight = true)
                        MetricItem("Peak", "${"%.1f".format(peak)} μT")
                        MetricItem("Ambient", baseline?.let { "%.1f μT".format(it.totalStrength) } ?: "–")
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        MetricItem("X", "${"%.1f".format(data?.x ?: 0f)}")
                        MetricItem("Y", "${"%.1f".format(data?.y ?: 0f)}")
                        MetricItem("Z", "${"%.1f".format(data?.z ?: 0f)}")
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::calibrate, enabled = !calibrating, modifier = Modifier.weight(1f)) {
                    Text(if (calibrating) "Hold still…" else if (baseline == null) "Calibrate ambient" else "Recalibrate")
                }
                if (baseline != null) {
                    OutlinedButton(onClick = viewModel::clearBaseline, modifier = Modifier.weight(1f)) { Text("Clear baseline") }
                }
            }
            if (calibrating) LinearProgressIndicator(Modifier.fillMaxWidth())

            Text(
                "How to sweep: calibrate while holding the phone away from objects, then move it slowly (1–3 cm away) over walls, furniture or vents. " +
                    "Hidden cameras, recorders, trackers, speakers and live wiring show up as local spikes. Keep your hand off the top of the phone, where the sensor usually sits.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MagnetometerGauge(strength: Float, modifier: Modifier = Modifier, maxStrength: Float = 200f) {
    val progress = (strength / maxStrength).coerceIn(0f, 1f)
    val color = when {
        progress < 0.25f -> MaterialTheme.colorScheme.primary
        progress < 0.5f -> Color(0xFFFFA000)
        else -> MaterialTheme.colorScheme.error
    }
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawArc(Color.Gray.copy(alpha = 0.2f), 135f, 270f, false, style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round))
            drawArc(color, 135f, 270f * progress, false, style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "%.1f".format(strength),
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold, fontSize = 48.sp),
                color = color
            )
            Text("μT", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        FieldStatus.HIGH -> "Strong source very close (magnet, speaker, motor)" to MaterialTheme.colorScheme.error
        FieldStatus.SIGNIFICANT -> "Electronics or wiring nearby" to Color(0xFFFFA000)
        FieldStatus.NORMAL -> "Slight disturbance" to MaterialTheme.colorScheme.primary
        FieldStatus.WEAK -> "Ambient field only" to MaterialTheme.colorScheme.secondary
    }
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(12.dp).background(color, CircleShape))
            Spacer(modifier = Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = color)
        }
    }
}
