package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.application.document.transition.ChangeSet

public sealed interface CommandResult {
    public data class Applied internal constructor(
        public val changeSet: ChangeSet,
    ) : CommandResult

    public data class Rejected internal constructor(
        public val reason: RejectionReason,
    ) : CommandResult

    public data class Failed internal constructor(
        public val failure: CommandFailure,
    ) : CommandResult
}
