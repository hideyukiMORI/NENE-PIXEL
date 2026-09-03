package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandGateway
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.green
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.palette
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.paletteIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.red
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.state
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionAssertions.prepared
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionAssertions.reduced
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionAssertions.rejected
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionAssertions.unchanged
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportState
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportValueResult
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportZoom
import io.github.hideyukimori.nenepixel.core.domain.drawing.DrawingTool
import io.github.hideyukimori.nenepixel.core.domain.drawing.Stroke
import io.github.hideyukimori.nenepixel.core.domain.drawing.StrokeEffect
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelLimits
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class WorkspaceReducerTest {
    private val palette = palette(red, green)
    private val reducer = WorkspaceReducer.create(palette)

    @Test
    fun `initial workspace contains first palette selection fit viewport and no preview`() {
        val canvas = canvas(16, 8)
        val first = WorkspaceState.create(canvas)
        val equal = WorkspaceState.create(canvas)

        assertEquals(paletteIndex(0), first.activePaletteIndex)
        assertEquals(DrawingTool.Pencil, first.activeTool)
        assertEquals(ViewportState.initial(canvas), first.viewport)
        assertNull(first.preview)
        assertEquals(equal, first)
        assertEquals(equal.hashCode(), first.hashCode())
    }

    @Test
    fun `active palette selection changes without changing an active preview color`() {
        val canvas = canvas(2, 1)
        val previewing = begin(WorkspaceState.create(canvas), canvas, position(0, 0))

        val changed =
            reduced(
                reducer.reduce(previewing, WorkspaceAction.SelectPaletteEntry(paletteIndex(1))),
            )
        val repeated = unchanged(reducer.reduce(changed, WorkspaceAction.SelectPaletteEntry(paletteIndex(1))))

        assertEquals(paletteIndex(1), changed.activePaletteIndex)
        assertEquals(StrokeEffect.Paint(red), changed.preview?.effect)
        assertEquals(previewing.preview, changed.preview)
        assertEquals(WorkspaceNoChangeReason.ActivePaletteEntryAlreadySelected, repeated.reason)
        assertSame(changed, repeated.nextState)
    }

    @Test
    fun `palette selection rejects an index outside configuration without changing state`() {
        val canvas = canvas(2, 1)
        val initial = WorkspaceState.create(canvas)

        val result = rejected(reducer.reduce(initial, WorkspaceAction.SelectPaletteEntry(paletteIndex(2))))
        val rejection =
            assertInstanceOf(
                WorkspaceActionRejection.PaletteIndexOutsidePalette::class.java,
                result.rejection,
            )

        assertEquals(paletteIndex(2), rejection.attemptedIndex)
        assertEquals(2, rejection.entryCount)
        assertSame(initial, result.nextState)
    }

    @Test
    fun `active tool changes only through reducer and an active gesture keeps its captured effect`() {
        val canvas = canvas(2, 1)
        val initial = WorkspaceState.create(canvas)
        val eraserSelected = reduced(reducer.reduce(initial, WorkspaceAction.SelectTool(DrawingTool.Eraser)))
        val repeated = unchanged(reducer.reduce(eraserSelected, WorkspaceAction.SelectTool(DrawingTool.Eraser)))
        val previewing = begin(eraserSelected, canvas, position(0, 0))
        val pencilSelected = reduced(reducer.reduce(previewing, WorkspaceAction.SelectTool(DrawingTool.Pencil)))

        assertEquals(DrawingTool.Eraser, eraserSelected.activeTool)
        assertEquals(WorkspaceNoChangeReason.ActiveToolAlreadySelected, repeated.reason)
        assertSame(eraserSelected, repeated.nextState)
        assertEquals(DrawingTool.Pencil, pencilSelected.activeTool)
        assertEquals(StrokeEffect.Erase, pencilSelected.preview?.effect)
        assertEquals(previewing.preview, pencilSelected.preview)
    }

    @Test
    fun `begin validates phase before bounds and creates one immutable sample`() {
        val canvas = canvas(2, 1)
        val initial = WorkspaceState.create(canvas)
        val previewing = begin(initial, canvas, position(0, 0))

        assertEquals(canvas, previewing.preview?.canvas)
        assertEquals(StrokeEffect.Paint(red), previewing.preview?.effect)
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
    fun `sample gaps use the canonical bounded Bresenham path in both preview and commit`() {
        val canvas = canvas(6, 3)
        val initial = WorkspaceState.create(canvas)
        val previewing = extend(begin(initial, canvas, position(0, 0)), position(5, 2))
        val expected =
            listOf(
                position(0, 0),
                position(1, 0),
                position(2, 1),
                position(3, 1),
                position(4, 2),
                position(5, 2),
            )

        assertEquals(expected, previewing.preview?.positions())
        assertEquals(expected.size, previewing.preview?.positionCount)

        val prepared = prepared(reducer.reduce(previewing, WorkspaceAction.PrepareGestureCommit))
        assertEquals(expected, prepared.stroke.positions())
    }

    @Test
    fun `extend preserves order makes consecutive duplicates unchanged and accepts revisits`() {
        val canvas = canvas(2, 1)
        val initial = WorkspaceState.create(canvas)
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
        val previewing = begin(WorkspaceState.create(document.size), document.size, position(0, 0))

        val cancelled = reduced(reducer.reduce(previewing, WorkspaceAction.CancelGesturePreview))

        assertEquals(paletteIndex(0), cancelled.activePaletteIndex)
        assertEquals(ViewportState.initial(document.size), cancelled.viewport)
        assertNull(cancelled.preview)
        assertEquals(document, gateway.runtimeState.documentState)

        val repeated = rejected(reducer.reduce(cancelled, WorkspaceAction.CancelGesturePreview))
        assertEquals(WorkspaceActionRejection.NoActivePreview, repeated.rejection)
        assertSame(cancelled, repeated.nextState)
    }

    @Test
    fun `commit preparation returns one stroke clears preview and executes no command`() {
        val document = state(canvas(2, 1))
        val gateway = CommandGateway.create(document)
        val previewing =
            extend(
                begin(WorkspaceState.create(document.size), document.size, position(0, 0)),
                position(1, 0),
            )
        val recolored = reduced(reducer.reduce(previewing, WorkspaceAction.SelectPaletteEntry(paletteIndex(1))))

        val prepared = prepared(reducer.reduce(recolored, WorkspaceAction.PrepareGestureCommit))

        assertEquals(document.size, prepared.stroke.canvas)
        assertEquals(StrokeEffect.Paint(red), prepared.stroke.effect)
        assertEquals(listOf(position(0, 0), position(1, 0)), prepared.stroke.positions())
        assertEquals(paletteIndex(1), prepared.nextState.activePaletteIndex)
        assertNull(prepared.nextState.preview)
        assertEquals(document, gateway.runtimeState.documentState)

        val outOfOrder = rejected(reducer.reduce(prepared.nextState, WorkspaceAction.PrepareGestureCommit))
        assertEquals(WorkspaceActionRejection.NoActivePreview, outOfOrder.rejection)
        assertSame(prepared.nextState, outOfOrder.nextState)
    }

    @Test
    fun `expanded path accepts cap minus one and cap then rejects cap plus one atomically`() {
        val canvas = canvas(256, 1)
        var state = begin(WorkspaceState.create(canvas), canvas, position(0, 0))
        repeat(FULL_WIDTH_SEGMENT_COUNT) { segment ->
            val x = if (segment % 2 == 0) 255 else 0
            state = extend(state, position(x, 0))
        }

        val capMinusOne = extend(state, position(2, 0))
        val cap = extend(capMinusOne, position(3, 0))
        val rejected = rejected(reducer.reduce(cap, WorkspaceAction.ExtendGesturePreview(position(4, 0))))
        val rejection =
            assertInstanceOf(
                WorkspaceActionRejection.PreviewPathAboveSupportedMaximum::class.java,
                rejected.rejection,
            )

        assertEquals(PixelLimits.MAX_RAW_STROKE_POSITIONS - 1, capMinusOne.preview?.positionCount)
        assertEquals(PixelLimits.MAX_RAW_STROKE_POSITIONS, cap.preview?.positionCount)
        assertEquals((PixelLimits.MAX_RAW_STROKE_POSITIONS + 1).toLong(), rejection.attemptedCount)
        assertEquals(PixelLimits.MAX_RAW_STROKE_POSITIONS, rejection.maximum)
        assertSame(cap, rejected.nextState)
    }

    @Test
    fun `identical action replay produces identical outcomes and final state`() {
        val canvas = canvas(2, 1)
        val actions =
            listOf(
                WorkspaceAction.SelectPaletteEntry(paletteIndex(1)),
                WorkspaceAction.BeginGesturePreview(canvas, position(0, 0)),
                WorkspaceAction.ExtendGesturePreview(position(1, 0)),
                WorkspaceAction.ExtendGesturePreview(position(1, 0)),
                WorkspaceAction.SelectPaletteEntry(paletteIndex(0)),
                WorkspaceAction.ExtendGesturePreview(position(0, 0)),
                WorkspaceAction.PrepareGestureCommit,
                WorkspaceAction.CancelGesturePreview,
            )

        val first = replay(WorkspaceState.create(canvas), actions)
        val second = replay(WorkspaceState.create(canvas), actions)

        assertEquals(first, second)
        assertEquals(first.last().nextState, second.last().nextState)
    }

    @Test
    fun `set viewport changes only workspace viewport and atomically cancels preview`() {
        val canvas = canvas(2, 1)
        val document = state(canvas)
        val gateway = CommandGateway.create(document)
        val initial = WorkspaceState.create(canvas)
        val previewing = begin(initial, canvas, position(0, 0))
        val changedViewport = ViewportState.create(zoom(2.0), initial.viewport.center)

        val changed = reduced(reducer.reduce(previewing, WorkspaceAction.SetViewport(changedViewport)))

        assertEquals(changedViewport, changed.viewport)
        assertEquals(paletteIndex(0), changed.activePaletteIndex)
        assertNull(changed.preview)
        assertEquals(document, gateway.runtimeState.documentState)
    }

    @Test
    fun `set same viewport cancels preview once then becomes unchanged`() {
        val canvas = canvas(2, 1)
        val initial = WorkspaceState.create(canvas)
        val previewing = begin(initial, canvas, position(0, 0))

        val cancelled = reduced(reducer.reduce(previewing, WorkspaceAction.SetViewport(initial.viewport)))
        val repeated = unchanged(reducer.reduce(cancelled, WorkspaceAction.SetViewport(initial.viewport)))

        assertEquals(initial.viewport, cancelled.viewport)
        assertNull(cancelled.preview)
        assertEquals(WorkspaceNoChangeReason.ViewportAlreadySet, repeated.reason)
        assertSame(cancelled, repeated.nextState)
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

    private fun zoom(value: Double): ViewportZoom =
        when (val result = ViewportZoom.create(value)) {
            is ViewportValueResult.Created -> result.value
            is ViewportValueResult.Rejected -> error("Test zoom was rejected: ${result.rejection}")
        }

    private data class Replay(
        val state: WorkspaceState,
        val results: List<WorkspaceReductionResult>,
    )

    private companion object {
        const val FULL_WIDTH_SEGMENT_COUNT: Int = 1_028
    }
}
