package io.github.hideyukimori.nenepixel.core.application.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.ReplacePaletteCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.UndoCommand
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.black
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.blackIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.defaultDefinition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.definition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.green
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.paletteIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.red
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteRemap
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

internal class PaletteSelectionPolicyTest {
    @Test
    fun `replacement maps recent slots through the remap in order`() {
        val target = definition(blackIndex, green, black, red)
        val command = replacement(target, indices(1, 2, 0))

        val recent = PaletteSelectionPolicy.recentAfterApplied(indices(2, 0), target, command)

        assertEquals(indices(0, 1), recent)
    }

    @Test
    fun `replacement keeps the first slot when two map to one destination`() {
        val target = definition(blackIndex, black, red)
        val command = replacement(target, indices(0, 1, 0))

        val recent = PaletteSelectionPolicy.recentAfterApplied(indices(2, 1, 0), target, command)

        assertEquals(indices(0, 1), recent)
    }

    @Test
    fun `other palette change drops slots outside the palette without defaulting`() {
        val shrunk = definition(paletteIndex(1), black, red)
        val undo = UndoCommand.create(DocumentId.create("1".repeat(32)).value(), Revision.initial())

        val recent = PaletteSelectionPolicy.recentAfterApplied(indices(2, 0, 1), shrunk, undo)

        assertEquals(indices(0, 1), recent)
    }

    private fun replacement(
        target: PaletteDefinition,
        destinations: List<PaletteIndex>,
    ): ReplacePaletteCommand {
        val runtime =
            EditorRuntime.create(canvas(1, 1), defaultDefinition) { DocumentId.create("2".repeat(32)).value() }
        val remap = PaletteRemap.create(defaultDefinition, target, destinations).value()
        return ReplacePaletteCommand.create(runtime.captureSource(), remap)
    }

    private fun indices(vararg values: Int): List<PaletteIndex> = values.map(::paletteIndex)
}

private fun <T> DomainValueResult<T>.value(): T =
    when (this) {
        is DomainValueResult.Created -> value
        is DomainValueResult.Rejected -> fail("Policy fixture was rejected: $rejection")
    }
