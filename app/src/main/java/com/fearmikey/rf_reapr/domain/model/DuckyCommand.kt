package com.fearmikey.rf_reapr.domain.model

/**
 * Categories used to group DuckyScript commands in the script builder UI.
 */
enum class DuckyCategory(val displayName: String) {
    TEXT("Text"),
    TIMING("Timing"),
    NAVIGATION("Keys"),
    FUNCTION_KEYS("Function Keys"),
    SHORTCUTS("Shortcuts"),
    ADVANCED("Advanced")
}

/** The kind of user input (if any) a command needs before it can be inserted. */
enum class DuckyParamType {
    NONE,
    TEXT,
    NUMBER
}

/**
 * Describes a single selectable DuckyScript "function" offered by the script builder.
 * [lineBuilder] turns the optional user-supplied parameter into the literal DuckyScript
 * line that gets appended to the script being edited.
 */
data class DuckyCommandSpec(
    val id: String,
    val label: String,
    val category: DuckyCategory,
    val paramType: DuckyParamType = DuckyParamType.NONE,
    val paramLabel: String = "",
    val defaultParam: String = "",
    val helpText: String = "",
    val lineBuilder: (String) -> String
)

/** Catalog of all built-in DuckyScript commands offered by the script builder. */
object DuckyCommandCatalog {

    val commands: List<DuckyCommandSpec> = buildList {
        // ---- Text ----
        add(
            DuckyCommandSpec(
                id = "STRING",
                label = "STRING",
                category = DuckyCategory.TEXT,
                paramType = DuckyParamType.TEXT,
                paramLabel = "Text to type",
                helpText = "Types the given text literally, character by character.",
                lineBuilder = { "STRING $it" }
            )
        )
        add(
            DuckyCommandSpec(
                id = "STRINGLN",
                label = "STRINGLN",
                category = DuckyCategory.TEXT,
                paramType = DuckyParamType.TEXT,
                paramLabel = "Text to type",
                helpText = "Types the given text and presses ENTER afterwards.",
                lineBuilder = { "STRINGLN $it" }
            )
        )
        add(
            DuckyCommandSpec(
                id = "REM",
                label = "REM (Comment)",
                category = DuckyCategory.ADVANCED,
                paramType = DuckyParamType.TEXT,
                paramLabel = "Comment text",
                helpText = "Adds a human-readable comment line; ignored at execution time.",
                lineBuilder = { "REM $it" }
            )
        )

        // ---- Timing ----
        add(
            DuckyCommandSpec(
                id = "DELAY",
                label = "DELAY",
                category = DuckyCategory.TIMING,
                paramType = DuckyParamType.NUMBER,
                paramLabel = "Milliseconds",
                defaultParam = "500",
                helpText = "Pauses execution for the given number of milliseconds.",
                lineBuilder = { "DELAY $it" }
            )
        )
        add(
            DuckyCommandSpec(
                id = "DEFAULTDELAY",
                label = "DEFAULT DELAY",
                category = DuckyCategory.TIMING,
                paramType = DuckyParamType.NUMBER,
                paramLabel = "Milliseconds between commands",
                defaultParam = "100",
                helpText = "Sets a delay automatically applied before every following command.",
                lineBuilder = { "DEFAULTDELAY $it" }
            )
        )
        add(
            DuckyCommandSpec(
                id = "REPEAT",
                label = "REPEAT",
                category = DuckyCategory.ADVANCED,
                paramType = DuckyParamType.NUMBER,
                paramLabel = "Extra repeats of previous line",
                defaultParam = "1",
                helpText = "Repeats the previous command N additional times (DuckyScript 2.0+).",
                lineBuilder = { "REPEAT $it" }
            )
        )

        // ---- Keys / navigation ----
        val simpleKeys = listOf(
            "ENTER" to "ENTER",
            "TAB" to "TAB",
            "SPACE" to "SPACE",
            "ESCAPE" to "ESCAPE",
            "BACKSPACE" to "BACKSPACE",
            "DELETE" to "DELETE",
            "HOME" to "HOME",
            "END" to "END",
            "PAGEUP" to "PAGE UP",
            "PAGEDOWN" to "PAGE DOWN",
            "UPARROW" to "UP ARROW",
            "DOWNARROW" to "DOWN ARROW",
            "LEFTARROW" to "LEFT ARROW",
            "RIGHTARROW" to "RIGHT ARROW",
            "INSERT" to "INSERT",
            "CAPSLOCK" to "CAPS LOCK"
        )
        simpleKeys.forEach { (token, label) ->
            add(DuckyCommandSpec(id = token, label = label, category = DuckyCategory.NAVIGATION) { token })
        }

        // ---- Function keys ----
        (1..12).forEach { n ->
            add(DuckyCommandSpec(id = "F$n", label = "F$n", category = DuckyCategory.FUNCTION_KEYS) { "F$n" })
        }

        // ---- Common shortcuts ----
        val shortcuts = listOf(
            Triple("GUI_R", "GUI r (Run dialog)", "GUI r"),
            Triple("GUI_D", "GUI d (Show desktop)", "GUI d"),
            Triple("GUI_L", "GUI l (Lock)", "GUI l"),
            Triple("GUI_E", "GUI e (Explorer)", "GUI e"),
            Triple("CTRL_ALT_DEL", "CTRL ALT DELETE", "CTRL ALT DELETE"),
            Triple("CTRL_SHIFT_ESC", "CTRL SHIFT ESC (Task Manager)", "CTRL SHIFT ESCAPE"),
            Triple("ALT_F4", "ALT F4 (Close window)", "ALT F4"),
            Triple("ALT_TAB", "ALT TAB (Switch window)", "ALT TAB"),
            Triple("CTRL_C", "CTRL c (Copy)", "CTRL c"),
            Triple("CTRL_V", "CTRL v (Paste)", "CTRL v"),
            Triple("CTRL_X", "CTRL x (Cut)", "CTRL x"),
            Triple("CTRL_A", "CTRL a (Select all)", "CTRL a"),
            Triple("CTRL_Z", "CTRL z (Undo)", "CTRL z"),
            Triple("CTRL_S", "CTRL s (Save)", "CTRL s"),
            Triple("CTRL_SHIFT_N", "CTRL SHIFT n (New folder)", "CTRL SHIFT n"),
            Triple("PRINTSCREEN", "PRINTSCREEN", "PRINTSCREEN")
        )
        shortcuts.forEach { (id, label, line) ->
            add(DuckyCommandSpec(id = id, label = label, category = DuckyCategory.SHORTCUTS) { line })
        }
    }

    fun byCategory(): Map<DuckyCategory, List<DuckyCommandSpec>> = commands.groupBy { it.category }
}
