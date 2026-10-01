package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.application.workspace.importing.PendingRasterImport
import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteDraftOperation
import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteImportMode
import io.github.hideyukimori.nenepixel.core.application.workspace.quickselect.QuickSelectItem
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportState
import io.github.hideyukimori.nenepixel.core.domain.drawing.DrawingTool
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
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

    /** Every gesture preview action; the reducer hands the whole family to one reduction. */
    public sealed interface GesturePreviewAction : WorkspaceAction

    public data class BeginGesturePreview(
        public val canvas: CanvasSize,
        public val position: PixelPosition,
    ) : GesturePreviewAction

    public data class ExtendGesturePreview(
        public val position: PixelPosition,
    ) : GesturePreviewAction

    public data object CancelGesturePreview : GesturePreviewAction

    public data object PrepareGestureCommit : GesturePreviewAction

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

    /** Every quick-select menu and eyedropper action; the reducer hands the whole family to one reduction. */
    public sealed interface QuickSelectAction : WorkspaceAction

    /** Opens the quick-select menu with the recent slots and the eyedropper, nothing highlighted. */
    public data object OpenQuickSelect : QuickSelectAction

    /** Highlights one item of the open menu, or clears the highlight with `null`. */
    public data class HighlightQuickSelectItem(
        public val item: QuickSelectItem?,
    ) : QuickSelectAction

    /** Closes the menu and applies its highlight. */
    public data object ConfirmQuickSelect : QuickSelectAction

    /** Closes the menu and changes nothing else. */
    public data object CancelQuickSelect : QuickSelectAction

    /** Selects the slot index painted at `position` and returns the armed eyedropper to idle. */
    public data class PickPaletteEntryAt(
        public val position: PixelPosition,
    ) : QuickSelectAction

    /** Returns the armed eyedropper to idle. */
    public data object DisarmEyedropper : QuickSelectAction

    /** Every active-layer action (ADR 0030); the reducer hands the whole family to one reduction. */
    public sealed interface LayerAction : WorkspaceAction

    /** Makes [layerId] the active layer; the only selection route (ADR 0030). */
    public data class SelectLayer(
        public val layerId: LayerId,
    ) : LayerAction

    /** Every reference-underlay action (ADR 0032); the reducer hands the whole family to one reduction. */
    public sealed interface ReferenceUnderlayAction : WorkspaceAction

    /** Replaces the whole reference underlay with [underlay]; never rejected. */
    public data class SetReferenceUnderlay(
        public val underlay: ReferenceUnderlay,
    ) : ReferenceUnderlayAction

    /** Removes the reference underlay; never rejected. */
    public data object ClearReferenceUnderlay : ReferenceUnderlayAction

    /** Every pending PNG import action (ADR 0033); the reducer hands the whole family to one reduction. */
    public sealed interface RasterImportAction : WorkspaceAction

    /** Replaces the pending import with [pending]; never rejected. */
    public data class SetPendingRasterImport(
        public val pending: PendingRasterImport,
    ) : RasterImportAction

    /** Removes the pending import; never rejected, and passes during a palette session (ADR 0033). */
    public data object ClearPendingRasterImport : RasterImportAction
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
        is WorkspaceAction.PaletteSessionAction,
        is BeginPaletteEdit,
        is DocumentReconciliation,
        WorkspaceAction.CancelQuickSelect,
        WorkspaceAction.DisarmEyedropper,
        is WorkspaceAction.ReferenceUnderlayAction,
        WorkspaceAction.ClearPendingRasterImport,
        -> true

        is WorkspaceAction.SelectTool,
        is WorkspaceAction.BeginGesturePreview,
        is WorkspaceAction.ExtendGesturePreview,
        WorkspaceAction.PrepareGestureCommit,
        WorkspaceAction.OpenQuickSelect,
        is WorkspaceAction.HighlightQuickSelectItem,
        WorkspaceAction.ConfirmQuickSelect,
        is WorkspaceAction.PickPaletteEntryAt,
        is WorkspaceAction.LayerAction,
        is WorkspaceAction.SetPendingRasterImport,
        -> false
    }
