package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandGateway
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.state
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionAssertions.reduced
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionAssertions.unchanged
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImage
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImageResult
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayInteraction
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayOpacity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ReferenceUnderlayReducerTest {
    private val reducer = WorkspaceReducer.create()
    private val initial = WorkspaceState.create(canvas(4, 4))
    private val admission = CommandGateway.create(state(canvas(4, 4))).captureSource()
    private val underlay = ReferenceUnderlay.placed(image(8, 4), canvas(4, 4))

    @Test
    fun `the workspace starts without an underlay`() {
        assertNull(initial.underlay)
    }

    @Test
    fun `set installs the underlay and clear removes it, leaving the rest of the workspace untouched`() {
        val set = reduced(reduce(initial, WorkspaceAction.SetReferenceUnderlay(underlay)))
        assertEquals(underlay, set.underlay)
        assertEquals(initial.withUnderlay(underlay), set)
        assertSame(initial.viewport, set.viewport)
        assertEquals(initial.actualSizeWindow, set.actualSizeWindow)
        val cleared = reduced(reduce(set, WorkspaceAction.ClearReferenceUnderlay))
        assertNull(cleared.underlay)
        assertEquals(initial, cleared)
    }

    @Test
    fun `an equal underlay is unchanged and clearing nothing is unchanged`() {
        val set = reduced(reduce(initial, WorkspaceAction.SetReferenceUnderlay(underlay)))
        val again = ReferenceUnderlay.placed(underlay.image, underlay.canvas)
        val same = unchanged(reduce(set, WorkspaceAction.SetReferenceUnderlay(again)))
        assertEquals(WorkspaceNoChangeReason.ReferenceUnderlayAlreadySet, same.reason)
        assertSame(set, same.nextState)
        val nothing = unchanged(reduce(initial, WorkspaceAction.ClearReferenceUnderlay))
        assertEquals(WorkspaceNoChangeReason.NoReferenceUnderlay, nothing.reason)
        assertSame(initial, nothing.nextState)
    }

    @Test
    fun `entering the adjust mode cancels the gesture preview, from no underlay or from a resting one`() {
        val adjusting = underlay.adjusting()
        assertEquals(UnderlayInteraction.Adjusting, adjusting.interaction)
        val fromNone = reduced(reduce(beginGesture(initial), WorkspaceAction.SetReferenceUnderlay(adjusting)))
        assertNull(fromNone.preview)
        assertEquals(adjusting, fromNone.underlay)
        val resting = beginGesture(reduced(reduce(initial, WorkspaceAction.SetReferenceUnderlay(underlay))))
        assertNotNull(resting.preview)
        val fromResting = reduced(reduce(resting, WorkspaceAction.SetReferenceUnderlay(adjusting)))
        assertNull(fromResting.preview)
        assertEquals(adjusting, fromResting.underlay)
    }

    @Test
    fun `staying in or leaving the adjust mode keeps the gesture preview`() {
        val adjusting = underlay.adjusting()
        val drawing = beginGesture(initial).withUnderlay(adjusting)
        val moved = adjusting.withPlacement(1.0, 1.0, 0.5)
        val stillAdjusting = reduced(reduce(drawing, WorkspaceAction.SetReferenceUnderlay(moved)))
        assertSame(drawing.preview, stillAdjusting.preview)
        val rested = reduced(reduce(drawing, WorkspaceAction.SetReferenceUnderlay(adjusting.rested())))
        assertSame(drawing.preview, rested.preview)
        val cleared = reduced(reduce(drawing, WorkspaceAction.ClearReferenceUnderlay))
        assertSame(drawing.preview, cleared.preview)
    }

    @Test
    fun `opacity and visibility changes keep the gesture preview`() {
        val drawing = beginGesture(reduced(reduce(initial, WorkspaceAction.SetReferenceUnderlay(underlay))))
        val opacity = underlay.withOpacity(UnderlayOpacity.MAX)
        val faded = reduced(reduce(drawing, WorkspaceAction.SetReferenceUnderlay(opacity)))
        assertSame(drawing.preview, faded.preview)
        assertEquals(opacity, faded.underlay)
        val hidden = underlay.toggledVisibility()
        val toggled = reduced(reduce(drawing, WorkspaceAction.SetReferenceUnderlay(hidden)))
        assertSame(drawing.preview, toggled.preview)
        assertEquals(hidden, toggled.underlay)
    }

    @Test
    fun `both underlay actions pass while a palette session is open`() {
        assertTrue(WorkspaceAction.SetReferenceUnderlay(underlay).isAllowedDuringPaletteSession())
        assertTrue(WorkspaceAction.ClearReferenceUnderlay.isAllowedDuringPaletteSession())
    }

    private fun reduce(
        state: WorkspaceState,
        action: WorkspaceAction,
    ): WorkspaceReductionResult = reducer.reduce(state, action, admission)

    private fun beginGesture(state: WorkspaceState): WorkspaceState =
        reduced(reduce(state, WorkspaceAction.BeginGesturePreview(canvas(4, 4), position(1, 1))))

    private fun image(
        width: Int,
        height: Int,
    ): ReferenceImage {
        val result = ReferenceImage.create(width, height, IntArray(width * height))
        check(result is ReferenceImageResult.Created) { "expected Created, got $result" }
        return result.image
    }
}
