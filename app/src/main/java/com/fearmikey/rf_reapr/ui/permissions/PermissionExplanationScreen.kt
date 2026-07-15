package com.fearmikey.rf_reapr.ui.permissions

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat

@Preview
@Composable
fun PermissionExplanationPreview() {
    MaterialTheme {
        PermissionExplanationScreen(onPermissionsGranted = {})
    }
}

@Composable
fun PermissionExplanationScreen(
    onPermissionsGranted: () -> Unit
) {
    val context = LocalContext.current
    val permissionsToRequest = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // We navigate forward regardless, but in a real app we might show a warning
        // if essential permissions are denied.
        onPermissionsGranted()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF00050A))
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        
        Icon(
            imageVector = Icons.Default.Security,
            contentDescription = null,
            tint = Color(0xFF00E676),
            modifier = Modifier.size(64.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "ACCESS REQUIRED",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 2.sp
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "RF_REAPR needs specific system permissions to interface with radio hardware and map local networks.\n\nAll data collected remains on-device. No information is uploaded to external servers.",
            color = Color.Gray,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        
        Spacer(modifier = Modifier.height(40.dp))
        
        PermissionItem(
            icon = Icons.Default.Bluetooth,
            title = "Bluetooth Low Energy",
            description = "Required to scan for and audit BLE devices in your vicinity."
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        PermissionItem(
            icon = Icons.Default.LocationOn,
            title = "Precise Location",
            description = "Android requires location access to perform WiFi and Bluetooth scans."
        )
        
        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(40.dp))
        
        Button(
            onClick = {
                launcher.launch(permissionsToRequest)
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF00E676),
                contentColor = Color.Black
            ),
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = "CONTINUE TO SYSTEM PROMPT",
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun PermissionItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF4FC3F7),
            modifier = Modifier.size(32.dp).padding(top = 4.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column {
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = description,
                color = Color.LightGray.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 18.sp
            )
        }
    }
}
