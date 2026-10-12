package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandFailure
import io.github.hideyukimori.nenepixel.core.application.document.command.RejectionReason
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceActionRejection

/** Outcome of applying the current geometry draft through the ordinary command gateway. */
public sealed interface SelectionApplyResult {
    public data object Applied : SelectionApplyResult

    public data object NoChange : SelectionApplyResult

    public data class Rejected internal constructor(
        public val rejection: WorkspaceActionRejection,
    ) : SelectionApplyResult

    public data class CommandRejected internal constructor(
        public val reason: RejectionReason,
    ) : SelectionApplyResult

    public data class CommandFailed internal constructor(
        public val failure: CommandFailure,
    ) : SelectionApplyResult
}
