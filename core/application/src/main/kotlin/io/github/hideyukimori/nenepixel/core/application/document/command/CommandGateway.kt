package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryEntry
import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryRetentionPolicy
import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryRetentionResult
import io.github.hideyukimori.nenepixel.core.application.document.history.OneLevelHistoryState
import io.github.hideyukimori.nenepixel.core.application.document.transition.DocumentTransitionResult
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState

public class CommandGateway private constructor(
    initialState: DocumentState,
) {
    private val executionLock: Any = Any()
    private var currentState: DocumentState = initialState
    private var historyState: OneLevelHistoryState = OneLevelHistoryState.Empty
    private val applyStrokeCommandHandler: ApplyStrokeCommandHandler = ApplyStrokeCommandHandler()
    private val undoCommandHandler: UndoCommandHandler = UndoCommandHandler()
    private val redoCommandHandler: RedoCommandHandler = RedoCommandHandler()

    public val runtimeState: CommandRuntimeState
        get() =
            synchronized(executionLock) {
                CommandRuntimeState(currentState, historyState.availability)
            }

    public fun execute(command: DocumentCommand): CommandResult =
        synchronized(executionLock) {
            when (command) {
                is ApplyStrokeCommand -> executeApplyStroke(command)
                is UndoCommand -> executeUndo(command)
                is RedoCommand -> executeRedo(command)
            }
        }

    private fun executeApplyStroke(command: ApplyStrokeCommand): CommandResult =
        when (val result = applyStrokeCommandHandler.execute(currentState, command)) {
            is DocumentTransitionResult.Created -> {
                val applied = CommandResult.Applied(result.transition.changeSet)
                requireSupportedHistory(applied)
                val nextHistory = OneLevelHistoryState.UndoAvailable(HistoryEntry.create(applied))
                commit(result, nextHistory, applied)
            }

            is DocumentTransitionResult.Rejected -> {
                rejected(result)
            }
        }

    private fun executeUndo(command: UndoCommand): CommandResult {
        val previousHistory = historyState
        val entry = (previousHistory as? OneLevelHistoryState.UndoAvailable)?.entry
        return when (val result = undoCommandHandler.execute(currentState, command, entry)) {
            is DocumentTransitionResult.Created -> {
                val applied = CommandResult.Applied(result.transition.changeSet)
                val nextHistory = moveToRedo(previousHistory)
                commit(result, nextHistory, applied)
            }

            is DocumentTransitionResult.Rejected -> {
                rejected(result)
            }
        }
    }

    private fun executeRedo(command: RedoCommand): CommandResult {
        val previousHistory = historyState
        val entry = (previousHistory as? OneLevelHistoryState.RedoAvailable)?.entry
        return when (val result = redoCommandHandler.execute(currentState, command, entry)) {
            is DocumentTransitionResult.Created -> {
                val applied = CommandResult.Applied(result.transition.changeSet)
                val nextHistory = moveToUndo(previousHistory)
                commit(result, nextHistory, applied)
            }

            is DocumentTransitionResult.Rejected -> {
                rejected(result)
            }
        }
    }

    private fun commit(
        result: DocumentTransitionResult.Created,
        nextHistory: OneLevelHistoryState,
        applied: CommandResult.Applied,
    ): CommandResult.Applied {
        currentState = result.transition.nextState
        historyState = nextHistory
        return applied
    }

    private fun requireSupportedHistory(applied: CommandResult.Applied) {
        val evaluation =
            HistoryRetentionPolicy.evaluate(
                entryCount = 1,
                retainedChangeCount = applied.changeSet.retainedChangeCount,
            )
        check(evaluation is HistoryRetentionResult.Accepted) {
            "One-level history exceeded the accepted retention policy: $evaluation"
        }
    }

    private fun moveToRedo(previousHistory: OneLevelHistoryState): OneLevelHistoryState =
        when (previousHistory) {
            is OneLevelHistoryState.UndoAvailable -> OneLevelHistoryState.RedoAvailable(previousHistory.entry)

            OneLevelHistoryState.Empty,
            is OneLevelHistoryState.RedoAvailable,
            -> error("Undo transition was created without an undo history entry.")
        }

    private fun moveToUndo(previousHistory: OneLevelHistoryState): OneLevelHistoryState =
        when (previousHistory) {
            is OneLevelHistoryState.RedoAvailable -> OneLevelHistoryState.UndoAvailable(previousHistory.entry)

            OneLevelHistoryState.Empty,
            is OneLevelHistoryState.UndoAvailable,
            -> error("Redo transition was created without a redo history entry.")
        }

    private fun rejected(result: DocumentTransitionResult.Rejected): CommandResult.Rejected =
        CommandResult.Rejected(result.reason)

    public companion object {
        public fun create(initialState: DocumentState): CommandGateway = CommandGateway(initialState)
    }
}
