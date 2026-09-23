package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.presentation.compose.R

/** The minimal RGBA editor (design ruling 7): one `#RRGGBBAA` field and four 0-255 fields, committed on Done. */
@Composable
internal fun PaletteColorEditor(
    color: PixelColor,
    enabled: Boolean,
    commit: (PixelColor) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PaletteEditorTextField(HEX_FIELD, PaletteHexColor.format(color), enabled) { text ->
            PaletteHexColor.parse(text)?.also(commit) != null
        }
        CHANNEL_FIELDS.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { field ->
                    Box(Modifier.weight(1f)) { PaletteChannelField(field, color, enabled, commit) }
                }
            }
        }
    }
}

@Composable
private fun PaletteChannelField(
    field: PaletteEditorField,
    color: PixelColor,
    enabled: Boolean,
    commit: (PixelColor) -> Unit,
) {
    val position = CHANNEL_FIELDS.indexOf(field)
    val value =
        PaletteHexColor
            .channels(color)[position]
            .value
            .toInt()
            .toString()
    PaletteEditorTextField(field, value, enabled) { text ->
        PaletteHexColor.parseChannel(text)?.also { commit(PaletteHexColor.withChannel(color, position, it)) } != null
    }
}

@Composable
private fun PaletteEditorTextField(
    field: PaletteEditorField,
    value: String,
    enabled: Boolean,
    submit: (String) -> Boolean,
) {
    var text by remember(value) { mutableStateOf(value) }
    var invalid by remember(value) { mutableStateOf(false) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            invalid = false
        },
        enabled = enabled,
        isError = invalid,
        label = { Text(stringResource(field.label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = field.keyboardType, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { invalid = !submit(text) }),
        modifier = Modifier.fillMaxWidth().editorDescription(field.label, identity = field.identity),
    )
}

private data class PaletteEditorField(
    val label: Int,
    val identity: String,
    val keyboardType: KeyboardType,
)

private val HEX_FIELD =
    PaletteEditorField(R.string.palette_editor_hex, "editor_palette_editor_hex", KeyboardType.Ascii)

private val CHANNEL_FIELDS =
    listOf(
        PaletteEditorField(R.string.palette_editor_red, "editor_palette_editor_r", KeyboardType.Number),
        PaletteEditorField(R.string.palette_editor_green, "editor_palette_editor_g", KeyboardType.Number),
        PaletteEditorField(R.string.palette_editor_blue, "editor_palette_editor_b", KeyboardType.Number),
        PaletteEditorField(R.string.palette_editor_alpha, "editor_palette_editor_a", KeyboardType.Number),
    )
