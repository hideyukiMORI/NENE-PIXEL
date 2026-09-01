package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandGateway
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReducer
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceState
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportGesture
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportMappingResult
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurface
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurfacePoint
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTransform
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportValueResult
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition

public class FixedSliceEditorController private constructor(
    private val commandGateway: CommandGateway,
    private val historyCommandAdapter: HistoryCommandAdapter,
    private val session: EditorWorkspaceSession,
) {
    public val renderState: EditorRenderState
        get() = session.renderState

    public val callbacks: EditorCallbacks =
        EditorCallbacks(
            pointerDown = ::pointerDown,
            pointerMove = ::pointerMove,
            pointerEnd = ::pointerEnd,
            pointerCancel = ::pointerCancel,
            viewportStarted = ::viewportStarted,
            viewportTransformed = ::viewportTransformed,
            undo = {
                historyCommandAdapter.undo()
                session.renderState
            },
            redo = {
                historyCommandAdapter.redo()
                session.renderState
            },
        )

    internal val documentState: DocumentState
        get() = commandGateway.runtimeState.documentState

    internal val workspaceState: WorkspaceState
        get() = session.workspaceState

    internal fun pointerDown(
        surface: ViewportSurface,
        point: ViewportSurfacePoint,
    ): PointerInputAcknowledgement =
        withMappedPoint(surface, point, outsideIsCancellation = false) { position ->
            session.reduce(
                WorkspaceAction.BeginGesturePreview(commandGateway.runtimeState.documentState.size, position),
            )
        }

    internal fun pointerMove(
        surface: ViewportSurface,
        point: ViewportSurfacePoint,
    ): PointerInputAcknowledgement =
        withMappedPoint(surface, point, outsideIsCancellation = true) { position ->
            session.reduce(WorkspaceAction.ExtendGesturePreview(position))
        }

    internal fun pointerEnd(
        surface: ViewportSurface,
        point: ViewportSurfacePoint,
    ): PointerInputAcknowledgement =
        withMappedPoint(surface, point, outsideIsCancellation = true, action = session::finishGesture)

    internal fun pointerCancel(): PointerInputAcknowledgement =
        if (session.workspaceState.preview == null) {
            session.ignored()
        } else {
            session.reduce(WorkspaceAction.CancelGesturePreview)
        }

    internal fun viewportStarted(surface: ViewportSurface): PointerInputAcknowledgement =
        when (val transform = createTransform(surface)) {
            is ViewportValueResult.Created -> session.reduce(WorkspaceAction.SetViewport(transform.value.viewport))
            is ViewportValueResult.Rejected -> session.rejected()
        }

    internal fun viewportTransformed(
        surface: ViewportSurface,
        gesture: ViewportGesture,
    ): PointerInputAcknowledgement =
        when (val transform = createTransform(surface)) {
            is ViewportValueResult.Created -> {
                when (val nextViewport = transform.value.apply(gesture)) {
                    is ViewportValueResult.Created -> session.reduce(WorkspaceAction.SetViewport(nextViewport.value))
                    is ViewportValueResult.Rejected -> session.rejected()
                }
            }

            is ViewportValueResult.Rejected -> {
                session.rejected()
            }
        }

    private fun withMappedPoint(
        surface: ViewportSurface,
        point: ViewportSurfacePoint,
        outsideIsCancellation: Boolean,
        action: (PixelPosition) -> PointerInputAcknowledgement,
    ): PointerInputAcknowledgement =
        when (val transform = createTransform(surface)) {
            is ViewportValueResult.Created -> {
                mapWithNormalizedViewport(transform.value, point, outsideIsCancellation, action)
            }

            is ViewportValueResult.Rejected -> {
                session.rejected()
            }
        }

    private fun mapWithNormalizedViewport(
        transform: ViewportTransform,
        point: ViewportSurfacePoint,
        outsideIsCancellation: Boolean,
        action: (PixelPosition) -> PointerInputAcknowledgement,
    ): PointerInputAcknowledgement {
        if (transform.viewport != session.workspaceState.viewport) {
            val normalization = session.reduce(WorkspaceAction.SetViewport(transform.viewport))
            if (
                normalization is PointerInputAcknowledgement.Cancelled ||
                normalization is PointerInputAcknowledgement.Rejected
            ) {
                return normalization
            }
        }
        return when (val mapping = transform.toPixelPosition(point)) {
            is ViewportMappingResult.Mapped -> action(mapping.value)

            ViewportMappingResult.OutsideCanvas,
            ViewportMappingResult.OutsideSurface,
            -> if (outsideIsCancellation) pointerCancel() else session.ignored()
        }
    }

    private fun createTransform(surface: ViewportSurface): ViewportValueResult<ViewportTransform> =
        ViewportTransform.create(
            canvas = commandGateway.runtimeState.documentState.size,
            surface = surface,
            viewport = session.workspaceState.viewport,
        )

    public companion object {
        public fun create(
            commandGateway: CommandGateway,
            workspaceReducer: WorkspaceReducer,
            initialWorkspaceState: WorkspaceState,
        ): FixedSliceEditorController =
            FixedSliceEditorController(
                commandGateway,
                HistoryCommandAdapter(commandGateway),
                EditorWorkspaceSession(commandGateway, workspaceReducer, initialWorkspaceState),
            )
    }
}
