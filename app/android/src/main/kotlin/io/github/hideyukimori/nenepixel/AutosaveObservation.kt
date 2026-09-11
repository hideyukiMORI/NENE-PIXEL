package io.github.hideyukimori.nenepixel

/**
 * One immutable sample of everything the autosave scheduler may react to. State identities remain
 * opaque so that the scheduling rules stay decidable without a runtime, a clock, or a persistence
 * workflow.
 *
 * [gate] is the opaque persistence-operation projection observed with this sample. The scheduler
 * never inspects it; a changed gate releases a persistence-blocked schedule because an unadopted
 * recovery offer and unavailable recovery lineage are both resolved through that projection.
 */
internal data class AutosaveObservation(
    val states: AutosaveStateObservation,
    val gate: Any?,
    val flushes: Long,
)
