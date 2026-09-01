package io.github.hideyukimori.nenepixel.core.application.document.transition

import io.github.hideyukimori.nenepixel.core.application.document.command.RejectionReason
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.black
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.colorAt
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.green
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.patch
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.red
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.revision
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.state
import io.github.hideyukimori.nenepixel.core.application.document.transition.DocumentTransitionAssertions.created
import io.github.hideyukimori.nenepixel.core.application.document.transition.DocumentTransitionAssertions.rejected
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelChange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

internal class DocumentTransitionTest {
    @Test
    fun `valid patch produces one deterministic next state and complete change set`() {
        val current = state(canvas(2, 1))
        val patch =
            patch(
                current.size,
                current.revision,
                listOf(PixelChange.create(position(1, 0), black, red)),
            )

        val first = created(DocumentTransition.create(current, patch))
        val second = created(DocumentTransition.create(current, patch))

        assertEquals(first, second)
        assertEquals(current.id, first.nextState.id)
        assertEquals(current.size, first.nextState.size)
        assertEquals(revision(1L), first.nextState.revision)
        assertEquals(red, colorAt(first.nextState.snapshot, position(1, 0)))
        assertEquals(black, colorAt(current.snapshot, position(1, 0)))
        assertEquals(first.changeSet, second.changeSet)
    }

    @Test
    fun `canvas mismatch is typed and leaves the current state unchanged`() {
        val current = state(canvas(2, 1))
        val smallerCanvas = canvas(1, 1)
        val patch =
            patch(
                smallerCanvas,
                current.revision,
                listOf(PixelChange.create(position(0, 0), black, red)),
            )

        val reason = rejected(DocumentTransition.create(current, patch))

        val mismatch = assertInstanceOf(RejectionReason.CanvasMismatch::class.java, reason)
        assertEquals(smallerCanvas, mismatch.expected)
        assertEquals(current.size, mismatch.actual)
        assertEquals(revision(0L), current.revision)
        assertEquals(black, colorAt(current.snapshot, position(0, 0)))
        assertEquals(black, colorAt(current.snapshot, position(1, 0)))
    }

    @Test
    fun `revision mismatch is typed and leaves the current state unchanged`() {
        val current = state(canvas(1, 1), revision(2L))
        val patch =
            patch(
                current.size,
                revision(1L),
                listOf(PixelChange.create(position(0, 0), black, red)),
            )

        val reason = rejected(DocumentTransition.create(current, patch))

        val mismatch = assertInstanceOf(RejectionReason.RevisionMismatch::class.java, reason)
        assertEquals(revision(1L), mismatch.expected)
        assertEquals(revision(2L), mismatch.actual)
        assertEquals(revision(2L), current.revision)
        assertEquals(black, colorAt(current.snapshot, position(0, 0)))
    }

    @Test
    fun `before value mismatch is typed and leaves the current state unchanged`() {
        val current = state(canvas(1, 1), pixels = listOf(green))
        val patch =
            patch(
                current.size,
                current.revision,
                listOf(PixelChange.create(position(0, 0), black, red)),
            )

        val reason = rejected(DocumentTransition.create(current, patch))

        val mismatch = assertInstanceOf(RejectionReason.PixelBeforeValueMismatch::class.java, reason)
        assertEquals(position(0, 0), mismatch.position)
        assertEquals(black, mismatch.expected)
        assertEquals(green, mismatch.actual)
        assertEquals(revision(0L), current.revision)
        assertEquals(green, colorAt(current.snapshot, position(0, 0)))
    }
}
