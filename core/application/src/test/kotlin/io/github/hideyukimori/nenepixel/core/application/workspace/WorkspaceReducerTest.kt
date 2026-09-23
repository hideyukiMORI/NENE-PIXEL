package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandGateway
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandSourceAdmission
import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryPosition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.defaultDocumentId
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.definition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.green
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.palette
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.paletteIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.red
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.state
import io.github.hideyukimori.nenepixel.core.application.editor.RuntimeSourceToken
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionAssertions.prepared
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionAssertions.reduced
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionAssertions.rejected
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionAssertions.unchanged
import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteEditSession
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportState
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportValueResult
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportZoom
import io.github.hideyukimori.nenepixel.core.domain.drawing.DrawingTool
import io.github.hideyukimori.nenepixel.core.domain.drawing.Stroke
import io.github.hideyukimori.nenepixel.core.domain.drawing.StrokeEffect
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelLimits
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class WorkspaceReducerTest {
    private val palette = palette(red, green)
    private val definition = definition(paletteIndex(0), red, green)
    private val reducer = WorkspaceReducer.create()
    private val admissions = mutableMapOf<CanvasSize, CommandSourceAdmission>()
    private val paletteBase = RuntimeSourceToken(1L, defaultDocumentId, HistoryPosition.initial)

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
                reduce(canvas, previewing, WorkspaceAction.SelectPaletteEntry(paletteIndex(1))),
            )
        val repeated = unchanged(reduce(canvas, changed, WorkspaceAction.SelectPaletteEntry(paletteIndex(1))))

        assertEquals(paletteIndex(1), changed.activePaletteIndex)
        assertEquals(StrokeEffect.Paint(paletteIndex(0)), changed.preview?.effect)
        assertEquals(previewing.preview, changed.preview)
        assertEquals(WorkspaceNoChangeReason.ActivePaletteEntryAlreadySelected, repeated.reason)
        assertSame(changed, repeated.nextState)
    }

    @Test
    fun `palette selection rejects an index outside configuration without changing state`() {
        val canvas = canvas(2, 1)
        val initial = WorkspaceState.create(canvas)

        val result = rejected(reduce(canvas, initial, WorkspaceAction.SelectPaletteEntry(paletteIndex(2))))
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
        val eraserSelected = reduced(reduce(canvas, initial, WorkspaceAction.SelectTool(DrawingTool.Eraser)))
        val repeated = unchanged(reduce(canvas, eraserSelected, WorkspaceAction.SelectTool(DrawingTool.Eraser)))
        val previewing = begin(eraserSelected, canvas, position(0, 0))
        val pencilSelected = reduced(reduce(canvas, previewing, WorkspaceAction.SelectTool(DrawingTool.Pencil)))

        assertEquals(DrawingTool.Eraser, eraserSelected.activeTool)
        assertEquals(WorkspaceNoChangeReason.ActiveToolAlreadySelected, repeated.reason)
        assertSame(eraserSelected, repeated.nextState)
        assertEquals(DrawingTool.Pencil, pencilSelected.activeTool)
        assertEquals(StrokeEffect.Erase(paletteIndex(0)), pencilSelected.preview?.effect)
        assertEquals(previewing.preview, pencilSelected.preview)
    }

    @Test
    fun `begin validates phase before bounds and creates one immutable sample`() {
        val canvas = canvas(2, 1)
        val initial = WorkspaceState.create(canvas)
        val previewing = begin(initial, canvas, position(0, 0))

        assertEquals(canvas, previewing.preview?.canvas)
        assertEquals(StrokeEffect.Paint(paletteIndex(0)), previewing.preview?.effect)
        assertEquals(listOf(position(0, 0)), previewing.preview?.positions())

        val alreadyActive =
            rejected(
                reduce(
                    canvas,
                    previewing,
                    WorkspaceAction.BeginGesturePreview(canvas, position(2, 0)),
                ),
            )
        assertEquals(WorkspaceActionRejection.PreviewAlreadyActive, alreadyActive.rejection)
        assertSame(previewing, alreadyActive.nextState)

        val outside = rejected(reduce(canvas, initial, WorkspaceAction.BeginGesturePreview(canvas, position(2, 0))))
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

        val prepared = prepared(reduce(canvas, previewing, WorkspaceAction.PrepareGestureCommit))
        assertEquals(expected, prepared.stroke.positions())
    }

    @Test
    fun `extend preserves order makes consecutive duplicates unchanged and accepts revisits`() {
        val canvas = canvas(2, 1)
        val initial = WorkspaceState.create(canvas)
        val withoutPreview =
            rejected(reduce(canvas, initial, WorkspaceAction.ExtendGesturePreview(position(2, 0))))
        assertEquals(WorkspaceActionRejection.NoActivePreview, withoutPreview.rejection)
        assertSame(initial, withoutPreview.nextState)

        val oneSample = begin(initial, canvas, position(0, 0))
        val twoSamples = extend(oneSample, position(1, 0))
        val duplicate = unchanged(reduce(canvas, twoSamples, WorkspaceAction.ExtendGesturePreview(position(1, 0))))
        val revisited = extend(duplicate.nextState, position(0, 0))

        assertEquals(WorkspaceNoChangeReason.DuplicatePreviewSample, duplicate.reason)
        assertSame(twoSamples, duplicate.nextState)
        assertEquals(listOf(position(0, 0)), oneSample.preview?.positions())
        assertEquals(listOf(position(0, 0), position(1, 0), position(0, 0)), revisited.preview?.positions())

        val outside = rejected(reduce(canvas, revisited, WorkspaceAction.ExtendGesturePreview(position(2, 0))))
        assertOutside(outside, revisited, canvas, position(2, 0))
    }

    @Test
    fun `cancel clears only preview and never involves document state`() {
        val document = state(canvas(2, 1))
        val gateway = CommandGateway.create(document)
        val previewing = begin(WorkspaceState.create(document.size), document.size, position(0, 0))

        val cancelled = reduced(reduce(document.size, previewing, WorkspaceAction.CancelGesturePreview))

        assertEquals(paletteIndex(0), cancelled.activePaletteIndex)
        assertEquals(ViewportState.initial(document.size), cancelled.viewport)
        assertNull(cancelled.preview)
        assertEquals(document, gateway.runtimeState.documentState)

        val repeated = rejected(reduce(document.size, cancelled, WorkspaceAction.CancelGesturePreview))
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
        val recolored =
            reduced(reduce(document.size, previewing, WorkspaceAction.SelectPaletteEntry(paletteIndex(1))))

        val prepared = prepared(reduce(document.size, recolored, WorkspaceAction.PrepareGestureCommit))

        assertEquals(document.size, prepared.stroke.canvas)
        assertEquals(StrokeEffect.Paint(paletteIndex(0)), prepared.stroke.effect)
        assertEquals(listOf(position(0, 0), position(1, 0)), prepared.stroke.positions())
        assertEquals(paletteIndex(1), prepared.nextState.activePaletteIndex)
        assertNull(prepared.nextState.preview)
        assertEquals(document, gateway.runtimeState.documentState)

        val outOfOrder = rejected(reduce(document.size, prepared.nextState, WorkspaceAction.PrepareGestureCommit))
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
        val rejected = rejected(reduce(canvas, cap, WorkspaceAction.ExtendGesturePreview(position(4, 0))))
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

        val first = replay(canvas, WorkspaceState.create(canvas), actions)
        val second = replay(canvas, WorkspaceState.create(canvas), actions)

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

        val changed = reduced(reduce(canvas, previewing, WorkspaceAction.SetViewport(changedViewport)))

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

        val cancelled = reduced(reduce(canvas, previewing, WorkspaceAction.SetViewport(initial.viewport)))
        val repeated = unchanged(reduce(canvas, cancelled, WorkspaceAction.SetViewport(initial.viewport)))

        assertEquals(initial.viewport, cancelled.viewport)
        assertNull(cancelled.preview)
        assertEquals(WorkspaceNoChangeReason.ViewportAlreadySet, repeated.reason)
        assertSame(cancelled, repeated.nextState)
    }

    @Test
    fun `begin palette edit opens a session whose draft is the document definition`() {
        val canvas = canvas(2, 1)
        val initial = WorkspaceState.create(canvas)

        val opened = reduced(reduce(canvas, initial, BeginPaletteEdit(paletteBase, definition)))

        val session = checkNotNull(opened.paletteEditSession)
        assertEquals(paletteBase, session.base)
        assertSame(definition, session.draft)
        assertEquals(PaletteEditSession.begin(paletteBase, definition), session)
        assertEquals(initial.withPaletteEditSession(session), opened)
    }

    @Test
    fun `begin palette edit keeps preview and active palette selection`() {
        val canvas = canvas(2, 1)
        val selected =
            reduced(reduce(canvas, WorkspaceState.create(canvas), WorkspaceAction.SelectPaletteEntry(paletteIndex(1))))
        val previewing = begin(selected, canvas, position(0, 0))

        val opened = reduced(reduce(canvas, previewing, BeginPaletteEdit(paletteBase, definition)))

        assertEquals(previewing.preview, opened.preview)
        assertEquals(paletteIndex(1), opened.activePaletteIndex)
        assertEquals(previewing.viewport, opened.viewport)
        assertEquals(previewing.appearance, opened.appearance)
        assertEquals(previewing.actualSizeWindow, opened.actualSizeWindow)
    }

    @Test
    fun `second begin palette edit is rejected without replacing the session`() {
        val canvas = canvas(2, 1)
        val opened = open(WorkspaceState.create(canvas), canvas)
        val otherBase = RuntimeSourceToken(2L, defaultDocumentId, HistoryPosition.create(1L))

        val result = rejected(reduce(canvas, opened, BeginPaletteEdit(otherBase, definition)))

        assertEquals(WorkspaceActionRejection.PaletteSessionAlreadyActive, result.rejection)
        assertSame(opened, result.nextState)
    }

    @Test
    fun `cancel palette edit clears only the session`() {
        val canvas = canvas(2, 1)
        val previewing = begin(WorkspaceState.create(canvas), canvas, position(0, 0))
        val opened = reduced(reduce(canvas, previewing, BeginPaletteEdit(paletteBase, definition)))

        val cancelled = reduced(reduce(canvas, opened, WorkspaceAction.CancelPaletteEdit))

        assertNull(cancelled.paletteEditSession)
        assertEquals(previewing, cancelled)
    }

    @Test
    fun `cancel palette edit without a session is rejected`() {
        val canvas = canvas(2, 1)
        val initial = WorkspaceState.create(canvas)

        val result = rejected(reduce(canvas, initial, WorkspaceAction.CancelPaletteEdit))

        assertEquals(WorkspaceActionRejection.NoPaletteSession, result.rejection)
        assertSame(initial, result.nextState)
    }

    private fun open(
        state: WorkspaceState,
        canvas: CanvasSize,
    ): WorkspaceState = reduced(reduce(canvas, state, BeginPaletteEdit(paletteBase, definition)))

    private fun begin(
        state: WorkspaceState,
        canvas: io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize,
        position: PixelPosition,
    ): WorkspaceState = reduced(reduce(canvas, state, WorkspaceAction.BeginGesturePreview(canvas, position)))

    private fun extend(
        state: WorkspaceState,
        position: PixelPosition,
    ): WorkspaceState =
        reduced(reduce(checkNotNull(state.preview).canvas, state, WorkspaceAction.ExtendGesturePreview(position)))

    private fun replay(
        canvas: io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize,
        initial: WorkspaceState,
        actions: List<WorkspaceAction>,
    ): List<WorkspaceReductionResult> =
        actions
            .fold(Replay(initial, emptyList())) { replay, action ->
                val result = reduce(canvas, replay.state, action)
                Replay(result.nextState, replay.results + result)
            }.results

    private fun reduce(
        canvas: io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize,
        state: WorkspaceState,
        action: WorkspaceAction,
    ): WorkspaceReductionResult = reducer.reduce(state, action, admission(canvas))

    private fun admission(canvas: io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize) =
        admissions.getOrPut(canvas) {
            CommandGateway.create(state(canvas, definition = definition)).captureSource()
        }

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
