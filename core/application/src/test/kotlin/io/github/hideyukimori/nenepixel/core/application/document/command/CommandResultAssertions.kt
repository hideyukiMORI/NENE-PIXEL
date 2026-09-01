package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.application.document.transition.ChangeSet
import org.junit.jupiter.api.fail

internal object CommandResultAssertions {
    fun applied(result: CommandResult): ChangeSet =
        when (result) {
            is CommandResult.Applied -> result.changeSet
            is CommandResult.Rejected -> fail("Expected Applied but was Rejected(${result.reason}).")
            is CommandResult.Failed -> fail("Expected Applied but was Failed(${result.failure}).")
        }

    fun rejected(result: CommandResult): RejectionReason =
        when (result) {
            is CommandResult.Applied -> fail("Expected Rejected but was Applied(${result.changeSet}).")
            is CommandResult.Rejected -> result.reason
            is CommandResult.Failed -> fail("Expected Rejected but was Failed(${result.failure}).")
        }
}
