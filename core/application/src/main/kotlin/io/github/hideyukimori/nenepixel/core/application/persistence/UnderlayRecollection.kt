package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.RememberedUnderlay

/** The closed answer of [UnderlayMemoryPort.recall] (ADR 0034). */
public sealed interface UnderlayRecollection {
    public data class Remembered(
        public val underlay: RememberedUnderlay,
    ) : UnderlayRecollection

    /** Nothing is remembered, or the record could not be read. */
    public data object Absent : UnderlayRecollection
}
