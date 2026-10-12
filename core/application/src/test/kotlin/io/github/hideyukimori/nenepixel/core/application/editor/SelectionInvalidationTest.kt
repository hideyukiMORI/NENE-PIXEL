package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.AddLayerCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.defaultDefinition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.redIndex
import io.github.hideyukimori.nenepixel.core.application.persistence.SequentialDocumentIdSource
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceActionRejection
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.application.workspace.selection.SelectionInteraction
import io.github.hideyukimori.nenepixel.core.application.workspace.selection.SelectionTestValues.begin
import io.github.hideyukimori.nenepixel.core.application.workspace.selection.SelectionTestValues.move
import io.github.hideyukimori.nenepixel.core.application.workspace.selection.SelectionTestValues.reduce
import io.github.hideyukimori.nenepixel.core.application.workspace.selection.SelectionTestValues.runtime
import io.github.hideyukimori.nenepixel.core.application.workspace.selection.SelectionTestValues.select
import io.github.hideyukimori.nenepixel.core.application.workspace.selection.SelectionTestValues.session
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay
import io.github.hideyukimori.nenepixel.core.domain.geometry.DocumentPoint
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class SelectionInvalidationTest {
    @Test
    fun `palette slot keeps selection but quick menu tool and underlay adjustment cancel it`() {
        val runtime = runtime()
        begin(runtime)
        select(runtime)
        val retained = session(runtime)
        reduce(runtime, WorkspaceAction.SelectPaletteEntry(redIndex))
        assertSame(retained, session(runtime))
        reduce(runtime, WorkspaceAction.OpenQuickSelect)
        assertNull(runtime.state.workspaceState.selectionSession)
        runtime.reduce(WorkspaceAction.CancelQuickSelect)
        begin(runtime)
        select(runtime)
        reduce(runtime, WorkspaceAction.SelectTool(runtime.state.workspaceState.activeTool))
        assertNull(runtime.state.workspaceState.selectionSession)
        begin(runtime)
        select(runtime)
        val underlay =
            ReferenceUnderlay
                .placed(
                    UnderlayMemoryFixtures.image(),
                    runtime.state.documentState.size,
                ).adjusting()
        reduce(runtime, WorkspaceAction.SetReferenceUnderlay(underlay))
        assertNull(runtime.state.workspaceState.selectionSession)
    }

    @Test
    fun `workspace layer change and explicit palette edit enter cancel selection`() {
        val runtime = runtime()
        assertInstanceOf(
            CommandResult.Applied::class.java,
            runtime.execute(AddLayerCommand.create(runtime.captureSource(), LayerId.first())),
        )
        val upper = runtime.state.workspaceState.activeLayerId
        begin(runtime, LayerId.first())
        select(runtime)
        reduce(runtime, WorkspaceAction.SelectLayer(upper))
        assertNull(runtime.state.workspaceState.selectionSession)
        begin(runtime, upper)
        select(runtime)
        assertInstanceOf(WorkspaceReductionResult.Reduced::class.java, runtime.paletteOperations.beginPaletteEdit())
        assertNull(runtime.state.workspaceState.selectionSession)
    }

    @Test
    fun `runtime preview cancellation restores prior draft and owner replacement forgets it`() {
        val runtime = runtime()
        begin(runtime)
        select(runtime)
        move(runtime)
        val retained = session(runtime).previewTransfer
        reduce(runtime, WorkspaceAction.BeginSelectionPointer(position(2, 1)))
        reduce(runtime, WorkspaceAction.UpdateSelectionPointer(DocumentPoint.create(4, 3)))
        runtime.transact { transaction ->
            PersistenceTransition(transaction.coordination, Unit, RuntimeOwnerEffect.CancelPreview)
        }
        assertEquals(retained, session(runtime).previewTransfer)
        assertEquals(SelectionInteraction.Idle, session(runtime).interaction)
        runtime.transact { transaction ->
            PersistenceTransition(
                transaction.coordination,
                Unit,
                RuntimeOwnerEffect.ReplaceOwners(
                    RuntimeOwners.create(canvas(3, 3), defaultDefinition, SequentialDocumentIdSource()),
                ),
            )
        }
        assertNull(runtime.state.workspaceState.selectionSession)
        assertEquals(canvas(3, 3), runtime.state.documentState.size)
    }

    @Test
    fun `final Switching refuses entry Apply and pointer changes without discarding draft`() {
        val runtime = runtime()
        begin(runtime)
        select(runtime)
        move(runtime)
        runtime.transact { transaction ->
            val creation =
                assertInstanceOf(
                    OperationHandleCreation.Created::class.java,
                    transaction.coordination.nextOperationHandle(),
                )
            val candidate = RuntimeOwners.create(canvas(3, 3), defaultDefinition, SequentialDocumentIdSource())
            val operation =
                ActivePersistenceOperation.Switch.Switching(
                    creation.handle,
                    candidate,
                    SwitchKind.NewDocument,
                )
            PersistenceTransition(creation.next.withActive(operation), Unit)
        }
        val before = runtime.state
        assertEquals(
            WorkspaceActionRejection.PersistenceBusy,
            assertInstanceOf(
                WorkspaceReductionResult.Rejected::class.java,
                runtime.selectionOperations.beginSelection(LayerId.first()),
            ).rejection,
        )
        assertEquals(
            SelectionApplyResult.Rejected(WorkspaceActionRejection.PersistenceBusy),
            runtime.selectionOperations.applySelection(),
        )
        assertInstanceOf(
            WorkspaceReductionResult.Rejected::class.java,
            runtime.reduce(WorkspaceAction.CancelSelectionPointer),
        )
        assertEquals(before, runtime.state)
    }
}
