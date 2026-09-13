package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.application.document.history.BoundedLinearHistory
import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryAppendRejection
import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryAppendResult
import io.github.hideyukimori.nenepixel.core.application.document.transition.DocumentTransitionResult
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState

public class CommandGateway private constructor(
    initialState: DocumentState,
) {
    private val executionLock: Any = Any()
    private val sourceOwner: CommandSourceOwner = CommandSourceOwner()
    private var currentState: DocumentState = initialState
    private var history: BoundedLinearHistory = BoundedLinearHistory.empty()
    private val applyStrokeCommandHandler: ApplyStrokeCommandHandler = ApplyStrokeCommandHandler()
    private val replacePaletteCommandHandler: ReplacePaletteCommandHandler = ReplacePaletteCommandHandler()
    private val undoCommandHandler: UndoCommandHandler = UndoCommandHandler()
    private val redoCommandHandler: RedoCommandHandler = RedoCommandHandler()

    public val runtimeState: CommandRuntimeState
        get() =
            synchronized(executionLock) {
                CommandRuntimeState(
                    documentState = currentState,
                    historyAvailability = history.availability,
                    historyPosition = history.currentPosition,
                    retention =
                        HistoryRetentionSummary(
                            entryCount = history.entryCount,
                            changeCount = history.retainedChangeCount,
                            byteCount = history.retainedByteCount,
                        ),
                )
            }

    public fun captureSource(): CommandSourceAdmission =
        synchronized(executionLock) { CommandSourceAdmission(sourceOwner, currentState, history.currentPosition) }

    public fun execute(command: DocumentCommand): CommandResult =
        synchronized(executionLock) {
            when (command) {
                is ApplyStrokeCommand -> {
                    executeAdmitted(command.admission) {
                        applyStrokeCommandHandler.execute(currentState, command)
                    }
                }

                is ReplacePaletteCommand -> {
                    executeAdmitted(command.admission) {
                        replacePaletteCommandHandler.execute(currentState, command)
                    }
                }

                is UndoCommand -> {
                    executeUndo(command)
                }

                is RedoCommand -> {
                    executeRedo(command)
                }
            }
        }

    private fun executeAdmitted(
        admission: CommandSourceAdmission,
        transition: () -> DocumentTransitionResult,
    ): CommandResult =
        when {
            admission.owner !== sourceOwner -> {
                CommandResult.Rejected(RejectionReason.SourceOwnerMismatch)
            }

            admission.document.id != currentState.id -> {
                CommandResult.Rejected(RejectionReason.TargetDocumentMismatch(admission.document.id, currentState.id))
            }

            admission.position != history.currentPosition -> {
                CommandResult.Rejected(RejectionReason.SourceHistoryMismatch)
            }

            else -> {
                executeForward(transition())
            }
        }

    private fun executeForward(result: DocumentTransitionResult): CommandResult =
        when (result) {
            is DocumentTransitionResult.Created -> {
                val applied = CommandResult.Applied(result.transition.changeSet)
                when (val append = history.append(applied)) {
                    is HistoryAppendResult.Appended -> commit(result, append.history, applied)
                    is HistoryAppendResult.Rejected -> CommandResult.Rejected(append.rejection.toReason())
                }
            }

            is DocumentTransitionResult.Rejected -> {
                rejected(result)
            }
        }

    private fun executeUndo(command: UndoCommand): CommandResult {
        val entry = history.undoEntry
        return when (val result = undoCommandHandler.execute(currentState, command, entry)) {
            is DocumentTransitionResult.Created -> {
                val applied = CommandResult.Applied(result.transition.changeSet)
                commit(result, history.moveBackward(), applied)
            }

            is DocumentTransitionResult.Rejected -> {
                rejected(result)
            }
        }
    }

    private fun executeRedo(command: RedoCommand): CommandResult {
        val entry = history.redoEntry
        return when (val result = redoCommandHandler.execute(currentState, command, entry)) {
            is DocumentTransitionResult.Created -> {
                val applied = CommandResult.Applied(result.transition.changeSet)
                commit(result, history.moveForward(), applied)
            }

            is DocumentTransitionResult.Rejected -> {
                rejected(result)
            }
        }
    }

    private fun commit(
        result: DocumentTransitionResult.Created,
        nextHistory: BoundedLinearHistory,
        applied: CommandResult.Applied,
    ): CommandResult.Applied {
        currentState = result.transition.nextState
        history = nextHistory
        return applied
    }

    private fun rejected(result: DocumentTransitionResult.Rejected): CommandResult.Rejected =
        CommandResult.Rejected(result.reason)

    public companion object {
        public fun create(initialState: DocumentState): CommandGateway = CommandGateway(initialState)
    }
}

private fun HistoryAppendRejection.toReason(): RejectionReason =
    when (this) {
        is HistoryAppendRejection.EntryAboveRetainedChangeMaximum -> {
            RejectionReason.HistoryEntryAboveRetainedChangeMaximum(attemptedCount, maximum)
        }

        HistoryAppendRejection.PositionExhausted -> {
            RejectionReason.HistoryPositionExhausted
        }

        is HistoryAppendRejection.EntryAboveRetainedPayloadMaximum -> {
            RejectionReason.HistoryEntryAboveRetainedPayloadMaximum(attemptedBytes, maximum)
        }
    }
