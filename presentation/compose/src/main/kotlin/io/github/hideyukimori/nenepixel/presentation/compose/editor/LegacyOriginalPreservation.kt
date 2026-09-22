package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.core.application.persistence.LegacyCopyAttemptOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.LegacyImportOrigin
import io.github.hideyukimori.nenepixel.core.application.persistence.LegacyOriginalCopyStatus
import io.github.hideyukimori.nenepixel.core.application.persistence.PartialOutputCleanup
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationPhase
import io.github.hideyukimori.nenepixel.presentation.compose.R

@Composable
internal fun LegacyOriginalPreservation(inputs: LegacyConversionInputs) {
    val verified = inputs.source.originalCopy == LegacyOriginalCopyStatus.VERIFIED
    val copyLabel = if (verified) R.string.legacy_copy_again else R.string.legacy_copy
    Column(Modifier.padding(top = 16.dp)) {
        Text(stringResource(R.string.legacy_preserve_title), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(inputs.source.originalCopy.descriptionResource()),
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(
            onClick = { inputs.callbacks.conversion.copyOriginal(inputs.source.operation) },
            enabled = inputs.ready,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .editorDescription(copyLabel, identity = "legacy_copy"),
        ) { Text(stringResource(copyLabel)) }
        LegacyCopyFeedback(inputs.source.latestCopyOutcome)
        LegacyBusyStatus(inputs.phase)
        if (inputs.source.origin == LegacyImportOrigin.RECOVERY) {
            TextButton(
                onClick = { inputs.callbacks.conversion.declineRecovery(inputs.source.operation) },
                enabled = inputs.ready && verified,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .editorDescription(R.string.legacy_discard_recovery, identity = "legacy_discard_recovery"),
            ) { Text(stringResource(R.string.legacy_discard_recovery)) }
        }
    }
}

@Composable
private fun LegacyCopyFeedback(outcome: LegacyCopyAttemptOutcome) {
    when (outcome) {
        LegacyCopyAttemptOutcome.NotAttempted, LegacyCopyAttemptOutcome.Copied -> {
            // The durable original-copy status already supplies this feedback.
        }

        LegacyCopyAttemptOutcome.Cancelled -> {
            Text(
                stringResource(R.string.legacy_copy_cancelled),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        is LegacyCopyAttemptOutcome.Failed -> {
            Text(
                stringResource(R.string.legacy_copy_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
            if (outcome.cleanup == PartialOutputCleanup.DELETE_FAILED) {
                Text(stringResource(R.string.legacy_copy_cleanup_failed), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun LegacyBusyStatus(phase: PersistenceOperationPhase.LegacyImport) {
    if (phase !is PersistenceOperationPhase.LegacyConversionRequired) {
        val label = phase.conversionStatusResource()
        Text(
            stringResource(label),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.editorDescription(label, identity = "legacy_busy"),
        )
    }
}

private fun LegacyOriginalCopyStatus.descriptionResource(): Int =
    when (this) {
        LegacyOriginalCopyStatus.OPTIONAL -> R.string.legacy_copy_optional
        LegacyOriginalCopyStatus.REQUIRED -> R.string.legacy_copy_required
        LegacyOriginalCopyStatus.VERIFIED -> R.string.legacy_copy_verified
    }
