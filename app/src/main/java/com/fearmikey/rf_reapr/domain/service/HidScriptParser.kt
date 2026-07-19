package com.fearmikey.rf_reapr.domain.service

import android.util.Log

interface HidScriptParser {
    suspend fun parseAndExecute(
        script: String,
        onLog: (String) -> Unit,
        onRetrieve: suspend (String) -> Unit = {}
    )
}

class DuckyScriptParser : HidScriptParser {
    override suspend fun parseAndExecute(
        script: String,
        onLog: (String) -> Unit,
        onRetrieve: suspend (String) -> Unit
    ) {
        val lines = script.lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("REM")) continue

            val parts = trimmed.split(" ", limit = 2)
            val command = parts[0].uppercase()
            val arg = if (parts.size > 1) parts[1] else ""

            when (command) {
                "STRING" -> {
                    onLog("Typing: $arg")
                    // Implement actual HID typing logic here
                }
                "DELAY" -> {
                    val delay = arg.toLongOrNull() ?: 0L
                    onLog("Delaying: ${delay}ms")
                    kotlinx.coroutines.delay(delay)
                }
                "ENTER" -> {
                    onLog("Pressing ENTER")
                    // Implement actual HID ENTER key logic here
                }
                "GUI", "WINDOWS" -> {
                    onLog("Pressing GUI/WINDOWS key")
                    // Implement actual HID GUI key logic here
                }
                "APP", "MENU" -> {
                    onLog("Pressing APP/MENU key")
                }
                "SHIFT", "ALT", "CONTROL", "CTRL" -> {
                    onLog("Pressing modifier: $command")
                }
                "RETRIEVE" -> {
                    onLog("Retrieving asset: $arg")
                    onRetrieve(arg)
                }
                else -> {
                    onLog("Unknown command: $command")
                }
            }
        }
    }
}
