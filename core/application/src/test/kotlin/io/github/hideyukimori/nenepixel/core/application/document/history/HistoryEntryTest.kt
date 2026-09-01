package io.github.hideyukimori.nenepixel.core.application.document.history

import io.github.hideyukimori.nenepixel.core.application.document.command.ApplyStrokeCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandGateway
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.red
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.state
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.stroke
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

internal class HistoryEntryTest {
    @Test
    fun `history entry is created only from the original applied change set`() {
        val initial = state(canvas(1, 1))
        val gateway = CommandGateway.create(initial)
        val command =
            ApplyStrokeCommand.create(
                initial.id,
                initial.revision,
                stroke(initial.size, listOf(position(0, 0)), red),
            )
        val applied = gateway.execute(command) as? CommandResult.Applied ?: fail("Expected applied command")

        val entry = HistoryEntry.create(applied)

        assertSame(applied.changeSet, entry.changeSet)
    }
}
