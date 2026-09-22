package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandGateway
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceState
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.pixelengine.importing.LegacyReductionPreview

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

    fun asUnsaved(): RuntimeOwners = copy(cleanCheckpoint = DocumentCleanCheckpoint.Unsaved)

    fun sourceToken(runtimeGeneration: Long): RuntimeSourceToken {
        val commandState = commandGateway.runtimeState
        return RuntimeSourceToken(runtimeGeneration, commandState.documentState.id, commandState.historyPosition)
    }

    companion object {
        fun create(
            canvas: CanvasSize,
            definition: PaletteDefinition,
            documentIdSource: DocumentIdSource,
        ): RuntimeOwners {
            val snapshot = required(PixelSnapshot.createFilled(canvas, Revision.initial(), definition.defaultIndex))
            return create(required(DocumentState.create(documentIdSource.nextDocumentId(), definition, snapshot)))
        }

        fun create(document: DocumentState): RuntimeOwners {
            val commandGateway = CommandGateway.create(document)
            return RuntimeOwners(
                commandGateway,
                WorkspaceState.create(document.size),
                DocumentCleanCheckpoint.create(commandGateway.runtimeState),
            )
        }

        fun createDerived(
            preview: LegacyReductionPreview,
            documentIdSource: DocumentIdSource,
        ): RuntimeOwners =
            create(
                required(
                    DocumentState.create(
                        documentIdSource.nextDocumentId(),
                        preview.definition,
                        preview.snapshot,
                    ),
                ),
            ).asUnsaved()

        private fun <T> required(result: DomainValueResult<T>): T =
            when (result) {
                is DomainValueResult.Created -> {
                    result.value
                }

                is DomainValueResult.Rejected -> {
                    error(
                        "Validated document inputs produced an invalid runtime: ${result.rejection}",
                    )
                }
            }
    }
}
