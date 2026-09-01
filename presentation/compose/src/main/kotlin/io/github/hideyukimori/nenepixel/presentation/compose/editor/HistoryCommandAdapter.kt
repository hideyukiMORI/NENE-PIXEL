package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandGateway
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.command.RedoCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.UndoCommand

internal class HistoryCommandAdapter(
    private val commandGateway: CommandGateway,
) {
    fun undo(): CommandResult {
        val target = commandGateway.runtimeState.documentState
        return commandGateway.execute(UndoCommand.create(target.id, target.revision))
    }

    fun redo(): CommandResult {
        val target = commandGateway.runtimeState.documentState
        return commandGateway.execute(RedoCommand.create(target.id, target.revision))
    }
}
