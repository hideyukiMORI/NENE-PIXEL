package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.presentation.compose.R

@Composable
internal fun LegacyConversionOptions(
    inputs: LegacyConversionInputs,
    acknowledgement: LegacyAcknowledgement,
) {
    Column {
        Text(stringResource(R.string.legacy_palette_title), style = MaterialTheme.typography.titleSmall)
        inputs.paletteChoices().forEach { choice -> LegacyPaletteChoice(inputs, choice) }
        inputs.source.selectedDestination?.let { definition ->
            Text(stringResource(definition.defaultColorResource()), style = MaterialTheme.typography.bodySmall)
        }
        LegacyOriginalPreservation(inputs)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(
                    value = acknowledgement.checked,
                    enabled = inputs.ready && inputs.source.reduction != null,
                    role = Role.Checkbox,
                    onValueChange = acknowledgement.change,
                ).editorDescription(R.string.legacy_acknowledge, identity = "legacy_acknowledge"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = acknowledgement.checked,
                onCheckedChange = null,
                enabled = inputs.ready && inputs.source.reduction != null,
            )
            Text(
                stringResource(R.string.legacy_acknowledge),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun LegacyPaletteChoice(
    inputs: LegacyConversionInputs,
    choice: LegacyPaletteChoice,
) {
    val definition = choice.definition
    val label = choice.label
    val selected = inputs.source.selectedDestination == definition
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, enabled = inputs.ready, role = Role.RadioButton) {
                inputs.callbacks.conversion.preview(inputs.source.operation, definition)
            }.editorDescription(label, definition.palette.entryCount, identity = choice.identity),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = inputs.ready)
        Column(Modifier.padding(start = 8.dp)) {
            Text(stringResource(label, definition.palette.entryCount), style = MaterialTheme.typography.bodyMedium)
            LegacyPaletteSwatches(definition)
        }
    }
}

private fun PaletteDefinition.defaultColorResource(): Int =
    when (val entry = palette.entryAt(defaultIndex)) {
        is DomainValueResult.Created -> {
            when (
                entry.value.color.alpha.value
                    .toInt()
            ) {
                0 -> R.string.legacy_default_transparent
                OPAQUE_ALPHA -> R.string.legacy_default_opaque
                else -> R.string.legacy_default_translucent
            }
        }

        is DomainValueResult.Rejected -> {
            error("Validated destination lost its default entry")
        }
    }

private const val OPAQUE_ALPHA = 255

private data class LegacyPaletteChoice(
    val definition: PaletteDefinition,
    val label: Int,
    val identity: String,
)

private fun LegacyConversionInputs.paletteChoices(): List<LegacyPaletteChoice> =
    listOf(
        LegacyPaletteChoice(current, R.string.legacy_current_palette, "legacy_palette_current"),
        LegacyPaletteChoice(callbacks.presets.dusk, R.string.legacy_dusk_palette, "legacy_palette_dusk"),
        LegacyPaletteChoice(callbacks.presets.grayscale, R.string.legacy_grayscale_palette, "legacy_palette_grayscale"),
        LegacyPaletteChoice(callbacks.presets.swatches, R.string.legacy_swatches_palette, "legacy_palette_swatches"),
    ).distinctBy { it.definition }
