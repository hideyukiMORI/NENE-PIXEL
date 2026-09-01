package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandGateway
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.green
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.red
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.state
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionAssertions.prepared
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionAssertions.reduced
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionAssertions.rejected
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionAssertions.unchanged
import io.github.hideyukimori.nenepixel.core.domain.drawing.Stroke
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class WorkspaceReducerTest {
    private val reducer = WorkspaceReducer.create()

    @Test
    fun `initial workspace contains only active color fixed viewport and no preview`() {
        val first = WorkspaceState.create(red)
        val equal = WorkspaceState.create(red)

        assertEquals(red, first.activeColor)
        assertEquals(FixedSliceViewport, first.viewport)
        assertNull(first.preview)
        assertEquals(equal, first)
        assertEquals(equal.hashCode(), first.hashCode())
    }

    @Test
    fun `active color changes without changing an active preview color`() {
        val canvas = canvas(2, 1)
        val previewing = begin(WorkspaceState.create(red), canvas, position(0, 0))

        val changed =
            reduced(
                reducer.reduce(previewing, WorkspaceAction.ChangeActiveColor(green)),
            )
        val repeated = unchanged(reducer.reduce(changed, WorkspaceAction.ChangeActiveColor(green)))

        assertEquals(green, changed.activeColor)
        assertEquals(red, changed.preview?.color)
        assertEquals(previewing.preview, changed.preview)
        assertEquals(WorkspaceNoChangeReason.ActiveColorAlreadySelected, repeated.reason)
        assertSame(changed, repeated.nextState)
    }

    @Test
    fun `begin validates phase before bounds and creates one immutable sample`() {
        val canvas = canvas(2, 1)
        val initial = WorkspaceState.create(red)
        val previewing = begin(initial, canvas, position(0, 0))

        assertEquals(canvas, previewing.preview?.canvas)
        assertEquals(red, previewing.preview?.color)
        assertEquals(listOf(position(0, 0)), previewing.preview?.positions())

        val alreadyActive =
            rejected(
                reducer.reduce(
                    previewing,
                    WorkspaceAction.BeginGesturePreview(canvas, position(2, 0)),
                ),
            )
        assertEquals(WorkspaceActionRejection.PreviewAlreadyActive, alreadyActive.rejection)
        assertSame(previewing, alreadyActive.nextState)

        val outside = rejected(reducer.reduce(initial, WorkspaceAction.BeginGesturePreview(canvas, position(2, 0))))
        assertOutside(outside, initial, canvas, position(2, 0))
    }

    @Test
    fun `extend preserves order makes consecutive duplicates unchanged and accepts revisits`() {
        val canvas = canvas(2, 1)
        val initial = WorkspaceState.create(red)
        val withoutPreview =
            rejected(reducer.reduce(initial, WorkspaceAction.ExtendGesturePreview(position(2, 0))))
        assertEquals(WorkspaceActionRejection.NoActivePreview, withoutPreview.rejection)
        assertSame(initial, withoutPreview.nextState)

        val oneSample = begin(initial, canvas, position(0, 0))
        val twoSamples = extend(oneSample, position(1, 0))
        val duplicate = unchanged(reducer.reduce(twoSamples, WorkspaceAction.ExtendGesturePreview(position(1, 0))))
        val revisited = extend(duplicate.nextState, position(0, 0))

        assertEquals(WorkspaceNoChangeReason.DuplicatePreviewSample, duplicate.reason)
        assertSame(twoSamples, duplicate.nextState)
        assertEquals(listOf(position(0, 0)), oneSample.preview?.positions())
        assertEquals(listOf(position(0, 0), position(1, 0), position(0, 0)), revisited.preview?.positions())

        val outside = rejected(reducer.reduce(revisited, WorkspaceAction.ExtendGesturePreview(position(2, 0))))
        assertOutside(outside, revisited, canvas, position(2, 0))
    }

    @Test
    fun `cancel clears only preview and never involves document state`() {
        val document = state(canvas(2, 1))
        val gateway = CommandGateway.create(document)
        val previewing = begin(WorkspaceState.create(red), document.size, position(0, 0))

        val cancelled = reduced(reducer.reduce(previewing, WorkspaceAction.CancelGesturePreview))

        assertEquals(red, cancelled.activeColor)
        assertEquals(FixedSliceViewport, cancelled.viewport)
        assertNull(cancelled.preview)
        assertEquals(document, gateway.documentState)

        val repeated = rejected(reducer.reduce(cancelled, WorkspaceAction.CancelGesturePreview))
        assertEquals(WorkspaceActionRejection.NoActivePreview, repeated.rejection)
        assertSame(cancelled, repeated.nextState)
    }

    @Test
    fun `commit preparation returns one stroke clears preview and executes no command`() {
        val document = state(canvas(2, 1))
        val gateway = CommandGateway.create(document)
        val previewing = extend(begin(WorkspaceState.create(red), document.size, position(0, 0)), position(1, 0))
        val recolored = reduced(reducer.reduce(previewing, WorkspaceAction.ChangeActiveColor(green)))

        val prepared = prepared(reducer.reduce(recolored, WorkspaceAction.PrepareGestureCommit))

        assertEquals(document.size, prepared.stroke.canvas)
        assertEquals(red, prepared.stroke.color)
        assertEquals(listOf(position(0, 0), position(1, 0)), prepared.stroke.positions())
        assertEquals(green, prepared.nextState.activeColor)
        assertNull(prepared.nextState.preview)
        assertEquals(document, gateway.documentState)

        val outOfOrder = rejected(reducer.reduce(prepared.nextState, WorkspaceAction.PrepareGestureCommit))
        assertEquals(WorkspaceActionRejection.NoActivePreview, outOfOrder.rejection)
        assertSame(prepared.nextState, outOfOrder.nextState)
    }

    @Test
    fun `identical action replay produces identical outcomes and final state`() {
        val canvas = canvas(2, 1)
        val actions =
            listOf(
                WorkspaceAction.ChangeActiveColor(green),
                WorkspaceAction.BeginGesturePreview(canvas, position(0, 0)),
                WorkspaceAction.ExtendGesturePreview(position(1, 0)),
                WorkspaceAction.ExtendGesturePreview(position(1, 0)),
                WorkspaceAction.ChangeActiveColor(red),
                WorkspaceAction.ExtendGesturePreview(position(0, 0)),
                WorkspaceAction.PrepareGestureCommit,
                WorkspaceAction.CancelGesturePreview,
            )

        val first = replay(WorkspaceState.create(red), actions)
        val second = replay(WorkspaceState.create(red), actions)

        assertEquals(first, second)
        assertEquals(first.last().nextState, second.last().nextState)
    }

    private fun begin(
        state: WorkspaceState,
        canvas: io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize,
        position: PixelPosition,
    ): WorkspaceState = reduced(reducer.reduce(state, WorkspaceAction.BeginGesturePreview(canvas, position)))

    private fun extend(
        state: WorkspaceState,
        position: PixelPosition,
    ): WorkspaceState = reduced(reducer.reduce(state, WorkspaceAction.ExtendGesturePreview(position)))

    private fun replay(
        initial: WorkspaceState,
        actions: List<WorkspaceAction>,
    ): List<WorkspaceReductionResult> =
        actions
            .fold(Replay(initial, emptyList())) { replay, action ->
                val result = reducer.reduce(replay.state, action)
                Replay(result.nextState, replay.results + result)
            }.results

    private fun assertOutside(
        result: WorkspaceReductionResult.Rejected,
        expectedState: WorkspaceState,
        expectedCanvas: io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize,
        expectedPosition: PixelPosition,
    ) {
        val rejection = result.rejection as WorkspaceActionRejection.PreviewPositionOutsideCanvas
        assertEquals(expectedCanvas, rejection.canvas)
        assertEquals(expectedPosition, rejection.position)
        assertSame(expectedState, result.nextState)
    }

    private fun ToolGesture.positions(): List<PixelPosition> = buildList { forEachPosition(::add) }

    private fun Stroke.positions(): List<PixelPosition> = buildList { forEachPosition(::add) }

    private data class Replay(
        val state: WorkspaceState,
        val results: List<WorkspaceReductionResult>,
    )
}
