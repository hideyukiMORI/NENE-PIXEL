package io.github.hideyukimori.nenepixel.core.application.workspace

import org.junit.jupiter.api.fail

internal object WorkspaceReductionAssertions {
    fun reduced(result: WorkspaceReductionResult): WorkspaceState =
        when (result) {
            is WorkspaceReductionResult.Reduced -> result.nextState
            is WorkspaceReductionResult.Unchanged -> fail("Expected Reduced but was Unchanged(${result.reason}).")
            is WorkspaceReductionResult.CommitPrepared -> fail("Expected Reduced but was CommitPrepared.")
            is WorkspaceReductionResult.Rejected -> fail("Expected Reduced but was Rejected(${result.rejection}).")
        }

    fun unchanged(result: WorkspaceReductionResult): WorkspaceReductionResult.Unchanged =
        when (result) {
            is WorkspaceReductionResult.Reduced -> fail("Expected Unchanged but was Reduced.")
            is WorkspaceReductionResult.Unchanged -> result
            is WorkspaceReductionResult.CommitPrepared -> fail("Expected Unchanged but was CommitPrepared.")
            is WorkspaceReductionResult.Rejected -> fail("Expected Unchanged but was Rejected(${result.rejection}).")
        }

    fun prepared(result: WorkspaceReductionResult): WorkspaceReductionResult.CommitPrepared =
        when (result) {
            is WorkspaceReductionResult.Reduced -> {
                fail("Expected CommitPrepared but was Reduced.")
            }

            is WorkspaceReductionResult.Unchanged -> {
                fail(
                    "Expected CommitPrepared but was Unchanged(${result.reason}).",
                )
            }

            is WorkspaceReductionResult.CommitPrepared -> {
                result
            }

            is WorkspaceReductionResult.Rejected -> {
                fail(
                    "Expected CommitPrepared but was Rejected(${result.rejection}).",
                )
            }
        }

    fun rejected(result: WorkspaceReductionResult): WorkspaceReductionResult.Rejected =
        when (result) {
            is WorkspaceReductionResult.Reduced -> fail("Expected Rejected but was Reduced.")
            is WorkspaceReductionResult.Unchanged -> fail("Expected Rejected but was Unchanged(${result.reason}).")
            is WorkspaceReductionResult.CommitPrepared -> fail("Expected Rejected but was CommitPrepared.")
            is WorkspaceReductionResult.Rejected -> result
        }
}
