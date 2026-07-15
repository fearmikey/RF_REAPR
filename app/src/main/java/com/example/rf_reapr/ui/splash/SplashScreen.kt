package com.example.rf_reapr.ui.splash

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Preview(showBackground = true)
@Composable
fun SplashScreenPreview() {
    SplashScreen(versionName = "1.2.6") { }
}

@Composable
fun SplashScreen(versionName: String, onTimeout: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(4000.milliseconds)
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF00050A)), // Deep midnight blue/black
        contentAlignment = Alignment.Center
    ) {
        // PCB Traces Background
        PCBBackground()

        // Scattered Tech Elements
        TechElementsOverlay(versionName)

        // Central Logo Area
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.alpha(0.7f)
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Blue Team",
                    tint = Color(0xFF4FC3F7), // Blue
                    modifier = Modifier.size(32.dp)
                )
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = "Red Team",
                    tint = Color(0xFFEF5350), // Red
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "RF_REAPR",
                color = Color.White,
                fontSize = 48.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 8.sp,
                modifier = Modifier.alpha(0.9f)
            )
            
            Text(
                text = "v $versionName",
                color = Color(0xFF00B0FF),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 4.dp).alpha(0.8f)
            )

            Text(
                text = "SYSTEM INITIALIZING...",
                color = Color(0xFF00E676), // Matrix green
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
fun PCBBackground() {
    Canvas(modifier = Modifier.fillMaxSize().alpha(0.2f)) {
        val strokeWidth = 2f
        val color = Color(0xFF00B0FF).copy(alpha = 0.5f)

        // Draw some "traces"
        drawLine(color, Offset(0f, 100f), Offset(200f, 100f), strokeWidth)
        drawLine(color, Offset(200f, 100f), Offset(300f, 300f), strokeWidth)
        drawCircle(color, 5f, Offset(300f, 300f))

        drawLine(color, Offset(size.width, 500f), Offset(size.width - 200f, 500f), strokeWidth)
        drawLine(color, Offset(size.width - 200f, 500f), Offset(size.width - 400f, 800f), strokeWidth)
        drawCircle(color, 5f, Offset(size.width - 400f, 800f))

        drawLine(color, Offset(100f, size.height), Offset(100f, size.height - 300f), strokeWidth)
        drawLine(color, Offset(100f, size.height - 300f), Offset(400f, size.height - 600f), strokeWidth)
        drawCircle(color, 5f, Offset(400f, size.height - 600f))
    }
}

@Composable
fun TechElementsOverlay(versionName: String) {
    Box(modifier = Modifier.fillMaxSize()) {
        TextElement("Pr = Pt+Gt+Gr+20log(λ/4πd)", 20, 150, 15f, 0.5f)
        TextElement("192.168.1.104", 250, 100, -10f, 0.4f)
        TextElement("01010010 01000110", 50, 600, 5f, 0.6f)
        TextElement("c = fλ", 280, 700, -20f, 0.5f)
        TextElement("S = PtGt / 4πd²", 180, 50, 0f, 0.4f)
        TextElement("10.0.0.1", 30, 450, 30f, 0.3f)
        TextElement("REAPR_v$versionName", 250, 800, -45f, 0.5f)
        TextElement("0xFF 0xAA 0x12", 320, 300, 10f, 0.4f)
    }
}

@Composable
fun TextElement(text: String, x: Int, y: Int, rotation: Float, alpha: Float) {
    Text(
        text = text,
        color = Color(0xFF4FC3F7),
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .offset(x.dp, y.dp)
            .rotate(rotation)
            .alpha(alpha)
    )
}
