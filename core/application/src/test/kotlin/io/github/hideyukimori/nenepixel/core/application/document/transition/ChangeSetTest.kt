package io.github.hideyukimori.nenepixel.core.application.document.transition

import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.appliedSnapshot
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.black
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.patch
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.red
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.revision
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.state
import io.github.hideyukimori.nenepixel.core.application.document.transition.DocumentTransitionAssertions.created
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelRegion
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelChange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

internal class ChangeSetTest {
    @Test
    fun `change set owns canonical patch inverse revisions and render invalidation`() {
        val canvas = canvas(4, 3)
        val original = state(canvas, revision(4L))
        val input =
            mutableListOf(
                PixelChange.create(position(3, 2), black, red),
                PixelChange.create(position(1, 0), black, red),
            )
        val patch = patch(canvas, original.revision, input)
        val changeSet = created(DocumentTransition.create(original, patch)).changeSet

        input.clear()

        assertEquals(revision(4L), changeSet.beforeRevision)
        assertEquals(revision(5L), changeSet.afterRevision)
        assertEquals(region(canvas, position(1, 0), canvas(3, 3)), changeSet.renderInvalidation)
        assertEquals(revision(5L), changeSet.inversePatch.beforeRevision)
        assertEquals(revision(4L), changeSet.inversePatch.afterRevision)
        assertEquals(changeSet.renderInvalidation, changeSet.inversePatch.affectedRegion)

        val changed = appliedSnapshot(changeSet.patch.applyTo(original.snapshot))
        val restored = appliedSnapshot(changeSet.inversePatch.applyTo(changed))
        assertEquals(original.snapshot, restored)
    }

    @Test
    fun `identical state and patch data produce equal change sets`() {
        val canvas = canvas(1, 1)
        val original = state(canvas)
        val firstPatch = patch(canvas, original.revision, listOf(PixelChange.create(position(0, 0), black, red)))
        val secondPatch = patch(canvas, original.revision, listOf(PixelChange.create(position(0, 0), black, red)))
        val first = created(DocumentTransition.create(original, firstPatch)).changeSet
        val second = created(DocumentTransition.create(original, secondPatch)).changeSet

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    private fun region(
        canvas: io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize,
        origin: io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition,
        size: io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize,
    ): PixelRegion =
        when (val result = PixelRegion.create(canvas, origin, size)) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> fail("Test region was rejected: ${result.rejection}")
        }
}
