package io.github.hideyukimori.nenepixel.core.application.document.transition

import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition

internal sealed interface PaletteTransition {
    data object Unchanged : PaletteTransition

    data class Changed(
        val before: PaletteDefinition,
        val after: PaletteDefinition,
    ) : PaletteTransition

    fun inverse(): PaletteTransition =
        when (this) {
            Unchanged -> this
            is Changed -> Changed(after, before)
        }

    val retainedByteCount: Long
        get() =
            when (this) {
                Unchanged -> 0L
                is Changed -> COLOR_BYTES * (before.palette.entryCount + after.palette.entryCount) + DEFAULT_BYTES
            }
}

private const val COLOR_BYTES: Long = 4L
private const val DEFAULT_BYTES: Long = 8L
