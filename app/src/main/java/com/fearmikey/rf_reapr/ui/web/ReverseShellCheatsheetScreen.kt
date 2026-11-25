package com.fearmikey.rf_reapr.ui.web

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fearmikey.rf_reapr.domain.service.ReverseShellCatalog
import com.fearmikey.rf_reapr.domain.service.ShellPlatform
import java.net.Inet4Address
import java.net.NetworkInterface

/** This device's own IPv4 address on Wi-Fi / VPN / tethering, for when the phone runs the listener. */
private fun localIpv4(): String? = runCatching {
    NetworkInterface.getNetworkInterfaces().asSequence()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.asSequence() }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
        ?.hostAddress
}.getOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReverseShellCheatsheetScreen(onBack: () -> Unit) {
    var hostInput by rememberSaveable { mutableStateOf("") }
    var portInput by rememberSaveable { mutableStateOf("4444") }
    var shell by rememberSaveable { mutableStateOf(ReverseShellCatalog.shells.first()) }
    var platformName by rememberSaveable { mutableStateOf<String?>(null) }
    var filter by rememberSaveable { mutableStateOf("") }
    val copy = rememberCopyAction()

    val hostError = remember(hostInput) { ReverseShellCatalog.hostError(hostInput) }
    val port = remember(portInput) { ReverseShellCatalog.parsePort(portInput) }
    val ready = hostError == null && port != null
    val platform = ShellPlatform.entries.firstOrNull { it.name == platformName }
    val templates = remember(platform, filter) { ReverseShellCatalog.filter(platform, filter) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reverse Shell Cheatsheet") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            item {
                OsintNotice("Reference for authorised penetration tests and CTFs. Only run these against systems you have written permission to test.")
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = hostInput,
                        onValueChange = { hostInput = it },
                        label = { Text("Listener address") },
                        placeholder = { Text("10.0.0.1") },
                        modifier = Modifier.weight(2f),
                        singleLine = true,
                        isError = hostInput.isNotBlank() && hostError != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                        trailingIcon = {
                            IconButton(onClick = { localIpv4()?.let { hostInput = it } }) {
                                Icon(Icons.Default.PhoneAndroid, contentDescription = "Use this device's IP address")
                            }
                        }
                    )
                    OutlinedTextField(
                        value = portInput,
                        onValueChange = { portInput = it.filter(Char::isDigit).take(5) },
                        label = { Text("Port") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        isError = port == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                val problem = when {
                    hostInput.isNotBlank() && hostError != null -> hostError
                    port == null -> "Port must be between 1 and 65535."
                    hostInput.isBlank() -> hostError
                    else -> ReverseShellCatalog.portNote(port)
                }
                problem?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ready) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(selected = platform == null, onClick = { platformName = null }, label = { Text("All") })
                    ShellPlatform.entries.forEach { option ->
                        FilterChip(
                            selected = platform == option,
                            onClick = { platformName = if (platform == option) null else option.name },
                            label = { Text(option.label) }
                        )
                    }
                    ReverseShellCatalog.shells.forEach { option ->
                        FilterChip(selected = shell == option, onClick = { shell = option }, label = { Text(option, fontFamily = FontFamily.Monospace) })
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    label = { Text("Filter (e.g. python, socat)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (!ready || port == null) {
                item { OsintNotice("Enter a valid listener address and port to generate the commands.") }
            } else {
                val host = hostInput.trim()
                item { Text("1. Start a listener on your machine", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
                items(ReverseShellCatalog.listeners, key = { "listener-${it.name}" }) { listener ->
                    val command = ReverseShellCatalog.render(listener.template, host, port)
                    CommandCard(listener.name, listener.note, command, onCopy = { copy("Command", command) }, onCopyEncoded = null)
                }

                item { Text("2. Run on the target", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
                if (templates.isEmpty()) item { OsintNotice("No payload matches the filter.") }
                items(templates, key = { it.name }) { template ->
                    val command = ReverseShellCatalog.render(template.template, host, port, shell)
                    CommandCard(
                        title = template.name,
                        subtitle = "${template.platform.label} · needs ${template.requires}",
                        command = command,
                        onCopy = { copy("Command", command) },
                        onCopyEncoded = { copy("URL-encoded command", ReverseShellCatalog.urlEncoded(command)) }
                    )
                }

                item { Text("3. Upgrade to a full terminal (Linux)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ReverseShellCatalog.ttyUpgrade.forEachIndexed { index, step ->
                                Text("${index + 1}. $step", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandCard(title: String, subtitle: String, command: String, onCopy: () -> Unit, onCopyEncoded: (() -> Unit)?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                onCopyEncoded?.let {
                    IconButton(onClick = it) {
                        Icon(Icons.Default.Link, contentDescription = "Copy URL-encoded")
                    }
                }
                IconButton(onClick = onCopy) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            SelectionContainer {
                Text(
                    text = command,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
    }
}
