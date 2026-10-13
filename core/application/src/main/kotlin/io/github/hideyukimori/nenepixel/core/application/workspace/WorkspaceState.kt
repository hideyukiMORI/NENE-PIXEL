package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.application.workspace.importing.PendingRasterImport
import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteEditSession
import io.github.hideyukimori.nenepixel.core.application.workspace.playback.PlaybackState
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.CanvasPointerIntent
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.EyedropperState
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.QuickSelection
import io.github.hideyukimori.nenepixel.core.application.workspace.selection.SelectionSession
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayInteraction
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayVisibility
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportState
import io.github.hideyukimori.nenepixel.core.domain.drawing.DrawingTool
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameId
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex

/**
 * The constructor is internal only for `withUnderlay` in `UnderlayReduction.kt` and `withPendingImport`
 * in `RasterImportReduction.kt`, selection copies in `SelectionReduction.kt`, and playback copies.
 * [create] remains the canonical initial factory. These reductions copy validated values only.
 */
public class WorkspaceState internal constructor(
    internal val editing: WorkspaceEditing,
    public val preview: ToolGesture?,
    internal val view: WorkspaceView,
    internal val sessions: WorkspaceSessions,
) {
    public val editTarget: EditTarget get() = editing.editTarget
    public val activeTool: DrawingTool get() = editing.activeTool
    public val viewport: ViewportState get() = view.viewport
    public val appearance: EditorAppearance get() = view.appearance
    public val actualSizeWindow: ActualSizeWindow get() = view.actualSizeWindow
    public val underlay: ReferenceUnderlay? get() = view.underlay
    public val paletteEditSession: PaletteEditSession? get() = sessions.paletteEditSession
    public val pendingImport: PendingRasterImport? get() = sessions.pendingImport
    public val quickSelection: QuickSelection get() = sessions.quickSelection
    internal val previewSessions: WorkspacePreviewSessions get() = sessions.previewSessions

    public val selectionSession: SelectionSession? get() = previewSessions.selection
    public val playback: PlaybackState get() = previewSessions.playback

    public val activePaletteIndex: PaletteIndex
        get() = editTarget.paletteIndex

    public val activeFrameId: FrameId get() = editTarget.frameId

    public val activeLayerId: LayerId
        get() = editTarget.layerId

    /**
     * How presentation translates a canvas pointer down: adjusting a shown underlay first (ADR 0032; an armed
     * eyedropper stays armed), then picking exactly while the eyedropper is armed (ADR 0029), else drawing.
     */
    public val canvasPointerIntent: CanvasPointerIntent
        get() {
            val underlay = underlay
            return when {
                underlay?.visibility == UnderlayVisibility.Shown &&
                    underlay.interaction == UnderlayInteraction.Adjusting -> CanvasPointerIntent.AdjustUnderlay

                quickSelection.eyedropper == EyedropperState.Armed -> CanvasPointerIntent.PickPaletteEntry

                selectionSession != null -> CanvasPointerIntent.SelectPixels

                else -> CanvasPointerIntent.Draw
            }
        }

    internal fun withEditTarget(editTarget: EditTarget): WorkspaceState =
        WorkspaceState(
            editing.copy(editTarget = editTarget),
            preview,
            view,
            sessions,
        )

    internal fun withActiveTool(activeTool: DrawingTool): WorkspaceState =
        WorkspaceState(
            editing.copy(activeTool = activeTool),
            preview,
            view,
            sessions,
        )

    /** Replaces the gesture preview; `null` ends it. */
    internal fun withPreview(preview: ToolGesture?): WorkspaceState =
        WorkspaceState(
            editing,
            preview,
            view,
            sessions,
        )

    internal fun withViewport(viewport: ViewportState): WorkspaceState =
        WorkspaceState(
            editing,
            null,
            view.copy(viewport = viewport),
            sessions.withPreviews(previewSessions.copy(selection = selectionSession?.cancelPointer())),
        )

    internal fun withAppearance(appearance: EditorAppearance): WorkspaceState =
        WorkspaceState(
            editing,
            null,
            view.copy(appearance = appearance),
            sessions.withPreviews(previewSessions.copy(selection = selectionSession?.cancelPointer())),
        )

    /** The actual-size window is non-modal: it never cancels an in-progress gesture (ADR 0026). */
    internal fun withActualSizeWindow(actualSizeWindow: ActualSizeWindow): WorkspaceState =
        WorkspaceState(
            editing,
            preview,
            view.copy(actualSizeWindow = actualSizeWindow),
            sessions,
        )

    /** The palette edit session is non-modal: opening or closing it never cancels an in-progress gesture (ADR 0022). */
    internal fun withPaletteEditSession(paletteEditSession: PaletteEditSession?): WorkspaceState {
        val selection = if (paletteEditSession != null) null else selectionSession
        return WorkspaceState(
            editing,
            preview,
            view,
            sessions.copy(
                paletteEditSession = paletteEditSession,
                previewSessions = previewSessions.copy(selection = selection),
            ),
        )
    }

    /** Quick-select state never cancels an in-progress gesture (ADR 0029). */
    internal fun withQuickSelection(quickSelection: QuickSelection): WorkspaceState =
        WorkspaceState(
            editing,
            preview,
            view,
            sessions.copy(
                quickSelection = quickSelection,
                previewSessions =
                    previewSessions.copy(
                        selection =
                            if (quickSelection.menu != null ||
                                quickSelection.eyedropper == EyedropperState.Armed
                            ) {
                                null
                            } else {
                                selectionSession
                            },
                    ),
            ),
        )

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is WorkspaceState &&
                    editTarget == other.editTarget &&
                    activeTool == other.activeTool &&
                    viewport == other.viewport &&
                    preview == other.preview &&
                    appearance == other.appearance &&
                    actualSizeWindow == other.actualSizeWindow &&
                    paletteEditSession == other.paletteEditSession &&
                    quickSelection == other.quickSelection &&
                    underlay == other.underlay &&
                    pendingImport == other.pendingImport &&
                    previewSessions == other.previewSessions
            )

    override fun hashCode(): Int =
        listOf(
            editTarget,
            activeTool,
            viewport,
            preview,
            appearance,
            actualSizeWindow,
            paletteEditSession,
            quickSelection,
            underlay,
            pendingImport,
            previewSessions,
        ).fold(INITIAL_HASH) { hash, value -> hash * HASH_MULTIPLIER + (value?.hashCode() ?: 0) }

    override fun toString(): String =
        "WorkspaceState(" +
            "editTarget=$editTarget, activeTool=$activeTool, viewport=$viewport, " +
            "preview=$preview, appearance=$appearance, actualSizeWindow=$actualSizeWindow, " +
            "paletteEditSession=$paletteEditSession, quickSelection=$quickSelection, underlay=$underlay, " +
            "pendingImport=$pendingImport, selectionSession=$selectionSession, playback=${playback.view})"

    public companion object {
        private const val INITIAL_HASH: Int = 1
        private const val HASH_MULTIPLIER: Int = 31

        public fun create(canvas: CanvasSize): WorkspaceState =
            WorkspaceState(
                WorkspaceEditing(
                    EditTarget.create(FrameId.first(), LayerId.first(), PaletteIndex.first),
                    DrawingTool.Pencil,
                ),
                null,
                WorkspaceView(ViewportState.initial(canvas), EditorAppearance.initial, ActualSizeWindow.initial, null),
                WorkspaceSessions(
                    null,
                    null,
                    QuickSelection.initial,
                    WorkspacePreviewSessions(null, PlaybackState.initial),
                ),
            )
    }
}
