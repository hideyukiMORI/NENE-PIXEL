package io.github.hideyukimori.nenepixel.core.pixelengine

import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

public class PixelPatch private constructor(
    public val canvas: CanvasSize,
    public val beforeRevision: Revision,
    public val afterRevision: Revision,
    private val changes: List<PixelChange>,
) {
    public val changeCount: Int
        get() = changes.size

    public fun applyTo(snapshot: PixelSnapshot): PixelPatchApplicationResult =
        when {
            snapshot.size != canvas -> {
                rejected(PixelPatchApplicationRejection.CanvasMismatch(canvas, snapshot.size))
            }

            snapshot.revision != beforeRevision -> {
                rejected(PixelPatchApplicationRejection.RevisionMismatch(beforeRevision, snapshot.revision))
            }

            else -> {
                applyToMatchingSnapshot(snapshot)
            }
        }

    private fun applyToMatchingSnapshot(snapshot: PixelSnapshot): PixelPatchApplicationResult {
        val surface = PixelSurface.from(snapshot)
        val conflict = changes.firstOrNull { change -> surface.colorAt(change.position) != change.before }
        return if (conflict != null) {
            rejected(
                PixelPatchApplicationRejection.BeforeValueMismatch(
                    position = conflict.position,
                    expected = conflict.before,
                    actual = surface.colorAt(conflict.position),
                ),
            )
        } else {
            changes.forEach(surface::write)
            PixelPatchApplicationResult.Applied(surface.snapshot(afterRevision))
        }
    }

    public fun inverse(): PixelPatch =
        PixelPatch(
            canvas = canvas,
            beforeRevision = afterRevision,
            afterRevision = beforeRevision,
            changes = changes.map(PixelChange::inverse),
        )

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is PixelPatch &&
                    canvas == other.canvas &&
                    beforeRevision == other.beforeRevision &&
                    afterRevision == other.afterRevision &&
                    changes == other.changes
            )

    override fun hashCode(): Int = listOf(canvas, beforeRevision, afterRevision, changes).hashCode()

    override fun toString(): String =
        "PixelPatch(canvas=$canvas, beforeRevision=$beforeRevision, " +
            "afterRevision=$afterRevision, changeCount=$changeCount)"

    public companion object {
        public fun create(
            canvas: CanvasSize,
            beforeRevision: Revision,
            changes: List<PixelChange>,
        ): PixelPatchCreationResult {
            val afterRevision =
                beforeRevision.nextOrNull()
                    ?: return creationRejected(PixelPatchCreationRejection.RevisionOverflow)
            val canonicalChanges = changes.sortedWith(ROW_MAJOR_ORDER)
            val rejection = validateChanges(canvas, canonicalChanges)
            return if (rejection == null) {
                PixelPatchCreationResult.Created(PixelPatch(canvas, beforeRevision, afterRevision, canonicalChanges))
            } else {
                creationRejected(rejection)
            }
        }

        private fun validateChanges(
            canvas: CanvasSize,
            changes: List<PixelChange>,
        ): PixelPatchCreationRejection? {
            val outside = changes.firstOrNull { change -> !canvas.contains(change.position) }
            val unchanged = changes.firstOrNull { change -> change.before == change.after }
            val duplicate = changes.zipWithNext().firstOrNull { (first, second) -> first.position == second.position }
            return when {
                changes.isEmpty() -> PixelPatchCreationRejection.EmptyPatch
                outside != null -> PixelPatchCreationRejection.PositionOutsideCanvas(canvas, outside.position)
                unchanged != null -> PixelPatchCreationRejection.UnchangedPixel(unchanged.position)
                duplicate != null -> PixelPatchCreationRejection.DuplicatePosition(duplicate.first.position)
                else -> null
            }
        }

        private fun Revision.nextOrNull(): Revision? =
            when (val result = advance()) {
                is DomainValueResult.Created -> result.value
                is DomainValueResult.Rejected -> null
            }

        private fun creationRejected(rejection: PixelPatchCreationRejection): PixelPatchCreationResult =
            PixelPatchCreationResult.Rejected(rejection)

        private fun rejected(rejection: PixelPatchApplicationRejection): PixelPatchApplicationResult =
            PixelPatchApplicationResult.Rejected(rejection)

        private val ROW_MAJOR_ORDER: Comparator<PixelChange> =
            compareBy(
                { change -> change.position.y.value },
                { change -> change.position.x.value },
            )
    }
}
