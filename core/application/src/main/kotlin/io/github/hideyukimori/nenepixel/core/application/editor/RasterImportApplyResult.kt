package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandFailure
import io.github.hideyukimori.nenepixel.core.application.document.command.RejectionReason
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceActionRejection

public sealed interface RasterImportApplyResult {
    public data object Applied : RasterImportApplyResult

    public data class Rejected internal constructor(
        public val reason: WorkspaceActionRejection,
    ) : RasterImportApplyResult

    public data class CommandRejected internal constructor(
        public val reason: RejectionReason,
    ) : RasterImportApplyResult

    public data class CommandFailed internal constructor(
        public val failure: CommandFailure,
    ) : RasterImportApplyResult
}
