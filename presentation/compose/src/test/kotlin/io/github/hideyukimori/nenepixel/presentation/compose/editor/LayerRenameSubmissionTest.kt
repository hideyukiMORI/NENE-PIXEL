package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.domain.layer.LayerLimits
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerName
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.presentation.compose.R
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The rename dialog's confirmation (#144 U7): unchanged, renamed, or a refused name mapped to its message. */
internal class LayerRenameSubmissionTest {
    @Test
    fun `the current name is unchanged and another valid name is a rename`() {
        val sky = name("Sky")
        assertEquals(LayerRenameSubmission.Unchanged, layerRenameSubmissionOf("Sky", sky))
        assertEquals(LayerRenameSubmission.Renamed(name("Sea")), layerRenameSubmissionOf("Sea", sky))
        assertEquals(LayerRenameSubmission.Renamed(LayerName.empty), layerRenameSubmissionOf("", sky))
        assertEquals(LayerRenameSubmission.Unchanged, layerRenameSubmissionOf("", LayerName.empty))
    }

    @Test
    fun `length counts code points up to the limit`() {
        val limit = LayerLimits.MAX_NAME_CODE_POINTS
        val emoji = "\uD83C\uDFA8".repeat(limit)
        assertEquals(LayerRenameSubmission.Renamed(name("あ".repeat(limit))), submit("あ".repeat(limit)))
        assertEquals(LayerRenameSubmission.Renamed(name(emoji)), submit(emoji))
        assertEquals(rejected(LayerRenameProblem.TooLong), submit("あ".repeat(limit + 1)))
    }

    @Test
    fun `control characters and unpaired surrogates are invalid characters`() {
        assertEquals(rejected(LayerRenameProblem.Invalid), submit("a\nb"))
        assertEquals(rejected(LayerRenameProblem.Invalid), submit("a\tb"))
        assertEquals(rejected(LayerRenameProblem.Invalid), submit("a\uD83C"))
    }

    @Test
    fun `each problem names its message resource`() {
        assertEquals(R.plurals.layer_rename_too_long, LayerRenameProblem.TooLong.message)
        assertEquals(R.string.layer_rename_invalid, LayerRenameProblem.Invalid.message)
    }

    private fun submit(input: String): LayerRenameSubmission = layerRenameSubmissionOf(input, LayerName.empty)

    private fun rejected(problem: LayerRenameProblem): LayerRenameSubmission = LayerRenameSubmission.Rejected(problem)

    private fun name(value: String): LayerName =
        when (val result = LayerName.create(value)) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Invalid test layer name: ${result.rejection}")
        }
}
