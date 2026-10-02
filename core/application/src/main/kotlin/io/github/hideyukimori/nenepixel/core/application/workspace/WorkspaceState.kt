package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.application.workspace.importing.PendingRasterImport
import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteEditSession
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.CanvasPointerIntent
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.EyedropperState
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.QuickSelection
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayInteraction
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayVisibility
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportState
import io.github.hideyukimori.nenepixel.core.domain.drawing.DrawingTool
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex

/**
 * The constructor is internal only for `withUnderlay` in `UnderlayReduction.kt` and `withPendingImport`
 * in `RasterImportReduction.kt`; no other code outside this class calls it, and [create] stays the one canonical
 * factory.
 */
public class WorkspaceState internal constructor(
    public val editTarget: EditTarget,
    public val activeTool: DrawingTool,
    public val viewport: ViewportState,
    public val preview: ToolGesture?,
    public val appearance: EditorAppearance,
    public val actualSizeWindow: ActualSizeWindow,
    public val paletteEditSession: PaletteEditSession?,
    public val quickSelection: QuickSelection,
    public val underlay: ReferenceUnderlay?,
    public val pendingImport: PendingRasterImport?,
) {
    public val activePaletteIndex: PaletteIndex
        get() = editTarget.paletteIndex

    public val activeLayerId: LayerId
        get() = editTarget.layerId

    /**
     * How presentation translates a canvas pointer down: adjusting a shown underlay first (ADR 0032; an armed
     * eyedropper stays armed), then picking exactly while the eyedropper is armed (ADR 0029), else drawing.
     */
    public val canvasPointerIntent: CanvasPointerIntent
        get() =
            when {
                underlay?.visibility == UnderlayVisibility.Shown &&
                    underlay.interaction == UnderlayInteraction.Adjusting -> CanvasPointerIntent.AdjustUnderlay

                quickSelection.eyedropper == EyedropperState.Armed -> CanvasPointerIntent.PickPaletteEntry

                else -> CanvasPointerIntent.Draw
            }

    internal fun withEditTarget(editTarget: EditTarget): WorkspaceState =
        WorkspaceState(
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
        )

    internal fun withActiveTool(activeTool: DrawingTool): WorkspaceState =
        WorkspaceState(
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
        )

    /** Replaces the gesture preview; `null` ends it. */
    internal fun withPreview(preview: ToolGesture?): WorkspaceState =
        WorkspaceState(
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
        )

    internal fun withViewport(viewport: ViewportState): WorkspaceState =
        WorkspaceState(
            editTarget,
            activeTool,
            viewport,
            null,
            appearance,
            actualSizeWindow,
            paletteEditSession,
            quickSelection,
            underlay,
            pendingImport,
        )

    internal fun withAppearance(appearance: EditorAppearance): WorkspaceState =
        WorkspaceState(
            editTarget,
            activeTool,
            viewport,
            null,
            appearance,
            actualSizeWindow,
            paletteEditSession,
            quickSelection,
            underlay,
            pendingImport,
        )

    /** The actual-size window is non-modal: it never cancels an in-progress gesture (ADR 0026). */
    internal fun withActualSizeWindow(actualSizeWindow: ActualSizeWindow): WorkspaceState =
        WorkspaceState(
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
        )

    /** The palette edit session is non-modal: opening or closing it never cancels an in-progress gesture (ADR 0022). */
    internal fun withPaletteEditSession(paletteEditSession: PaletteEditSession?): WorkspaceState =
        WorkspaceState(
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
        )

    /** Quick-select state never cancels an in-progress gesture (ADR 0029). */
    internal fun withQuickSelection(quickSelection: QuickSelection): WorkspaceState =
        WorkspaceState(
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
                    pendingImport == other.pendingImport
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
        ).fold(INITIAL_HASH) { hash, value -> hash * HASH_MULTIPLIER + (value?.hashCode() ?: 0) }

    override fun toString(): String =
        "WorkspaceState(" +
            "editTarget=$editTarget, activeTool=$activeTool, viewport=$viewport, " +
            "preview=$preview, appearance=$appearance, actualSizeWindow=$actualSizeWindow, " +
            "paletteEditSession=$paletteEditSession, quickSelection=$quickSelection, underlay=$underlay, " +
            "pendingImport=$pendingImport)"

    public companion object {
        private const val INITIAL_HASH: Int = 1
        private const val HASH_MULTIPLIER: Int = 31

        public fun create(canvas: CanvasSize): WorkspaceState =
            WorkspaceState(
                EditTarget.create(LayerId.first(), PaletteIndex.first),
                DrawingTool.Pencil,
                ViewportState.initial(canvas),
                null,
                EditorAppearance.initial,
                ActualSizeWindow.initial,
                null,
                QuickSelection.initial,
                null,
                null,
            )
    }
}
