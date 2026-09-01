package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.domain.drawing.Stroke

public sealed interface WorkspaceReductionResult {
    public val nextState: WorkspaceState

    public data class Reduced internal constructor(
        override val nextState: WorkspaceState,
    ) : WorkspaceReductionResult

    public data class Unchanged internal constructor(
        override val nextState: WorkspaceState,
        public val reason: WorkspaceNoChangeReason,
    ) : WorkspaceReductionResult

    public data class CommitPrepared internal constructor(
        override val nextState: WorkspaceState,
        public val stroke: Stroke,
    ) : WorkspaceReductionResult

    public data class Rejected internal constructor(
        override val nextState: WorkspaceState,
        public val rejection: WorkspaceActionRejection,
    ) : WorkspaceReductionResult
}
