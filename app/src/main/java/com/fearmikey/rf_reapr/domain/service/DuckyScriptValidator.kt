package com.fearmikey.rf_reapr.domain.service

import com.fearmikey.rf_reapr.domain.model.DuckyCommandCatalog

enum class LintLevel { ERROR, WARNING, INFO }

data class LintIssue(val line: Int, val level: LintLevel, val message: String)

data class DuckyLintResult(
    val issues: List<LintIssue>,
    val commandCount: Int,
    val estimatedDurationMs: Long
) {
    val errors: Int get() = issues.count { it.level == LintLevel.ERROR }
    val warnings: Int get() = issues.count { it.level == LintLevel.WARNING }
    val isValid: Boolean get() = errors == 0
}

/**
 * Static checker for DuckyScript (Rubber Ducky) payloads. Catches typos and structural
 * mistakes before a script is flashed to a device, and estimates run time. Pure Kotlin so
 * it's unit-tested and runs off the UI thread. It does not execute anything.
 */
object DuckyScriptValidator {

    // Standalone commands that take an argument.
    private val stringCommands = setOf("STRING", "STRINGLN", "REM", "REM_BLOCK", "END_REM")
    private val numberCommands = setOf("DELAY", "DEFAULTDELAY", "DEFAULT_DELAY", "REPEAT", "HOLD", "VID", "PID")

    // Single keys and modifiers that can appear alone or in a combo like "CTRL ALT DELETE".
    private val keyTokens = setOf(
        "ENTER", "TAB", "SPACE", "ESCAPE", "ESC", "BACKSPACE", "DELETE", "DEL", "INSERT", "HOME", "END",
        "PAGEUP", "PAGEDOWN", "UP", "DOWN", "LEFT", "RIGHT", "UPARROW", "DOWNARROW", "LEFTARROW", "RIGHTARROW",
        "CAPSLOCK", "NUMLOCK", "SCROLLLOCK", "PRINTSCREEN", "PAUSE", "BREAK", "MENU", "APP",
        "CTRL", "CONTROL", "SHIFT", "ALT", "GUI", "WINDOWS", "WIN", "COMMAND", "META",
        "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12",
        "ARROW", "PAGE", "SCROLL", "LOCK" // allow spaced spellings like "PAGE UP", "CAPS LOCK"
    )

    // Commands with no argument.
    private val bareCommands = setOf(
        "ENTER", "MENU", "APP", "PAUSE", "BREAK", "LED", "WAIT_FOR_BUTTON_PRESS",
        "RANDOM_LOWERCASE_LETTER", "RANDOM_UPPERCASE_LETTER", "RANDOM_LETTER", "RANDOM_NUMBER", "RANDOM_SPECIAL", "RANDOM_CHAR"
    )

    private val catalogIds = DuckyCommandCatalog.commands.map { it.id }.toSet()

    fun validate(script: String, defaultDelayMs: Long = 0): DuckyLintResult {
        val issues = mutableListOf<LintIssue>()
        var commands = 0
        var duration = 0L
        var defaultDelay = defaultDelayMs
        var sawDefaultDelay = false
        var prevWasActionable = false
        var inRemBlock = false

        script.lines().forEachIndexed { index, raw ->
            val lineNo = index + 1
            val line = raw.trim()
            if (line.isEmpty()) return@forEachIndexed

            val token = line.substringBefore(' ')
            val arg = line.substringAfter(' ', "").trim()
            val upper = token.uppercase()

            if (inRemBlock) {
                if (upper == "END_REM") inRemBlock = false
                return@forEachIndexed
            }

            when {
                upper == "REM_BLOCK" -> { inRemBlock = true }
                upper == "REM" -> { /* comment */ }
                upper in stringCommands -> {
                    commands++
                    if (arg.isEmpty() && upper != "STRINGLN") {
                        issues += LintIssue(lineNo, LintLevel.WARNING, "$upper has no text; it will type nothing.")
                    }
                    duration += arg.length * 3L + defaultDelay
                    prevWasActionable = true
                }
                upper in numberCommands -> {
                    val n = arg.toLongOrNull()
                    when {
                        arg.isEmpty() -> issues += LintIssue(lineNo, LintLevel.ERROR, "$upper needs a number.")
                        n == null -> issues += LintIssue(lineNo, LintLevel.ERROR, "$upper expects a number, got \"$arg\".")
                        n < 0 -> issues += LintIssue(lineNo, LintLevel.ERROR, "$upper can't be negative.")
                        else -> when (upper) {
                            "DELAY" -> { duration += n; if (n > 10000) issues += LintIssue(lineNo, LintLevel.INFO, "Long delay (${n}ms).") }
                            "DEFAULTDELAY", "DEFAULT_DELAY" -> { defaultDelay = n; sawDefaultDelay = true }
                            "REPEAT" -> {
                                if (!prevWasActionable) issues += LintIssue(lineNo, LintLevel.WARNING, "REPEAT with no command before it.")
                                duration += n * 50
                            }
                        }
                    }
                    commands++
                }
                upper == "STRING_DELAY" || upper == "STRINGDELAY" -> commands++
                else -> {
                    // Treat as a key / key-combo line: every token must be a known key, or a
                    // single literal character pressed with a modifier (e.g. "GUI r", "CTRL c").
                    val parts = line.split(Regex("\\s+"))
                    fun known(t: String) = t.uppercase() in keyTokens || t.uppercase() in bareCommands || t.length == 1
                    val unknown = parts.filterNot { known(it) }
                    when {
                        parts.all { known(it) } -> {
                            commands++
                            duration += defaultDelay + 5
                            prevWasActionable = true
                        }
                        upper in catalogIds -> { commands++; prevWasActionable = true }
                        unknown.size == parts.size && parts.size == 1 ->
                            issues += LintIssue(lineNo, LintLevel.ERROR, "Unknown command \"$token\". Did you mean ${suggest(upper) ?: "STRING"}?")
                        else ->
                            issues += LintIssue(lineNo, LintLevel.ERROR, "Unrecognized key(s): ${unknown.joinToString()}. Use STRING to type literal text.")
                    }
                }
            }
        }

        if (inRemBlock) issues += LintIssue(script.lines().size, LintLevel.WARNING, "REM_BLOCK was never closed with END_REM.")
        if (commands == 0) issues += LintIssue(1, LintLevel.WARNING, "Script is empty.")
        if (!sawDefaultDelay && commands > 3)
            issues += LintIssue(1, LintLevel.INFO, "No DEFAULTDELAY set. On slow or busy targets, add one so keystrokes aren't dropped.")

        return DuckyLintResult(issues.sortedBy { it.line }, commands, duration)
    }

    private fun suggest(token: String): String? {
        val all = catalogIds + keyTokens + stringCommands + numberCommands
        return all.minByOrNull { levenshtein(token, it) }?.takeIf { levenshtein(token, it) <= 2 }
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = if (a[i - 1] == b[j - 1]) prev else 1 + minOf(prev, dp[j], dp[j - 1])
                prev = tmp
            }
        }
        return dp[b.length]
    }
}
