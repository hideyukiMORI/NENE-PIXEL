package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResultAssertions.applied
import io.github.hideyukimori.nenepixel.core.application.document.command.RedoCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.RejectionReason
import io.github.hideyukimori.nenepixel.core.application.document.command.SetFrameDurationCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.UndoCommand
import io.github.hideyukimori.nenepixel.core.application.document.transition.FrameTestValues.duration
import io.github.hideyukimori.nenepixel.core.application.document.transition.FrameTestValues.id
import io.github.hideyukimori.nenepixel.core.application.workspace.EditorTheme
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceActionRejection
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.application.workspace.playback.PlaybackMode
import io.github.hideyukimori.nenepixel.core.application.workspace.playback.PlaybackPulseResult
import io.github.hideyukimori.nenepixel.core.application.workspace.playback.PlaybackTestValues.MS
import io.github.hideyukimori.nenepixel.core.application.workspace.playback.PlaybackTestValues.document
import io.github.hideyukimori.nenepixel.core.application.workspace.playback.PlaybackTestValues.pulse
import io.github.hideyukimori.nenepixel.core.application.workspace.playback.PlaybackTestValues.runtime
import io.github.hideyukimori.nenepixel.core.application.workspace.playback.PlaybackTestValues.start
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class PlaybackRuntimeTest {
    @Test fun `samples preserve edit source history dirty checkpoints and persistence projections`() {
        val runtime = runtime()
        val before = runtime.state
        val source = runtime.captureSource()
        val history = runtime.read { it.historyPosition() }
        val autosave = runtime.autosaveProjection.value
        val persistence = runtime.persistenceOperation.value
        val memory = runtime.underlayMemory.value
        val token = start(runtime)
        pulse(runtime, token, 0L)
        val first = runtime.state.workspaceState.playback.view
        runtime.reduce(WorkspaceAction.SetActualSizeWindow(before.workspaceState.actualSizeWindow.toggled()))
        runtime.reduce(WorkspaceAction.SetAppearance(before.workspaceState.appearance.copy(theme = EditorTheme.Light)))
        pulse(runtime, token, 1 * MS)
        assertSame(first, runtime.state.workspaceState.playback.view)
        pulse(runtime, token, 10 * MS)
        assertEquals(id(2), runtime.state.workspaceState.playback.view.frameId)
        assertEquals(before.workspaceState.editTarget, runtime.state.workspaceState.editTarget)
        runtime.reduce(WorkspaceAction.StopPlayback)
        assertSame(before.documentState, runtime.state.documentState)
        assertEquals(before.dirtyState, runtime.state.dirtyState)
        assertEquals(history, runtime.read { it.historyPosition() })
        val afterSource = runtime.captureSource()
        assertSame(source.owner, afterSource.owner)
        assertSame(source.document, afterSource.document)
        assertEquals(source.position, afterSource.position)
        assertSame(autosave, runtime.autosaveProjection.value)
        assertSame(persistence, runtime.persistenceOperation.value)
        assertSame(memory, runtime.underlayMemory.value)
    }

    @Test fun `only applied commands and undo redo end playback before shared follow up`() {
        val runtime = runtime()
        val admission = runtime.captureSource()
        val token = start(runtime, PlaybackMode.Once)
        val before = runtime.state.workspaceState
        assertEquals(
            CommandResult.Rejected(RejectionReason.NoEffectiveChange),
            runtime.execute(SetFrameDurationCommand.create(admission, id(4), duration(10))),
        )
        assertSame(before, runtime.state.workspaceState)
        applied(runtime.execute(SetFrameDurationCommand.create(admission, id(4), duration(50))))
        assertNull(runtime.state.workspaceState.playback.view.token)
        assertSame(PlaybackPulseResult.Stale, runtime.advancePlayback(token, 0L))
        start(runtime, PlaybackMode.Once)
        val restarted = runtime.state.workspaceState
        assertEquals(
            CommandResult.Rejected(RejectionReason.SourceHistoryMismatch),
            runtime.execute(SetFrameDurationCommand.create(admission, id(4), duration(60))),
        )
        assertSame(restarted, runtime.state.workspaceState)
        val changed = runtime.state.documentState
        applied(runtime.execute(UndoCommand.create(changed.id, changed.revision)))
        assertNull(runtime.state.workspaceState.playback.view.token)
        start(runtime, PlaybackMode.Once)
        val original = runtime.state.documentState
        applied(runtime.execute(RedoCommand.create(original.id, original.revision)))
        assertNull(runtime.state.workspaceState.playback.view.token)
        assertEquals(PlaybackMode.Once, runtime.state.workspaceState.playback.view.mode)
    }

    @Test fun `final Switching cancels immediately and cleanup bypasses busy admission`() {
        val runtime = runtime()
        val token = start(runtime)
        val ready =
            assertInstanceOf(
                SwitchStart.Ready::class.java,
                runtime.switchOperations.beginNewDocument(NewDocumentRequest.create("2", "1")),
            )
        assertInstanceOf(SwitchBegin.Permit::class.java, runtime.switchOperations.beginSwitch(ready.handle))
        assertNull(runtime.state.workspaceState.playback.view.token)
        val before = runtime.state
        assertInstanceOf(
            WorkspaceReductionResult.Unchanged::class.java,
            runtime.reduce(WorkspaceAction.StopPlaybackIfCurrent(token)),
        )
        assertInstanceOf(WorkspaceReductionResult.Unchanged::class.java, runtime.reduce(WorkspaceAction.StopPlayback))
        val rejected =
            assertInstanceOf(
                WorkspaceReductionResult.Rejected::class.java,
                runtime.reduce(WorkspaceAction.StartPlayback),
            )
        assertEquals(WorkspaceActionRejection.PersistenceBusy, rejected.rejection)
        assertEquals(before, runtime.state)
        assertSame(PlaybackPulseResult.Stale, runtime.advancePlayback(token, 0L))
    }

    @Test fun `owner install carries only mode and stale cleanup cannot end its next session`() {
        val runtime = runtime()
        val old = start(runtime, PlaybackMode.Once)
        runtime.transact { transaction ->
            PersistenceTransition(
                transaction.coordination,
                Unit,
                RuntimeOwnerEffect.ReplaceOwners(RuntimeOwners.create(document())),
            )
        }
        assertEquals(PlaybackMode.Once, runtime.state.workspaceState.playback.view.mode)
        assertNull(runtime.state.workspaceState.playback.view.token)
        assertEquals(id(4), runtime.state.workspaceState.activeFrameId)
        val token = start(runtime, PlaybackMode.Once)
        val before = runtime.state.workspaceState
        runtime.reduce(WorkspaceAction.StopPlaybackIfCurrent(old))
        assertSame(PlaybackPulseResult.Stale, runtime.advancePlayback(old, Long.MAX_VALUE))
        assertSame(before, runtime.state.workspaceState)
        assertSame(token, runtime.state.workspaceState.playback.view.token)
    }
}
