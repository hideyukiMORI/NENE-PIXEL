package io.github.hideyukimori.nenepixel

/**
 * One immutable sample of everything the autosave scheduler may react to. It carries no core type so
 * that the scheduling rules stay decidable without a runtime, a clock, or a persistence workflow.
 *
 * [gate] is the opaque persistence-operation projection observed with this sample. The scheduler
 * never inspects it; a changed gate is the only signal that releases a suspended schedule, because
 * an unadopted recovery offer and an unavailable recovery lineage are both resolved through that
 * projection.
 */
internal data class AutosaveObservation(
    val pendingRevision: Long?,
    val publishedRevision: Long?,
    val gate: Any?,
    val flushes: Long,
)
