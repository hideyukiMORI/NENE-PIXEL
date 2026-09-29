package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.domain.layer.LayerName
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

/** What confirming the rename dialog does (#144 U7): nothing, a rename, or a problem shown under the field. */
internal sealed interface LayerRenameSubmission {
    /** The input is the current name; the dialog closes without a command, so history does not grow. */
    data object Unchanged : LayerRenameSubmission

    data class Renamed(
        val name: LayerName,
    ) : LayerRenameSubmission

    data class Rejected(
        val problem: LayerRenameProblem,
    ) : LayerRenameSubmission
}

/** Validates [input] with `LayerName.create` only now, at confirmation, and compares it with [current]. */
internal fun layerRenameSubmissionOf(
    input: String,
    current: LayerName,
): LayerRenameSubmission =
    when (val result = LayerName.create(input)) {
        is DomainValueResult.Created -> {
            val name = result.value
            if (name == current) LayerRenameSubmission.Unchanged else LayerRenameSubmission.Renamed(name)
        }

        is DomainValueResult.Rejected -> {
            LayerRenameSubmission.Rejected(layerRenameProblemOf(result.rejection))
        }
    }
