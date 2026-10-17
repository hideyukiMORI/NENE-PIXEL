package io.github.hideyukimori.nenepixel

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandGateway
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.command.SetFrameDurationCommand
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameDuration
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.Assert.assertTrue

internal enum class RecoveryProcessCase(
    val argument: String,
    val expectedGeneration: Long,
) {
    BeforePublication("before-publication", 2L),
    DuringPublication("during-publication", 2L),
    VerifiedCompletion("verified-completion", 3L),
    ;

    /** Reconstructs the oracle through the real command gateway, without storage or a second runtime. */
    fun expectedDocument(): DocumentState {
        val gateway = CommandGateway.create(RecoveryFaultDocument.create())
        applyDuration(gateway, 0, 110)
        if (this == VerifiedCompletion) applyDuration(gateway, 1, 260)
        return gateway.runtimeState.documentState
    }

    private fun applyDuration(
        gateway: CommandGateway,
        frame: Int,
        milliseconds: Int,
    ) {
        val duration =
            when (val result = FrameDuration.create(milliseconds)) {
                is DomainValueResult.Created -> result.value
                is DomainValueResult.Rejected -> error("Invalid synthetic duration")
            }
        val command =
            SetFrameDurationCommand.create(
                gateway.captureSource(),
                gateway.runtimeState.documentState.frames[frame].id,
                duration,
            )
        assertTrue(gateway.execute(command) is CommandResult.Applied)
    }
}
