package io.github.hideyukimori.nenepixel.core.application.workspace.selection

import io.github.hideyukimori.nenepixel.core.application.document.command.ApplyStrokeCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.defaultDefinition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.defaultDocumentId
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.redIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.stroke
import io.github.hideyukimori.nenepixel.core.application.editor.DocumentIdSource
import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.DocumentPoint
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.fail

/** Shared selection fixture uses only the existing runtime command/reducer boundaries. */
internal object SelectionTestValues {
    fun runtime(size: CanvasSize = canvas(8, 6)): EditorRuntime =
        EditorRuntime.create(size, defaultDefinition, SelectionDocumentIdSource())

    fun begin(
        runtime: EditorRuntime,
        layerId: LayerId = LayerId.first(),
    ) {
        assertInstanceOf(
            WorkspaceReductionResult.Reduced::class.java,
            runtime.selectionOperations.beginSelection(layerId),
        )
    }

    fun select(
        runtime: EditorRuntime,
        start: PixelPosition = position(0, 0),
        end: DocumentPoint = DocumentPoint.create(1, 1),
    ) {
        reduce(runtime, WorkspaceAction.BeginSelectionPointer(start))
        reduce(runtime, WorkspaceAction.UpdateSelectionPointer(end))
        reduce(runtime, WorkspaceAction.FinishSelectionPointer)
    }

    fun move(
        runtime: EditorRuntime,
        anchor: PixelPosition = position(0, 0),
        end: DocumentPoint = DocumentPoint.create(2, 1),
    ) {
        reduce(runtime, WorkspaceAction.BeginSelectionPointer(anchor))
        reduce(runtime, WorkspaceAction.UpdateSelectionPointer(end))
        reduce(runtime, WorkspaceAction.FinishSelectionPointer)
    }

    fun reduce(
        runtime: EditorRuntime,
        action: WorkspaceAction,
    ) {
        assertInstanceOf(WorkspaceReductionResult.Reduced::class.java, runtime.reduce(action))
    }

    fun session(runtime: EditorRuntime): SelectionSession =
        runtime.state.workspaceState.selectionSession ?: fail("Expected an open selection session")

    fun paint(runtime: EditorRuntime): CommandResult.Applied =
        assertInstanceOf(
            CommandResult.Applied::class.java,
            runtime.execute(
                ApplyStrokeCommand.create(
                    runtime.captureSource(),
                    LayerId.first(),
                    stroke(runtime.state.documentState.size, listOf(position(0, 0)), redIndex),
                ),
            ),
        )
}

private class SelectionDocumentIdSource : DocumentIdSource {
    override fun nextDocumentId(): DocumentId = defaultDocumentId
}
