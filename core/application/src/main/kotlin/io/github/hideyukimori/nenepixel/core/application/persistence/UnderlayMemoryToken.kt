package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.RememberedUnderlay

/**
 * Opaque equality token of a pending [UnderlayMemoryProjection] (ADR 0034). It compares the
 * installation, the kind of request and, for a publication, the current value and whether a
 * departing capture exists; a departing capture written alone carries no current value. Its contents
 * cannot be read.
 */
public class UnderlayMemoryToken private constructor(
    private val installation: Long,
    private val subject: Subject,
) {
    public override fun equals(other: Any?): Boolean =
        other is UnderlayMemoryToken &&
            installation == other.installation &&
            subject == other.subject

    public override fun hashCode(): Int = HASH_MULTIPLIER * installation.hashCode() + subject.hashCode()

    public override fun toString(): String = "UnderlayMemoryToken"

    private sealed interface Subject {
        data object Recall : Subject

        data class Publication(
            val current: RememberedUnderlay?,
            val departing: DepartingPresence,
        ) : Subject

        /** Writes the departing capture alone while the store of the current work is unknown. */
        data object DepartingOnly : Subject
    }

    private enum class DepartingPresence {
        Present,
        Absent,
    }

    internal companion object {
        private const val HASH_MULTIPLIER: Int = 31

        fun recall(installation: Long): UnderlayMemoryToken = UnderlayMemoryToken(installation, Subject.Recall)

        fun publication(
            installation: Long,
            current: RememberedUnderlay?,
        ): UnderlayMemoryToken =
            UnderlayMemoryToken(installation, Subject.Publication(current, DepartingPresence.Absent))

        fun departingPublication(
            installation: Long,
            current: RememberedUnderlay?,
        ): UnderlayMemoryToken =
            UnderlayMemoryToken(installation, Subject.Publication(current, DepartingPresence.Present))

        /** A publication of the departing capture alone; the unknown current value is not part of it. */
        fun departingOnlyPublication(installation: Long): UnderlayMemoryToken =
            UnderlayMemoryToken(installation, Subject.DepartingOnly)
    }
}
