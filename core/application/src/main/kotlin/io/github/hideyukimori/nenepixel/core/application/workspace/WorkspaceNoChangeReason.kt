package io.github.hideyukimori.nenepixel.core.application.workspace

public sealed interface WorkspaceNoChangeReason {
    public data object AppearanceAlreadySet : WorkspaceNoChangeReason

    public data object ActualSizeWindowAlreadySet : WorkspaceNoChangeReason

    public data object ActivePaletteEntryAlreadySelected : WorkspaceNoChangeReason

    public data object ActiveToolAlreadySelected : WorkspaceNoChangeReason

    public data object DuplicatePreviewSample : WorkspaceNoChangeReason

    public data object ViewportAlreadySet : WorkspaceNoChangeReason

    public data object PaletteDraftUnchanged : WorkspaceNoChangeReason

    public data object QuickSelectMenuAlreadyOpen : WorkspaceNoChangeReason

    public data object QuickSelectMenuAlreadyClosed : WorkspaceNoChangeReason

    public data object QuickSelectHighlightUnchanged : WorkspaceNoChangeReason

    public data object EyedropperAlreadyIdle : WorkspaceNoChangeReason

    public data object ActiveLayerAlreadySelected : WorkspaceNoChangeReason

    public data object ReferenceUnderlayAlreadySet : WorkspaceNoChangeReason

    public data object NoReferenceUnderlay : WorkspaceNoChangeReason

    public data object PendingRasterImportAlreadySet : WorkspaceNoChangeReason

    public data object NoPendingRasterImport : WorkspaceNoChangeReason
}
