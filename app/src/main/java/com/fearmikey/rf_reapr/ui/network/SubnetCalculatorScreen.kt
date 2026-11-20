package com.fearmikey.rf_reapr.ui.network

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fearmikey.rf_reapr.domain.service.SubnetCalculator
import com.fearmikey.rf_reapr.domain.service.SubnetInfo
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubnetCalculatorScreen(
    viewModel: SubnetCalculatorViewModel,
    onBack: () -> Unit
) {
    var ipInput by rememberSaveable { mutableStateOf("192.168.1.100") }
    var cidrInput by rememberSaveable { mutableStateOf("24") }
    var splitPrefix by rememberSaveable { mutableStateOf<Int?>(null) }
    var containsInput by rememberSaveable { mutableStateOf("") }
    val message by viewModel.message.collectAsStateWithLifecycle()

    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val copy: (String) -> Unit = { value ->
        clipboard.setText(AnnotatedString(value))
        Toast.makeText(context, "Copied $value", Toast.LENGTH_SHORT).show()
    }

    val parsed = remember(ipInput, cidrInput) { SubnetCalculator.parseInput(ipInput, cidrInput) }
    val info = remember(parsed) { parsed?.let { SubnetCalculator.calculate(it.first, it.second) } }
    val prefixInvalid = SubnetCalculator.parsePrefix(cidrInput) == null && !ipInput.contains('/')

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Subnet Calculator") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.loadCurrentNetwork { ip, prefix ->
                            ipInput = ip
                            cidrInput = prefix.toString()
                            splitPrefix = null
                        }
                    }) {
                        Icon(Icons.Default.MyLocation, contentDescription = "Use my current network")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "inputs") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = ipInput,
                            onValueChange = { ipInput = it.trim(); splitPrefix = null },
                            label = { Text("IP Address") },
                            placeholder = { Text("e.g. 192.168.1.100") },
                            modifier = Modifier.weight(2f),
                            singleLine = true,
                            isError = ipInput.isNotBlank() && SubnetCalculator.parseIpv4(ipInput.substringBefore('/')) == null,
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                            // Decimal (not Number) so the '.' separator is available on numeric IMEs.
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        OutlinedTextField(
                            value = cidrInput,
                            onValueChange = { cidrInput = it.trim(); splitPrefix = null },
                            label = { Text("Prefix / mask") },
                            placeholder = { Text("24") },
                            modifier = Modifier.weight(1.3f),
                            singleLine = true,
                            isError = prefixInvalid,
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    }
                    val prefix = parsed?.second ?: SubnetCalculator.parsePrefix(cidrInput) ?: 24
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("/$prefix", style = MaterialTheme.typography.labelLarge, fontFamily = FontFamily.Monospace, modifier = Modifier.width(40.dp))
                        Slider(
                            value = prefix.toFloat(),
                            onValueChange = {
                                cidrInput = it.roundToInt().toString()
                                if (ipInput.contains('/')) ipInput = ipInput.substringBefore('/')
                                splitPrefix = null
                            },
                            valueRange = 0f..32f,
                            steps = 31,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        "Prefix accepts 24, /24 or a netmask like 255.255.255.0. You can also type 10.0.0.5/8 in the address field.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            }

            if (info == null) {
                item(key = "invalid") {
                    if (ipInput.isNotBlank()) Text("Invalid IP or prefix", color = MaterialTheme.colorScheme.error)
                }
            } else {
                item(key = "results") { SubnetResultCard(info, copy) }
                item(key = "binary") { BinaryCard(info) }
                item(key = "contains") {
                    ContainsCard(info, containsInput, onChange = { containsInput = it })
                }
                if (info.prefix < 32) {
                    item(key = "split_header") {
                        Column {
                            Text("Split into subnets", style = MaterialTheme.typography.titleMedium)
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ((info.prefix + 1)..minOf(info.prefix + 8, 32)).forEach { p ->
                                    FilterChip(
                                        selected = splitPrefix == p,
                                        onClick = { splitPrefix = if (splitPrefix == p) null else p },
                                        label = { Text("/$p × ${SubnetCalculator.subnetCount(info, p)}") }
                                    )
                                }
                            }
                        }
                    }
                    splitPrefix?.let { p ->
                        val subnets = SubnetCalculator.split(info, p)
                        items(subnets, key = { "split_${it.network}" }) { sub ->
                            OutlinedCard(onClick = { copy(sub.cidr) }, modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(sub.cidr, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    Text(
                                        "${SubnetCalculator.format(sub.firstHost)} – ${SubnetCalculator.format(sub.lastHost)}",
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                        val total = SubnetCalculator.subnetCount(info, p)
                        if (total > subnets.size) {
                            item(key = "split_more") {
                                Text("Showing first ${subnets.size} of $total subnets", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubnetResultCard(info: SubnetInfo, onCopy: (String) -> Unit) {
    val f = SubnetCalculator::format
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            ResultRow("CIDR", info.cidr, onCopy)
            ResultRow("Netmask", f(info.netmask), onCopy)
            ResultRow("Wildcard", f(info.wildcard), onCopy)
            ResultRow("Network", f(info.network), onCopy)
            ResultRow("Broadcast", if (info.prefix >= 31) "n/a" else f(info.broadcast), onCopy)
            ResultRow("First host", f(info.firstHost), onCopy)
            ResultRow("Last host", f(info.lastHost), onCopy)
            ResultRow("Usable hosts", "%,d".format(info.usableHosts), onCopy)
            ResultRow("Total addresses", "%,d".format(info.totalAddresses), onCopy)
            ResultRow("Address type", info.addressType.label, onCopy, monospace = false)
            ResultRow("Class", info.ipClass.toString(), onCopy, monospace = false)
            ResultRow("Hex", SubnetCalculator.toHex(info.address), onCopy)
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String, onCopy: (String) -> Unit, monospace: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Copy $label") { onCopy(value) }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(120.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = if (monospace) FontFamily.Monospace else null,
            modifier = Modifier.weight(1f)
        )
        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun BinaryCard(info: SubnetInfo) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp).horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Binary", style = MaterialTheme.typography.titleSmall)
            listOf(
                "Address" to info.address,
                "Netmask" to info.netmask,
                "Network" to info.network
            ).forEach { (label, value) ->
                Row {
                    Text(label, fontSize = 12.sp, modifier = Modifier.width(72.dp))
                    Text(SubnetCalculator.toBinary(value), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ContainsCard(info: SubnetInfo, input: String, onChange: (String) -> Unit) {
    val ip = SubnetCalculator.parseIpv4(input)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Is an address in this subnet?", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = input,
                onValueChange = { onChange(it.trim()) },
                placeholder = { Text("e.g. 192.168.1.42") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                trailingIcon = {
                    when {
                        ip == null -> Unit
                        info.contains(ip) -> Icon(Icons.Default.CheckCircle, contentDescription = "Inside", tint = MaterialTheme.colorScheme.primary)
                        else -> Icon(Icons.Default.Cancel, contentDescription = "Outside", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
            if (ip != null) {
                Text(
                    if (info.contains(ip)) "$input is inside ${info.cidr}" else "$input is outside ${info.cidr}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
