package com.fearmikey.rf_reapr.ui.physical

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.fearmikey.rf_reapr.domain.model.DuckyCategory
import com.fearmikey.rf_reapr.domain.model.DuckyCommandCatalog
import com.fearmikey.rf_reapr.domain.model.DuckyCommandSpec
import com.fearmikey.rf_reapr.domain.model.DuckyParamType

/**
 * A bottom sheet that lets the user pick DuckyScript "functions" (STRING, DELAY, key
 * combos, shortcuts, etc.) from a categorized list and roll them into the script being
 * edited, one line at a time, without needing to memorize DuckyScript syntax.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DuckyScriptBuilderSheet(
    onDismiss: () -> Unit,
    onAddLine: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedCategory by remember { mutableStateOf<DuckyCategory?>(null) }
    var pendingCommand by remember { mutableStateOf<DuckyCommandSpec?>(null) }
    var paramValue by remember { mutableStateOf("") }

    // Custom key-combo builder state (e.g. CTRL ALT DEL, GUI r, CTRL SHIFT n)
    var comboGui by remember { mutableStateOf(false) }
    var comboCtrl by remember { mutableStateOf(false) }
    var comboAlt by remember { mutableStateOf(false) }
    var comboShift by remember { mutableStateOf(false) }
    var comboKey by remember { mutableStateOf("") }

    fun addCombo() {
        val mods = buildList {
            if (comboGui) add("GUI")
            if (comboCtrl) add("CTRL")
            if (comboAlt) add("ALT")
            if (comboShift) add("SHIFT")
        }
        val key = comboKey.trim()
        val line = (mods + listOfNotNull(key.ifBlank { null })).joinToString(" ")
        if (line.isNotBlank()) {
            onAddLine(line)
            comboKey = ""
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            Text("DuckyScript Builder", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Tap a function below to append it to your script.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            // ---- Custom key combo builder ----
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Custom Key Combo", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = comboGui, onClick = { comboGui = !comboGui }, label = { Text("GUI") })
                        FilterChip(selected = comboCtrl, onClick = { comboCtrl = !comboCtrl }, label = { Text("CTRL") })
                        FilterChip(selected = comboAlt, onClick = { comboAlt = !comboAlt }, label = { Text("ALT") })
                        FilterChip(selected = comboShift, onClick = { comboShift = !comboShift }, label = { Text("SHIFT") })
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = comboKey,
                            onValueChange = { comboKey = it },
                            label = { Text("Key (e.g. a, F4, DELETE)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledIconButton(onClick = { addCombo() }) {
                            Icon(Icons.Default.Add, contentDescription = "Add key combo")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ---- Category filter chips ----
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text("All") }
                    )
                }
                items(DuckyCategory.entries) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = if (selectedCategory == category) null else category },
                        label = { Text(category.displayName) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ---- Command list, grouped by category ----
            val grouped = remember(selectedCategory) {
                DuckyCommandCatalog.commands
                    .filter { selectedCategory == null || it.category == selectedCategory }
                    .groupBy { it.category }
            }

            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                grouped.forEach { (category, commands) ->
                    item(key = "header_${category.name}") {
                        Text(
                            category.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
                        )
                    }
                    item(key = "row_${category.name}") {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            commands.forEach { command ->
                                AssistChip(
                                    onClick = {
                                        if (command.paramType == DuckyParamType.NONE) {
                                            onAddLine(command.lineBuilder(""))
                                        } else {
                                            paramValue = command.defaultParam
                                            pendingCommand = command
                                        }
                                    },
                                    label = { Text(command.label) }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("Done")
            }
        }
    }

    pendingCommand?.let { command ->
        AlertDialog(
            onDismissRequest = { pendingCommand = null },
            title = { Text(command.label) },
            text = {
                Column {
                    if (command.helpText.isNotBlank()) {
                        Text(command.helpText, style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    OutlinedTextField(
                        value = paramValue,
                        onValueChange = { paramValue = it },
                        label = { Text(command.paramLabel) },
                        singleLine = command.paramType == DuckyParamType.NUMBER,
                        keyboardOptions = if (command.paramType == DuckyParamType.NUMBER) {
                            KeyboardOptions(keyboardType = KeyboardType.Number)
                        } else {
                            KeyboardOptions.Default
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (paramValue.isNotBlank()) {
                            onAddLine(command.lineBuilder(paramValue))
                        }
                        pendingCommand = null
                    }
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { pendingCommand = null }) { Text("Cancel") }
            }
        )
    }
}
