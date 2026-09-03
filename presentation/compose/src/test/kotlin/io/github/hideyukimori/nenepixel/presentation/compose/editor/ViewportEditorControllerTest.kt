package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.ApplyStrokeCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.command.RejectionReason
import io.github.hideyukimori.nenepixel.core.application.editor.DocumentDirtyState
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceState
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportGesture
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurface
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurfacePoint
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportValueResult
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.drawing.DrawingTool
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.presentation.compose.EditorFixture
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.colorAt
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.fixture
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.green
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.position
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.red
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

internal class ViewportEditorControllerTest {
    private val surface: ViewportSurface = viewportSurface()

    @Test
    fun `validated surface points commit one command then undo and redo exact snapshots`() {
        val fixture = fixture()
        val initial = fixture.controller.renderState

        val down = fixture.controller.pointerDown(surface, surfacePoint(0, 0))
        val move = fixture.controller.pointerMove(surface, surfacePoint(1, 0))
        val end = fixture.controller.pointerEnd(surface, surfacePoint(2, 0))

        assertInstanceOf(PointerInputAcknowledgement.Accepted::class.java, down)
        assertInstanceOf(PointerInputAcknowledgement.Accepted::class.java, move)
        val accepted = assertInstanceOf(PointerInputAcknowledgement.Accepted::class.java, end)
        assertInstanceOf(CommandResult.Applied::class.java, accepted.commandResult)
        assertEquals(1L, accepted.renderState.snapshot.revision.value)
        assertTrue(accepted.renderState.canUndo)
        assertFalse(accepted.renderState.canRedo)
        assertEquals(red, colorAt(fixture.controller.documentState, position(0, 0)))
        assertEquals(red, colorAt(fixture.controller.documentState, position(1, 0)))
        assertEquals(red, colorAt(fixture.controller.documentState, position(2, 0)))

        val undone = fixture.controller.callbacks.onUndo()
        assertEquals(initial.snapshot, undone.snapshot)
        assertFalse(undone.canUndo)
        assertTrue(undone.canRedo)

        val redone = fixture.controller.callbacks.onRedo()
        assertEquals(accepted.renderState.snapshot, redone.snapshot)
        assertTrue(redone.canUndo)
        assertFalse(redone.canRedo)
    }

    @Test
    fun `sample gap commits one interpolated pencil command and one history entry`() {
        val fixture = fixture()
        fixture.controller.pointerDown(surface, surfacePoint(0, 0))

        val end = fixture.controller.pointerEnd(surface, surfacePoint(3, 0))
        val accepted = assertInstanceOf(PointerInputAcknowledgement.Accepted::class.java, end)
        assertInstanceOf(CommandResult.Applied::class.java, accepted.commandResult)

        repeat(4) { x -> assertEquals(red, colorAt(fixture.controller.documentState, position(x, 0))) }
        assertEquals(1L, fixture.controller.documentState.revision.value)
        assertTrue(accepted.renderState.canUndo)
        assertFalse(accepted.renderState.canRedo)
    }

    @Test
    fun `gesture captures pencil before tool change and the next eraser uses the same command path`() {
        val fixture = fixture()
        val beforeSelection = fixture.controller.documentState
        fixture.controller.pointerDown(surface, surfacePoint(0, 0))

        val selected = fixture.controller.callbacks.onSelectTool(DrawingTool.Eraser)
        assertSame(beforeSelection, fixture.controller.documentState)
        val pencilEnd = fixture.controller.pointerEnd(surface, surfacePoint(0, 0))

        assertEquals(DrawingTool.Eraser, selected.activeTool)
        assertInstanceOf(CommandResult.Applied::class.java, accepted(pencilEnd).commandResult)
        assertEquals(red, colorAt(fixture.controller.documentState, position(0, 0)))

        fixture.controller.pointerDown(surface, surfacePoint(0, 0))
        val erased = fixture.controller.pointerEnd(surface, surfacePoint(0, 0))

        assertInstanceOf(CommandResult.Applied::class.java, accepted(erased).commandResult)
        assertEquals(PixelColor.blank, colorAt(fixture.controller.documentState, position(0, 0)))
        assertEquals(2L, fixture.controller.documentState.revision.value)
        assertTrue(erased.renderState.canUndo)
    }

    @Test
    fun `palette selection is workspace only and a running gesture keeps its exact rgba`() {
        val fixture = fixture()
        val beforeSelection = fixture.runtime.state
        fixture.controller.pointerDown(surface, surfacePoint(0, 0))

        val selected = fixture.controller.callbacks.onSelectPaletteEntry(paletteIndex(1))

        assertSame(beforeSelection.documentState, fixture.controller.documentState)
        assertEquals(beforeSelection.historyAvailability, fixture.runtime.state.historyAvailability)
        assertEquals(beforeSelection.dirtyState, fixture.runtime.state.dirtyState)
        assertEquals(paletteIndex(1), selected.activePaletteIndex)
        assertEquals(green, selected.activeColor)

        val first = accepted(fixture.controller.pointerEnd(surface, surfacePoint(0, 0)))
        assertInstanceOf(CommandResult.Applied::class.java, first.commandResult)
        assertEquals(red, colorAt(fixture.controller.documentState, position(0, 0)))

        fixture.controller.pointerDown(surface, surfacePoint(1, 0))
        val second = accepted(fixture.controller.pointerEnd(surface, surfacePoint(1, 0)))
        assertInstanceOf(CommandResult.Applied::class.java, second.commandResult)
        assertEquals(green, colorAt(fixture.controller.documentState, position(1, 0)))
        assertEquals(
            green.toPackedRgba8888(),
            colorAt(fixture.controller.documentState, position(1, 0)).toPackedRgba8888(),
        )
    }

    @Test
    fun `already blank eraser is one typed no-op without history or dirty state`() {
        val fixture = fixture()
        fixture.controller.callbacks.onSelectTool(DrawingTool.Eraser)
        fixture.controller.pointerDown(surface, surfacePoint(0, 0))

        val end = accepted(fixture.controller.pointerEnd(surface, surfacePoint(0, 0)))
        val rejected = assertInstanceOf(CommandResult.Rejected::class.java, end.commandResult)

        assertEquals(RejectionReason.NoEffectiveChange, rejected.reason)
        assertSame(fixture.initialDocument, fixture.controller.documentState)
        assertFalse(end.renderState.canUndo)
        assertFalse(end.renderState.canRedo)
        assertEquals(DocumentDirtyState.Clean, fixture.runtime.state.dirtyState)
    }

    @Test
    fun `second pointer start atomically cancels preview before viewport transform`() {
        val fixture = fixture()
        val initialViewport = fixture.controller.workspaceState.viewport
        fixture.controller.pointerDown(surface, surfacePoint(0, 0))
        fixture.controller.pointerMove(surface, surfacePoint(1, 0))

        val started = fixture.controller.viewportStarted(surface)

        assertInstanceOf(PointerInputAcknowledgement.Cancelled::class.java, started)
        assertNull(fixture.controller.workspaceState.preview)
        assertSame(fixture.initialDocument, fixture.controller.documentState)

        val transformed = fixture.controller.viewportTransformed(surface, zoomGesture())

        assertInstanceOf(PointerInputAcknowledgement.Accepted::class.java, transformed)
        assertNotEquals(initialViewport, fixture.controller.workspaceState.viewport)
        assertNull(fixture.controller.workspaceState.preview)
        assertSame(fixture.initialDocument, fixture.controller.documentState)
        assertFalse(transformed.renderState.canUndo)
        assertFalse(transformed.renderState.canRedo)
    }

    @Test
    fun `outside end and explicit cancel clear preview without document command`() {
        val outside = fixture()
        outside.controller.pointerDown(surface, surfacePoint(0, 0))
        val outsideResult = outside.controller.pointerEnd(surface, surfacePoint(SURFACE_EDGE, CELL_EDGE / 2.0))

        assertInstanceOf(PointerInputAcknowledgement.Cancelled::class.java, outsideResult)
        assertNull(outside.controller.workspaceState.preview)
        assertSame(outside.initialDocument, outside.controller.documentState)

        val cancelled = fixture()
        cancelled.controller.pointerDown(surface, surfacePoint(0, 0))
        val cancelResult = cancelled.controller.pointerCancel()

        assertInstanceOf(PointerInputAcknowledgement.Cancelled::class.java, cancelResult)
        assertNull(cancelled.controller.workspaceState.preview)
        assertSame(cancelled.initialDocument, cancelled.controller.documentState)
        assertInstanceOf(PointerInputAcknowledgement.Ignored::class.java, cancelled.controller.pointerCancel())
    }

    @Test
    fun `canonical viewport mapping and direct core command produce identical states`() {
        val direct = fixture()
        val mapped = fixture()
        val samples = listOf(position(0, 0), position(1, 1), position(2, 1))

        val directOutcome = executeDirect(direct, samples)
        mapped.controller.pointerDown(surface, surfacePoint(0, 0))
        mapped.controller.pointerMove(surface, surfacePoint(1, 1))
        val result = mapped.controller.pointerEnd(surface, surfacePoint(2, 1))
        val accepted = assertInstanceOf(PointerInputAcknowledgement.Accepted::class.java, result)

        assertEquals(directOutcome.commandResult, accepted.commandResult)
        assertEquals(direct.runtime.state.documentState, mapped.runtime.state.documentState)
        assertEquals(directOutcome.workspaceState, mapped.controller.workspaceState)
    }

    @Test
    fun `direct reducer and controller eraser paths produce identical results`() {
        val direct = fixture()
        val mapped = fixture()
        seedFirstRow(direct)
        seedFirstRow(mapped)
        val samples = listOf(position(0, 0), position(3, 0))

        val directOutcome = executeDirect(direct, samples, DrawingTool.Eraser)
        mapped.controller.callbacks.onSelectTool(DrawingTool.Eraser)
        mapped.controller.pointerDown(surface, surfacePoint(0, 0))
        val mappedOutcome = accepted(mapped.controller.pointerEnd(surface, surfacePoint(3, 0)))

        assertEquals(directOutcome.commandResult, mappedOutcome.commandResult)
        assertEquals(direct.runtime.state.documentState, mapped.runtime.state.documentState)
        assertEquals(directOutcome.workspaceState, mapped.controller.workspaceState)
        repeat(4) { x ->
            assertEquals(PixelColor.blank, colorAt(mapped.controller.documentState, position(x, 0)))
        }
    }

    private fun executeDirect(
        fixture: EditorFixture,
        samples: List<PixelPosition>,
        tool: DrawingTool = DrawingTool.Pencil,
    ): DirectOutcome {
        var workspace = fixture.initialWorkspace
        if (tool != workspace.activeTool) {
            workspace = fixture.reducer.reduce(workspace, WorkspaceAction.SelectTool(tool)).nextState
        }
        workspace =
            fixture.reducer
                .reduce(
                    workspace,
                    WorkspaceAction.BeginGesturePreview(fixture.initialDocument.size, samples.first()),
                ).nextState
        samples.drop(1).forEach { sample ->
            workspace = fixture.reducer.reduce(workspace, WorkspaceAction.ExtendGesturePreview(sample)).nextState
        }
        val prepared = fixture.reducer.reduce(workspace, WorkspaceAction.PrepareGestureCommit)
        val commit = assertInstanceOf(WorkspaceReductionResult.CommitPrepared::class.java, prepared)
        val target = fixture.runtime.state.documentState
        return DirectOutcome(
            commandResult =
                fixture.runtime.execute(
                    ApplyStrokeCommand.create(target.id, target.revision, commit.stroke),
                ),
            workspaceState = commit.nextState,
        )
    }

    private fun seedFirstRow(fixture: EditorFixture) {
        val target = fixture.runtime.state.documentState
        val directOutcome = executeDirect(fixture, listOf(position(0, 0), position(3, 0)))
        assertInstanceOf(CommandResult.Applied::class.java, directOutcome.commandResult)
        assertEquals(target.revision.value + 1L, fixture.runtime.state.documentState.revision.value)
    }

    private fun zoomGesture(): ViewportGesture =
        ViewportGesture.create(
            previousFirst = surfacePoint(100.0, 100.0),
            previousSecond = surfacePoint(300.0, 300.0),
            currentFirst = surfacePoint(80.0, 80.0),
            currentSecond = surfacePoint(320.0, 320.0),
        )

    private fun accepted(result: PointerInputAcknowledgement): PointerInputAcknowledgement.Accepted =
        assertInstanceOf(PointerInputAcknowledgement.Accepted::class.java, result)

    private fun paletteIndex(value: Int): PaletteIndex =
        when (val result = PaletteIndex.create(value)) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> fail("Palette index fixture was rejected: ${result.rejection}")
        }

    private fun viewportSurface(): ViewportSurface =
        ViewportSurface.create(SURFACE_EDGE.toInt(), SURFACE_EDGE.toInt(), PIXELS_PER_DP).requiredValue()

    private fun surfacePoint(
        x: Int,
        y: Int,
    ): ViewportSurfacePoint = surfacePoint((x + HALF_CELL) * CELL_EDGE, (y + HALF_CELL) * CELL_EDGE)

    private fun surfacePoint(
        xPixels: Double,
        yPixels: Double,
    ): ViewportSurfacePoint = ViewportSurfacePoint.create(xPixels, yPixels).requiredValue()

    private fun <T> ViewportValueResult<T>.requiredValue(): T =
        when (this) {
            is ViewportValueResult.Created -> value
            is ViewportValueResult.Rejected -> fail("Viewport test value was rejected: $rejection")
        }

    private data class DirectOutcome(
        val commandResult: CommandResult,
        val workspaceState: WorkspaceState,
    )

    private companion object {
        const val CELL_EDGE: Double = 100.0
        const val HALF_CELL: Double = 0.5
        const val SURFACE_EDGE: Double = 400.0
        const val PIXELS_PER_DP: Double = 2.0
    }
}
