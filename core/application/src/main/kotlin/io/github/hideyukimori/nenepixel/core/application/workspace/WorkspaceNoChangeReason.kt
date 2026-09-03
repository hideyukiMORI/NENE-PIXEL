package io.github.hideyukimori.nenepixel.core.application.workspace

public sealed interface WorkspaceNoChangeReason {
    public data object ActiveColorAlreadySelected : WorkspaceNoChangeReason

    public data object ActiveToolAlreadySelected : WorkspaceNoChangeReason

    public data object DuplicatePreviewSample : WorkspaceNoChangeReason

    public data object ViewportAlreadySet : WorkspaceNoChangeReason
}
