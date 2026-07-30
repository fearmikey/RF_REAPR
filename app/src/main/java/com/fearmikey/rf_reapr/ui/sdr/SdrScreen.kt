package com.fearmikey.rf_reapr.ui.sdr

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fearmikey.rf_reapr.ui.sdr.components.WaterfallView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.fearmikey.rf_reapr.data.repository.RtlTcpRepositoryImpl
import com.fearmikey.rf_reapr.domain.repository.SdrRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SdrScreen(
    onNavigateBack: () -> Unit,
    viewModel: SdrViewModel = viewModel(factory = SdrViewModelFactory(RtlTcpRepositoryImpl()))
) {
    val config by viewModel.config.collectAsState()
    val fftData by viewModel.fftData.collectAsState()
    
    var host by remember { mutableStateOf("127.0.0.1") }
    var port by remember { mutableStateOf("1234") }
    var freqText by remember { mutableStateOf(config.frequency.toString()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SDR Controller") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        // Back button icon could be added here
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!config.isConnected) {
                TextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Host") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("Port") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.connect(host, port.toIntOrNull() ?: 1234) }) {
                    Text("Connect")
                }
            } else {
                Text("Connected to $host:$port", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = freqText,
                        onValueChange = { freqText = it },
                        label = { Text("Frequency (Hz)") },
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { viewModel.setFrequency(freqText.toLongOrNull() ?: config.frequency) }) {
                        Text("Set")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Gain: ${config.gain}")
                Slider(
                    value = config.gain.toFloat(),
                    onValueChange = { viewModel.setGain(it.toInt()) },
                    valueRange = 0f..50f,
                    steps = 50
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                WaterfallView(
                    fftData = fftData,
                    modifier = Modifier.weight(1f)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(onClick = { viewModel.disconnect() }) {
                    Text("Disconnect")
                }
            }
        }
    }
}

class SdrViewModelFactory(private val repository: SdrRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SdrViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SdrViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
