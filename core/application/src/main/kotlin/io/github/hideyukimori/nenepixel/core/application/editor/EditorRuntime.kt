package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandGateway
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.command.DocumentCommand
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReducer
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceState
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot

public class EditorRuntime private constructor(
    private val documentIdSource: DocumentIdSource,
    public val palette: Palette,
    private val workspaceReducer: WorkspaceReducer,
    initialOwners: RuntimeOwners,
) {
    private val runtimeLock: Any = Any()
    private var owners: RuntimeOwners = initialOwners

    public val state: EditorRuntimeState
        get() = synchronized(runtimeLock) { owners.toState() }

    public fun execute(command: DocumentCommand): CommandResult =
        synchronized(runtimeLock) {
            owners.commandGateway.execute(command)
        }

    public fun reduce(action: WorkspaceAction): WorkspaceReductionResult =
        synchronized(runtimeLock) {
            val result = workspaceReducer.reduce(owners.workspaceState, action)
            owners = owners.copy(workspaceState = result.nextState)
            result
        }

    public fun createNewDocument(requestResult: NewDocumentRequestResult): NewDocumentResult =
        synchronized(runtimeLock) {
            when (requestResult) {
                is NewDocumentRequestResult.Created -> {
                    owners =
                        createRuntimeOwners(
                            requestResult.request.canvas,
                            documentIdSource,
                        )
                    NewDocumentResult.Created(owners.toState())
                }

                is NewDocumentRequestResult.Rejected -> {
                    NewDocumentResult.Rejected(requestResult.rejection, owners.toState())
                }
            }
        }

    private fun RuntimeOwners.toState(): EditorRuntimeState {
        val commandState = commandGateway.runtimeState
        return EditorRuntimeState(
            documentState = commandState.documentState,
            historyAvailability = commandState.historyAvailability,
            workspaceState = workspaceState,
            dirtyState = cleanCheckpoint.deriveDirtyState(commandState),
        )
    }

    public companion object {
        public fun create(
            initialCanvas: CanvasSize,
            palette: Palette,
            documentIdSource: DocumentIdSource,
        ): EditorRuntime =
            EditorRuntime(
                documentIdSource,
                palette,
                WorkspaceReducer.create(palette),
                createRuntimeOwners(initialCanvas, documentIdSource),
            )
    }
}

private data class RuntimeOwners(
    val commandGateway: CommandGateway,
    val workspaceState: WorkspaceState,
    val cleanCheckpoint: DocumentCleanCheckpoint,
)

private fun createRuntimeOwners(
    canvas: CanvasSize,
    documentIdSource: DocumentIdSource,
): RuntimeOwners {
    val documentId = documentIdSource.nextDocumentId()
    val snapshot = PixelSnapshot.createFilled(canvas, Revision.initial(), PixelColor.blank)
    val document = DocumentState.create(documentId, snapshot)
    val commandGateway = CommandGateway.create(document)
    return RuntimeOwners(
        commandGateway,
        WorkspaceState.create(canvas),
        DocumentCleanCheckpoint.create(commandGateway.runtimeState),
    )
}
