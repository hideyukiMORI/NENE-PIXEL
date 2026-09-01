package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.black
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.patch
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.red
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.revision
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.state
import io.github.hideyukimori.nenepixel.core.application.document.transition.DocumentTransition
import io.github.hideyukimori.nenepixel.core.application.document.transition.DocumentTransitionResult
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelChange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

internal class CommandResultContractTest {
    @Test
    fun `command result is a closed deterministic applied rejected failed algebra`() {
        val current = state(canvas(1, 1))
        val patch =
            patch(
                current.size,
                current.revision,
                listOf(PixelChange.create(position(0, 0), black, red)),
            )
        val changeSet =
            when (val result = DocumentTransition.create(current, patch)) {
                is DocumentTransitionResult.Created -> result.transition.changeSet
                is DocumentTransitionResult.Rejected -> fail("Test transition was rejected: ${result.reason}")
            }
        val applied = CommandResult.Applied(changeSet)
        val rejected = CommandResult.Rejected(RejectionReason.RevisionMismatch(revision(0L), revision(1L)))

        assertEquals(ResultKind.APPLIED, kindOf(applied))
        assertEquals(ResultKind.REJECTED, kindOf(rejected))
        assertEquals(CommandResult.Applied(changeSet), applied)
        assertEquals(
            CommandResult.Rejected(RejectionReason.RevisionMismatch(revision(0L), revision(1L))),
            rejected,
        )
    }

    private fun kindOf(result: CommandResult): ResultKind =
        when (result) {
            is CommandResult.Applied -> ResultKind.APPLIED
            is CommandResult.Rejected -> ResultKind.REJECTED
            is CommandResult.Failed -> ResultKind.FAILED
        }

    private enum class ResultKind {
        APPLIED,
        REJECTED,
        FAILED,
    }
}
