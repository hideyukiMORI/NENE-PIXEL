package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResultAssertions.applied
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.black
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.definition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.green
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.indexAt
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.paletteIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.red
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.state
import io.github.hideyukimori.nenepixel.core.application.document.transition.IndexChanges
import io.github.hideyukimori.nenepixel.core.application.document.transition.PaletteTransition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteRemap
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

internal class ReplacePaletteCommandTest {
    @Test
    fun `equal RGBA in different slots remains an effective exact index change`() {
        val definition = definition(paletteIndex(0), black, red, red)
        val initial = state(canvas(1, 1), indices = listOf(paletteIndex(1)), definition = definition)
        val gateway = CommandGateway.create(initial)
        val result =
            applied(gateway.execute(command(gateway, remap(definition, definition, 0, 2, 2))))

        assertInstanceOf(IndexChanges.Changed::class.java, result.indexChanges)
        assertEquals(PaletteTransition.Unchanged, result.paletteTransition)
        assertEquals(paletteIndex(2), indexAt(gateway.runtimeState.documentState.snapshot, position(0, 0)))
    }

    @Test
    fun `palette only transition advances revision and invalidates full canvas without empty patch`() {
        val source = definition(paletteIndex(0), black, red)
        val target = definition(paletteIndex(0), black, green)
        val initial = state(canvas(2, 1), indices = listOf(paletteIndex(0), paletteIndex(1)), definition = source)
        val gateway = CommandGateway.create(initial)
        val result = applied(gateway.execute(command(gateway, remap(source, target, 0, 1))))

        assertInstanceOf(IndexChanges.NoIndexChanges::class.java, result.indexChanges)
        assertInstanceOf(PaletteTransition.Changed::class.java, result.paletteTransition)
        assertEquals(initial.size, result.renderInvalidation.size)
        assertEquals(1L, gateway.runtimeState.documentState.revision.value)
        assertEquals(target, gateway.runtimeState.documentState.definition)
        assertEquals(
            initial.snapshot.copyPackedIndices().toList(),
            gateway.runtimeState.documentState.snapshot
                .copyPackedIndices()
                .toList(),
        )
    }

    @Test
    fun `many to one remap restores exact source slots and definitions through undo redo`() {
        val source = definition(paletteIndex(0), black, red, green)
        val target = definition(paletteIndex(0), black, red)
        val initial = state(canvas(2, 1), indices = listOf(paletteIndex(1), paletteIndex(2)), definition = source)
        val gateway = CommandGateway.create(initial)
        applied(gateway.execute(command(gateway, remap(source, target, 0, 1, 1))))
        val changed = gateway.runtimeState.documentState
        assertEquals(listOf(1, 1), changed.snapshot.copyPackedIndices().map { it.toInt() and 0xff })

        applied(gateway.execute(UndoCommand.create(changed.id, changed.revision)))
        assertEquals(initial, gateway.runtimeState.documentState)
        applied(gateway.execute(RedoCommand.create(initial.id, initial.revision)))
        assertEquals(changed, gateway.runtimeState.documentState)
    }

    private fun command(
        gateway: CommandGateway,
        remap: PaletteRemap,
    ): ReplacePaletteCommand = ReplacePaletteCommand.create(gateway.captureSource(), remap)

    private fun remap(
        source: PaletteDefinition,
        target: PaletteDefinition,
        vararg destinations: Int,
    ): PaletteRemap = PaletteRemap.create(source, target, destinations.map(::paletteIndex)).value()

    private fun <T> DomainValueResult<T>.value(): T =
        when (this) {
            is DomainValueResult.Created -> value
            is DomainValueResult.Rejected -> fail("Value rejected: $rejection")
        }
}
