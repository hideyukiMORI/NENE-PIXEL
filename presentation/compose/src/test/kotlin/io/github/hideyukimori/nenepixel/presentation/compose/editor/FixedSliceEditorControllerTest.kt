package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import io.github.hideyukimori.nenepixel.core.application.document.command.ApplyStrokeCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.workspace.ToolGesture
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceState
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.presentation.compose.EditorFixture
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.colorAt
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.fixture
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.position
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.red
import io.github.hideyukimori.nenepixel.presentation.compose.input.FixedCanvasTouchTranslator
import io.github.hideyukimori.nenepixel.presentation.compose.input.TouchTranslation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

internal class FixedSliceEditorControllerTest {
    @Test
    fun `history callbacks project exact draw undo redo states`() {
        val fixture = fixture()
        val initial = fixture.controller.renderState
        assertFalse(initial.canUndo)
        assertFalse(initial.canRedo)

        fixture.controller.pointerDown(position(0, 0))
        val drawn = fixture.controller.pointerEnd(position(1, 0)).renderState
        assertTrue(drawn.canUndo)
        assertFalse(drawn.canRedo)
        assertEquals(1L, drawn.snapshot.revision.value)

        val undone = fixture.controller.callbacks.onUndo()
        assertEquals(initial.snapshot, undone.snapshot)
        assertFalse(undone.canUndo)
        assertTrue(undone.canRedo)
        assertEquals(0L, undone.snapshot.revision.value)

        val redone = fixture.controller.callbacks.onRedo()
        assertEquals(drawn.snapshot, redone.snapshot)
        assertTrue(redone.canUndo)
        assertFalse(redone.canRedo)
        assertEquals(1L, redone.snapshot.revision.value)
    }

    @Test
    fun `down and move only project preview then end executes exactly one command`() {
        val fixture = fixture()
        val initialRenderState = fixture.controller.renderState

        val down = fixture.controller.pointerDown(position(0, 0))
        val move = fixture.controller.pointerMove(position(1, 0))

        assertInstanceOf(EditorInteractionResult.WorkspaceReduced::class.java, down)
        assertInstanceOf(EditorInteractionResult.WorkspaceReduced::class.java, move)
        assertEquals(fixture.initialDocument, fixture.controller.documentState)
        assertEquals(
            listOf(position(0, 0), position(1, 0)),
            fixture.controller.workspaceState.preview
                .positions(),
        )
        assertNull(initialRenderState.preview)
        assertSame(fixture.initialDocument.snapshot, initialRenderState.snapshot)

        val end = fixture.controller.pointerEnd(position(2, 0))

        val executed = assertInstanceOf(EditorInteractionResult.CommandExecuted::class.java, end)
        assertInstanceOf(CommandResult.Applied::class.java, executed.commandResult)
        assertEquals(1L, fixture.controller.documentState.revision.value)
        assertNull(fixture.controller.workspaceState.preview)
        assertEquals(red, colorAt(fixture.controller.documentState, position(0, 0)))
        assertEquals(red, colorAt(fixture.controller.documentState, position(1, 0)))
        assertEquals(red, colorAt(fixture.controller.documentState, position(2, 0)))

        fixture.controller.pointerEnd(position(3, 0))

        assertEquals(1L, fixture.controller.documentState.revision.value)
    }

    @Test
    fun `cancel and outside end clear preview without document command`() {
        val cancelled = fixture()
        cancelled.controller.pointerDown(position(0, 0))
        cancelled.controller.pointerMove(position(1, 0))

        val cancelResult = cancelled.controller.pointerCancel()

        assertInstanceOf(EditorInteractionResult.WorkspaceReduced::class.java, cancelResult)
        assertNull(cancelled.controller.workspaceState.preview)
        assertSame(cancelled.initialDocument, cancelled.controller.documentState)

        val outside = fixture()
        outside.controller.pointerDown(position(0, 0))
        outside.controller.pointerMove(position(1, 0))

        val outsideResult = outside.controller.pointerEnd(position(4, 0))

        assertInstanceOf(EditorInteractionResult.WorkspaceReduced::class.java, outsideResult)
        assertNull(outside.controller.workspaceState.preview)
        assertSame(outside.initialDocument, outside.controller.documentState)
    }

    @Test
    fun `touch translation and direct core path produce identical command result and states`() {
        val direct = fixture()
        val touch = fixture()
        val samples = listOf(position(0, 0), position(1, 1), position(2, 1))

        val directOutcome = executeDirect(direct, samples)
        val touchResult = executeTranslatedTouch(touch, samples)

        assertEquals(directOutcome.commandResult, touchResult)
        assertEquals(direct.gateway.runtimeState.documentState, touch.gateway.runtimeState.documentState)
        assertEquals(directOutcome.workspaceState, touch.controller.workspaceState)
    }

    private fun executeDirect(
        fixture: EditorFixture,
        samples: List<PixelPosition>,
    ): DirectOutcome {
        var workspace = fixture.initialWorkspace
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
        val target = fixture.gateway.runtimeState.documentState
        return DirectOutcome(
            commandResult =
                fixture.gateway.execute(
                    ApplyStrokeCommand.create(target.id, target.revision, commit.stroke),
                ),
            workspaceState = commit.nextState,
        )
    }

    private fun executeTranslatedTouch(
        fixture: EditorFixture,
        samples: List<PixelPosition>,
    ): CommandResult {
        val translator = FixedCanvasTouchTranslator.create()
        val surface = Size(400.0f, 400.0f)
        val translated =
            samples.map { sample ->
                translator.translate(sample.centerOffset(), surface, fixture.initialDocument.size)
            }

        fixture.controller.pointerDown(translated[0].mappedPosition())
        fixture.controller.pointerMove(translated[1].mappedPosition())
        val result = fixture.controller.pointerEnd(translated[2].mappedPosition())

        return assertInstanceOf(EditorInteractionResult.CommandExecuted::class.java, result).commandResult
    }

    private fun PixelPosition.centerOffset(): Offset =
        Offset(x.value * CELL_SIZE + CELL_SIZE / 2.0f, y.value * CELL_SIZE + CELL_SIZE / 2.0f)

    private fun TouchTranslation.mappedPosition(): PixelPosition =
        when (this) {
            is TouchTranslation.Mapped -> position
            TouchTranslation.Outside -> fail("Test touch unexpectedly mapped outside")
        }

    private fun ToolGesture?.positions(): List<PixelPosition> =
        if (this == null) emptyList() else buildList { forEachPosition(::add) }

    private data class DirectOutcome(
        val commandResult: CommandResult,
        val workspaceState: WorkspaceState,
    )

    private companion object {
        const val CELL_SIZE: Float = 100.0f
    }
}
