package io.github.hideyukimori.nenepixel.core.projectformat.palette

internal class PaletteJsonSequence(
    private val cursor: PaletteJsonCursor,
) {
    fun read(
        closing: Char,
        element: () -> PaletteJsonResult<Unit>,
    ): PaletteJsonResult<Unit> {
        var step = accepted(if (cursor.consume(closing)) SequenceStep.Done else SequenceStep.More)
        while (step is PaletteJsonResult.Accepted && step.value == SequenceStep.More) {
            step = element().andThen { separator(closing) }
        }
        return step.andThen { accepted(Unit) }
    }

    private fun separator(closing: Char): PaletteJsonResult<SequenceStep> =
        when {
            cursor.consume(closing) -> accepted(SequenceStep.Done)
            cursor.consume(',') -> accepted(SequenceStep.More)
            else -> cursor.invalid()
        }
}

private enum class SequenceStep { More, Done }
