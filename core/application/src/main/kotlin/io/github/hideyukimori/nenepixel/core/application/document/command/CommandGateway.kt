package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.application.document.transition.DocumentTransitionResult
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState

public class CommandGateway private constructor(
    initialState: DocumentState,
) {
    private val executionLock: Any = Any()
    private var currentState: DocumentState = initialState
    private val applyStrokeCommandHandler: ApplyStrokeCommandHandler = ApplyStrokeCommandHandler()

    public val documentState: DocumentState
        get() = synchronized(executionLock) { currentState }

    public fun execute(command: DocumentCommand): CommandResult =
        synchronized(executionLock) {
            when (command) {
                is ApplyStrokeCommand -> commit(applyStrokeCommandHandler.execute(currentState, command))
            }
        }

    private fun commit(result: DocumentTransitionResult): CommandResult =
        when (result) {
            is DocumentTransitionResult.Created -> {
                currentState = result.transition.nextState
                CommandResult.Applied(result.transition.changeSet)
            }

            is DocumentTransitionResult.Rejected -> {
                CommandResult.Rejected(result.reason)
            }
        }

    public companion object {
        public fun create(initialState: DocumentState): CommandGateway = CommandGateway(initialState)
    }
}
