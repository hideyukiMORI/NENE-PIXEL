package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.domain.frame.FrameDuration

/** An invalid field is a typed outcome, never an absent duration. */
internal sealed interface FrameDurationInputResult {
    data class Valid(
        val duration: FrameDuration,
    ) : FrameDurationInputResult

    data object Invalid : FrameDurationInputResult
}
