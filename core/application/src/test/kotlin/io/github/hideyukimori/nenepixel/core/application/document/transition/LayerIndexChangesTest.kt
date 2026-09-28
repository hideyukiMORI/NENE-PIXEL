package io.github.hideyukimori.nenepixel.core.application.document.transition

import io.github.hideyukimori.nenepixel.core.application.document.command.ApplyStrokeCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandGateway
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResultAssertions.applied
import io.github.hideyukimori.nenepixel.core.application.document.command.RejectionReason
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.blackIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.patch
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.redIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.revision
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.snapshot
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.state
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.stroke
import io.github.hideyukimori.nenepixel.core.application.document.transition.DocumentTransitionAssertions.created
import io.github.hideyukimori.nenepixel.core.application.document.transition.DocumentTransitionAssertions.rejected
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelCell
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelLimits
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelChange
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelPatch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

internal class LayerIndexChangesTest {
    private val fullCanvas: CanvasSize = canvas(PixelLimits.MAX_CANVAS_AXIS, PixelLimits.MAX_CANVAS_AXIS)

    @Test
    fun `largest sparse change and one more select sparse then dense on a full canvas`() {
        val before = snapshot(fullCanvas)

        val sparse = LayerIndexChanges.select(before, redPatch(before, 24_576))
        val dense = LayerIndexChanges.select(before, redPatch(before, 24_577))

        assertInstanceOf(LayerIndexChanges.Sparse::class.java, sparse)
        assertEquals(24_576, sparse.changeCount)
        assertEquals(147_456L, sparse.retainedByteCount)
        val denseChanges = assertInstanceOf(LayerIndexChanges.Dense::class.java, dense)
        assertEquals(0, dense.changeCount)
        assertEquals(2L * (65_536L + 8_192L), dense.retainedByteCount)
        assertEquals(before, denseChanges.before)
    }

    @Test
    fun `dense inverse swaps snapshots and restores the source layer`() {
        val original = state(fullCanvas)
        val dense = LayerIndexChanges.select(original.snapshot, redPatch(original.snapshot, 24_577))
        val changeSet = denseChangeSet(original, dense)

        val forward = created(DocumentTransition.create(original, changeSet))
        val backward = created(DocumentTransition.create(forward.nextState, changeSet.inverse()))

        assertEquals(LayerIndexChanges.Dense((dense as LayerIndexChanges.Dense).after, dense.before), dense.inverse())
        assertEquals(0, changeSet.retainedChangeCount)
        assertEquals(32L + 147_456L, changeSet.retainedByteCount)
        assertEquals(fullCanvas, changeSet.renderInvalidation.size)
        assertEquals(original, backward.nextState)
    }

    @Test
    fun `dense change whose before snapshot differs is rejected as layer snapshot mismatch`() {
        val current = state(canvas(2, 1))
        val other = snapshot(canvas(2, 1), indices = listOf(redIndex, blackIndex))
        val changeSet = denseChangeSet(current, LayerIndexChanges.Dense(other, current.snapshot))

        assertEquals(
            RejectionReason.LayerSnapshotMismatch(LayerId.first()),
            rejected(DocumentTransition.create(current, changeSet)),
        )
    }

    @Test
    fun `change for a layer id absent from the document is rejected as layer not found`() {
        val current = state(canvas(1, 1))
        val missing = LayerId.create(2).requiredValue()
        val sparse = LayerIndexChanges.Sparse(redPatch(current.snapshot, 1))
        val changeSet =
            ChangeSet.create(current, revision(1L), PaletteTransition.Unchanged, listOf(LayerChange(missing, sparse)))

        assertEquals(RejectionReason.LayerNotFound(missing), rejected(DocumentTransition.create(current, changeSet)))
    }

    @Test
    fun `stroke render invalidation stays the affected region of its sparse patch`() {
        val initial = state(canvas(4, 3))
        val gateway = CommandGateway.create(initial)
        val draw = stroke(initial.size, listOf(position(1, 0), position(3, 2)), redIndex)

        val changeSet = applied(gateway.execute(ApplyStrokeCommand.create(gateway.captureSource(), draw)))

        val sparse = assertInstanceOf(LayerIndexChanges.Sparse::class.java, changeSet.layerChanges.single().changes)
        assertEquals(sparse.patch.affectedRegion, changeSet.renderInvalidation)
        assertEquals(position(1, 0), changeSet.renderInvalidation.origin)
        assertEquals(canvas(3, 3), changeSet.renderInvalidation.size)
    }

    private fun redPatch(
        before: PixelSnapshot,
        count: Int,
    ): PixelPatch {
        val width = before.size.width.value
        val changes =
            List(count) { index ->
                PixelChange.create(
                    position(index % width, index / width),
                    PixelCell.Covered(blackIndex),
                    PixelCell.Covered(redIndex),
                )
            }
        return patch(before.size, changes)
    }

    private fun denseChangeSet(
        source: DocumentState,
        changes: LayerIndexChanges,
    ): ChangeSet =
        ChangeSet.create(
            source,
            revision(1L),
            PaletteTransition.Unchanged,
            listOf(LayerChange(LayerId.first(), changes)),
        )

    private fun <T> DomainValueResult<T>.requiredValue(): T =
        when (this) {
            is DomainValueResult.Created -> value
            is DomainValueResult.Rejected -> fail("Test value was rejected: $rejection")
        }
}
