package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteDraftOperation
import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteImportMode
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportState
import io.github.hideyukimori.nenepixel.core.domain.drawing.DrawingTool
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex

public sealed interface WorkspaceAction {
    public data class SetAppearance(
        public val appearance: EditorAppearance,
    ) : WorkspaceAction

    public data class SetActualSizeWindow(
        public val window: ActualSizeWindow,
    ) : WorkspaceAction

    public data class SelectPaletteEntry(
        public val index: PaletteIndex,
    ) : WorkspaceAction

    public data class SelectTool(
        public val tool: DrawingTool,
    ) : WorkspaceAction

    public data class BeginGesturePreview(
        public val canvas: CanvasSize,
        public val position: PixelPosition,
    ) : WorkspaceAction

    public data class ExtendGesturePreview(
        public val position: PixelPosition,
    ) : WorkspaceAction

    public data object CancelGesturePreview : WorkspaceAction

    public data object PrepareGestureCommit : WorkspaceAction

    public data class SetViewport(
        public val viewport: ViewportState,
    ) : WorkspaceAction

    /** Every action the palette session owns; the reducer hands the whole family to the session reduction. */
    public sealed interface PaletteSessionAction : WorkspaceAction

    public data object CancelPaletteEdit : PaletteSessionAction

    public data class EditPaletteDraft(
        public val operation: PaletteDraftOperation,
    ) : PaletteSessionAction

    public data object UndoPaletteDraft : PaletteSessionAction

    public data object RedoPaletteDraft : PaletteSessionAction

    /** Stages an imported palette as the pending import of the open draft, replacing any earlier one. */
    public data class ImportPaletteDraft(
        public val definition: PaletteDefinition,
    ) : PaletteSessionAction

    public data class SetPaletteImportMode(
        public val mode: PaletteImportMode,
    ) : PaletteSessionAction

    /** Maps draft slot `source` to imported slot `destination` in the pending import. */
    public data class AssignPaletteImportSlot(
        public val source: PaletteIndex,
        public val destination: PaletteIndex,
    ) : PaletteSessionAction

    public data object ConfirmPaletteImport : PaletteSessionAction

    public data object CancelPaletteImport : PaletteSessionAction
}

/**
 * ADR 0022: while a palette draft is open, only appearance, viewport, selection, preview cancellation and
 * draft actions pass; drawing and tool changes wait until the session closes.
 */
internal fun WorkspaceAction.isAllowedDuringPaletteSession(): Boolean =
    when (this) {
        is WorkspaceAction.SetAppearance,
        is WorkspaceAction.SetActualSizeWindow,
        is WorkspaceAction.SetViewport,
        is WorkspaceAction.SelectPaletteEntry,
        WorkspaceAction.CancelGesturePreview,
        WorkspaceAction.CancelPaletteEdit,
        is WorkspaceAction.EditPaletteDraft,
        WorkspaceAction.UndoPaletteDraft,
        WorkspaceAction.RedoPaletteDraft,
        is WorkspaceAction.ImportPaletteDraft,
        is WorkspaceAction.SetPaletteImportMode,
        is WorkspaceAction.AssignPaletteImportSlot,
        WorkspaceAction.ConfirmPaletteImport,
        WorkspaceAction.CancelPaletteImport,
        is BeginPaletteEdit,
        is ReconcileDocumentPalette,
        -> true

        is WorkspaceAction.SelectTool,
        is WorkspaceAction.BeginGesturePreview,
        is WorkspaceAction.ExtendGesturePreview,
        WorkspaceAction.PrepareGestureCommit,
        -> false
    }
