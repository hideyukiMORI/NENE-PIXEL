package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandFailure
import io.github.hideyukimori.nenepixel.core.application.document.command.RejectionReason
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceActionRejection

/** Outcome of applying the open palette draft to the document (ADR 0022). */
public sealed interface PaletteApplyResult {
    /** The draft became one `ReplacePaletteCommand` and the session closed. */
    public data object Applied : PaletteApplyResult

    /** The draft equals its origin; the session closed without a command. */
    public data object NoChange : PaletteApplyResult

    /** The runtime refused before any command; session and preview are unchanged. */
    public data class Rejected internal constructor(
        public val rejection: WorkspaceActionRejection,
    ) : PaletteApplyResult

    /** The command gateway rejected the command; session and preview are unchanged. */
    public data class CommandRejected internal constructor(
        public val reason: RejectionReason,
    ) : PaletteApplyResult

    /** The command gateway failed the command; session and preview are unchanged. */
    public data class CommandFailed internal constructor(
        public val failure: CommandFailure,
    ) : PaletteApplyResult
}
