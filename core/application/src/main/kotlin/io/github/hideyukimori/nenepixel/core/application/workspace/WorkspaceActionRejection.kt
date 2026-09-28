package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteDraftRejection
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex

public sealed interface WorkspaceActionRejection {
    public data object PersistenceBusy : WorkspaceActionRejection

    /** A palette draft is open; drawing and tool changes wait until it closes (ADR 0022). */
    public data object PaletteSessionActive : WorkspaceActionRejection

    public data object PreviewAlreadyActive : WorkspaceActionRejection

    public data object NoActivePreview : WorkspaceActionRejection

    public data class PreviewCanvasMismatch internal constructor(
        public val expected: CanvasSize,
        public val actual: CanvasSize,
    ) : WorkspaceActionRejection

    public data class PaletteIndexOutsidePalette internal constructor(
        public val attemptedIndex: PaletteIndex,
        public val entryCount: Int,
    ) : WorkspaceActionRejection

    public data class PreviewPositionOutsideCanvas internal constructor(
        public val canvas: CanvasSize,
        public val position: PixelPosition,
    ) : WorkspaceActionRejection

    public data class PreviewPathAboveSupportedMaximum internal constructor(
        public val attemptedCount: Long,
        public val maximum: Int,
    ) : WorkspaceActionRejection

    public data object PaletteSessionAlreadyActive : WorkspaceActionRejection

    public data object NoPaletteSession : WorkspaceActionRejection

    public data class PaletteDraftRejected internal constructor(
        public val reason: PaletteDraftRejection,
    ) : WorkspaceActionRejection

    /** The eyedropper is armed; the next canvas pointer down reads a slot instead (ADR 0029). */
    public data object EyedropperArmed : WorkspaceActionRejection

    public data object EyedropperNotArmed : WorkspaceActionRejection

    public data object NoQuickSelectMenu : WorkspaceActionRejection

    /** The quick-select menu is open; the canvas does not draw behind it (ADR 0029). */
    public data object QuickSelectMenuOpen : WorkspaceActionRejection

    public data object QuickSelectItemNotInMenu : WorkspaceActionRejection

    public data class PickPositionOutsideCanvas internal constructor(
        public val canvas: CanvasSize,
        public val position: PixelPosition,
    ) : WorkspaceActionRejection

    /** The eyedropper hit an empty cell on the active layer; the selection is unchanged (ADR 0030). */
    public data class PickEmptyCell internal constructor(
        public val position: PixelPosition,
    ) : WorkspaceActionRejection

    /** The workspace's active layer is not in the document (ADR 0030). */
    public data class ActiveLayerNotFound internal constructor(
        public val layerId: LayerId,
    ) : WorkspaceActionRejection

    /** [WorkspaceAction.SelectLayer] named a layer that is not in the document (ADR 0030). */
    public data class LayerNotFound internal constructor(
        public val layerId: LayerId,
    ) : WorkspaceActionRejection

    /** The active layer is hidden; drawing and eyedropper picks wait until it is shown (ADR 0030). */
    public data class ActiveLayerHidden internal constructor(
        public val layerId: LayerId,
    ) : WorkspaceActionRejection
}
