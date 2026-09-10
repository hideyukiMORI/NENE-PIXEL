package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandGateway
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceState
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot

internal data class RuntimeOwners(
    val commandGateway: CommandGateway,
    val workspaceState: WorkspaceState,
    val cleanCheckpoint: DocumentCleanCheckpoint,
) {
    fun toState(): EditorRuntimeState {
        val commandState = commandGateway.runtimeState
        return EditorRuntimeState(
            documentState = commandState.documentState,
            historyAvailability = commandState.historyAvailability,
            workspaceState = workspaceState,
            dirtyState = cleanCheckpoint.deriveDirtyState(commandState),
        )
    }

    fun isDirty(): Boolean = cleanCheckpoint.deriveDirtyState(commandGateway.runtimeState) == DocumentDirtyState.Dirty

    fun documentId(): DocumentId = commandGateway.runtimeState.documentState.id

    fun sourceToken(runtimeGeneration: Long): RuntimeSourceToken {
        val commandState = commandGateway.runtimeState
        return RuntimeSourceToken(runtimeGeneration, commandState.documentState.id, commandState.historyPosition)
    }

    companion object {
        fun create(
            canvas: CanvasSize,
            documentIdSource: DocumentIdSource,
        ): RuntimeOwners {
            val snapshot = PixelSnapshot.createFilled(canvas, Revision.initial(), PixelColor.blank)
            return create(DocumentState.create(documentIdSource.nextDocumentId(), snapshot))
        }

        fun create(document: DocumentState): RuntimeOwners {
            val commandGateway = CommandGateway.create(document)
            return RuntimeOwners(
                commandGateway,
                WorkspaceState.create(document.size),
                DocumentCleanCheckpoint.create(commandGateway.runtimeState),
            )
        }
    }
}
