package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.RememberedUnderlay

/** What the tracking knows of the store for the installed work (ADR 0034). */
internal sealed interface UnderlayStoreKnowledge {
    /** Installed, the recall has not completed, and no underlay action was reduced since. */
    data object Unknown : UnderlayStoreKnowledge

    /** Installed, the recall has not completed, and an underlay action was reduced since. */
    data object UnknownTouched : UnderlayStoreKnowledge

    /** The store holds [remembered]; null means it holds nothing for the work. */
    data class Known(
        val remembered: RememberedUnderlay?,
    ) : UnderlayStoreKnowledge
}
