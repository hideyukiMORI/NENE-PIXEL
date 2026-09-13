package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.hideyukimori.nenepixel.core.application.persistence.LegacyImportProjection
import io.github.hideyukimori.nenepixel.core.application.persistence.LegacyOriginalCopyStatus
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationPhase
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationProjection
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.presentation.compose.R

@Composable
internal fun LegacyConversionDialog(
    operation: PersistenceOperationProjection,
    current: PaletteDefinition,
    callbacks: EditorPersistenceCallbacks,
) {
    val phase = operation.phase
    if (phase is PersistenceOperationPhase.LegacyImport &&
        phase !is PersistenceOperationPhase.NeedsLegacyConfirmation
    ) {
        val inputs = LegacyConversionInputs(phase.import, phase, current, callbacks)
        Dialog(
            onDismissRequest = { inputs.cancel() },
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
        ) {
            Surface(
                modifier =
                    Modifier
                        .widthIn(max = 960.dp)
                        .fillMaxSize(DIALOG_FRACTION)
                        .semantics { testTagsAsResourceId = true },
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
            ) {
                LegacyConversionContent(inputs)
            }
        }
    }
}

@Composable
private fun LegacyConversionContent(inputs: LegacyConversionInputs) {
    var acknowledged by remember(inputs.source.reduction?.handle) { mutableStateOf(false) }
    val acknowledgement = LegacyAcknowledgement(acknowledged) { acknowledged = it }
    Column {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(stringResource(R.string.legacy_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.legacy_intro, inputs.source.distinctColorCount),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        HorizontalDivider()
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            if (maxWidth > maxHeight && maxWidth >= 480.dp) {
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp)) {
                        LegacyPreviewComparison(inputs.source)
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp)) {
                        LegacyConversionOptions(inputs, acknowledgement)
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
                    LegacyPreviewComparison(inputs.source)
                    LegacyConversionOptions(inputs, acknowledgement)
                }
            }
        }
        HorizontalDivider()
        LegacyConversionFooter(inputs, acknowledged)
    }
}

@Composable
private fun LegacyConversionFooter(
    inputs: LegacyConversionInputs,
    acknowledged: Boolean,
) {
    val reduction = inputs.source.reduction
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = { inputs.cancel() },
            enabled = inputs.phase !is PersistenceOperationPhase.CancellingLegacyImport,
            modifier =
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .editorDescription(R.string.legacy_cancel, identity = "legacy_cancel"),
        ) { Text(stringResource(R.string.legacy_cancel)) }
        Button(
            onClick = { reduction?.let { inputs.callbacks.conversion.accept(it.handle) } },
            enabled =
                inputs.ready && reduction != null && acknowledged &&
                    inputs.source.originalCopy != LegacyOriginalCopyStatus.REQUIRED,
            modifier =
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .editorDescription(R.string.legacy_apply, identity = "legacy_apply"),
        ) { Text(stringResource(R.string.legacy_apply)) }
    }
}

internal data class LegacyConversionInputs(
    val source: LegacyImportProjection,
    val phase: PersistenceOperationPhase.LegacyImport,
    val current: PaletteDefinition,
    val callbacks: EditorPersistenceCallbacks,
) {
    val ready: Boolean get() = phase is PersistenceOperationPhase.LegacyConversionRequired

    fun cancel() {
        if (phase !is PersistenceOperationPhase.CancellingLegacyImport) callbacks.onCancel(source.operation)
    }
}

internal data class LegacyAcknowledgement(
    val checked: Boolean,
    val change: (Boolean) -> Unit,
)

private const val DIALOG_FRACTION = 0.95f
