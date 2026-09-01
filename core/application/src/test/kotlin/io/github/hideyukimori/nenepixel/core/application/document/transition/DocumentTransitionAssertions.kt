package io.github.hideyukimori.nenepixel.core.application.document.transition

import io.github.hideyukimori.nenepixel.core.application.document.command.RejectionReason
import org.junit.jupiter.api.fail

internal object DocumentTransitionAssertions {
    fun created(result: DocumentTransitionResult): DocumentTransition =
        when (result) {
            is DocumentTransitionResult.Created -> result.transition
            is DocumentTransitionResult.Rejected -> fail("Expected Created but was Rejected(${result.reason}).")
        }

    fun rejected(result: DocumentTransitionResult): RejectionReason =
        when (result) {
            is DocumentTransitionResult.Created -> fail("Expected Rejected but was Created(${result.transition}).")
            is DocumentTransitionResult.Rejected -> result.reason
        }
}
