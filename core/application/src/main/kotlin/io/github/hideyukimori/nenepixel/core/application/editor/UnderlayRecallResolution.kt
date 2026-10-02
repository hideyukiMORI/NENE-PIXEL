package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay

/** What a recall completion means for the workspace (ADR 0034). */
internal sealed interface UnderlayRecallResolution {
    /** Reduce [underlay] into the workspace, unless a switch is installing another work. */
    data class Restore(
        val underlay: ReferenceUnderlay,
    ) : UnderlayRecallResolution

    /** The store became known; the workspace is left alone. */
    data object Keep : UnderlayRecallResolution

    /** The completion is stale; nothing changed. */
    data object Dropped : UnderlayRecallResolution
}
