package io.github.hideyukimori.nenepixel.core.application.editor

/** The tracking after a recall completion and what the completion means for the workspace. */
internal data class UnderlayRecallStep(
    val tracking: UnderlayMemoryTracking,
    val resolution: UnderlayRecallResolution,
)
