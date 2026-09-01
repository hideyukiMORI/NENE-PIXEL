package io.github.hideyukimori.nenepixel.core.application.workspace

public sealed interface WorkspaceNoChangeReason {
    public data object ActiveColorAlreadySelected : WorkspaceNoChangeReason

    public data object DuplicatePreviewSample : WorkspaceNoChangeReason
}
