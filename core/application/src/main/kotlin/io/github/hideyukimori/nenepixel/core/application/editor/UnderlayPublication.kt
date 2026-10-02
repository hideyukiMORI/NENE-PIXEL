package io.github.hideyukimori.nenepixel.core.application.editor

/**
 * What one publication writes (ADR 0034): the departing capture first, then the current work's
 * value. [departing] is the captured instance itself; [current] is absent when the current value is
 * not part of this publication. It is passed back unchanged to
 * [UnderlayMemoryTracking.publicationCompleted].
 */
internal data class UnderlayPublication(
    val installation: Long,
    val departing: DepartingUnderlay?,
    val current: UnderlayMemoryWrite?,
) {
    val writes: List<UnderlayMemoryWrite>
        get() = listOfNotNull(departing?.write(), current)
}
