package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType

/**
 * Pure helpers for integer text fields that must allow clearing mid-edit.
 * Binding [Int.toString] directly and ignoring empty input makes the last digit undeletable.
 */
object IntFieldEditing {
    fun filterDigits(raw: String, maxLen: Int = 9): String =
        raw.filter { it.isDigit() }.take(maxLen.coerceAtLeast(0))

    /** Commit only when the draft parses and is inside [range] (no coerce while typing). */
    fun commitIfInRange(text: String, range: IntRange): Int? =
        text.toIntOrNull()?.takeIf { it in range }

    /**
     * On blur: coerce into range when possible; otherwise restore [current].
     * Returns display text and an optional value to persist.
     */
    fun finalizeOnBlur(text: String, current: Int, range: IntRange): Pair<String, Int?> {
        val coerced = text.toIntOrNull()?.coerceIn(range.first, range.last)
        return if (coerced != null) {
            coerced.toString() to coerced.takeIf { it != current }
        } else {
            current.toString() to null
        }
    }
}

/**
 * Integer [OutlinedTextField] that keeps a local draft so the field can be emptied
 * while editing. Values outside [range] are not committed until blur (then coerced).
 */
@Composable
fun IntOutlinedTextField(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = 0..Int.MAX_VALUE,
    label: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    var text by remember { mutableStateOf(value.toString()) }
    var focused by remember { mutableStateOf(false) }

    LaunchedEffect(value, focused) {
        if (!focused) {
            text = value.toString()
        }
    }

    val parsed = text.toIntOrNull()
    val showError = text.isNotEmpty() && (parsed == null || parsed !in range)

    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val filtered = IntFieldEditing.filterDigits(raw)
            text = filtered
            IntFieldEditing.commitIfInRange(filtered, range)?.let(onValueChange)
        },
        modifier = modifier.onFocusChanged { state ->
            val nowFocused = state.isFocused
            if (focused && !nowFocused) {
                val (display, commit) = IntFieldEditing.finalizeOnBlur(text, value, range)
                text = display
                commit?.let(onValueChange)
            }
            focused = nowFocused
        },
        label = label,
        supportingText = supportingText,
        isError = showError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        enabled = enabled,
    )
}

@Composable
fun <T : Number> OutlinedNumberInput(
    value: T,
    onValueChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors()
) {
    var textFieldValue by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        modifier = modifier,
        value = textFieldValue,
        onValueChange = { newValue ->
            textFieldValue = newValue
            if (textFieldValue.isValidNumberInput()) {
                try {
                    @Suppress("UNCHECKED_CAST")
                    val newVal = when (value) {
                        is Int -> newValue.toInt() as T
                        is Float -> newValue.toFloat() as T
                        is Double -> newValue.toDouble() as T
                        else -> throw IllegalArgumentException("Unsupported number type")
                    }
                    onValueChange(newVal)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = !textFieldValue.isValidNumberInput(),
        colors = colors
    )
}

@Composable
fun <T : Number> NumberInput(
    value: T,
    onValueChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    colors: TextFieldColors = TextFieldDefaults.colors()
) {
    var textFieldValue by remember(value) { mutableStateOf(value.toString()) }
    TextField(
        modifier = modifier,
        value = textFieldValue,
        onValueChange = { newValue ->
            textFieldValue = newValue
            if (textFieldValue.isValidNumberInput()) {
                try {
                    @Suppress("UNCHECKED_CAST")
                    val newVal = when (value) {
                        is Int -> newValue.toInt() as T
                        is Float -> newValue.toFloat() as T
                        is Double -> newValue.toDouble() as T
                        else -> throw IllegalArgumentException("Unsupported number type")
                    }
                    onValueChange(newVal)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = !textFieldValue.isValidNumberInput(),
        colors = colors
    )
}

private val NumberRegex = Regex("^[+-]?\\d+(\\.\\d+)?$")
private fun String.isValidNumberInput() = this.isNotEmpty() && NumberRegex.matches(this)
