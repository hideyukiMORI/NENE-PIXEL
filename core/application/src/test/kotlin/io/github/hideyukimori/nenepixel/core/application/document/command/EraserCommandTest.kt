package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResultAssertions.applied
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResultAssertions.rejected
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.cellAt
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.defaultDefinition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.defaultDocumentId
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.eraserStroke
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.greenIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.paletteIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.redIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.state
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.stroke
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.drawing.Stroke
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelCell
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

internal class EraserCommandTest {
    @Test
    fun `eraser turns covered cells Empty and undo restores the original covered cells`() {
        val initial = state(canvas(3, 1), indices = listOf(redIndex, greenIndex, redIndex))
        val gateway = CommandGateway.create(initial)

        applied(gateway.execute(command(gateway, eraserStroke(initial.size, listOf(position(0, 0), position(1, 0))))))
        val erased = gateway.runtimeState.documentState

        assertEquals(PixelCell.Empty, cellAt(erased.snapshot, position(0, 0)))
        assertEquals(PixelCell.Empty, cellAt(erased.snapshot, position(1, 0)))
        assertEquals(PixelCell.Covered(redIndex), cellAt(erased.snapshot, position(2, 0)))

        applied(gateway.execute(UndoCommand.create(erased.id, erased.revision)))
        val restored = gateway.runtimeState.documentState

        assertEquals(PixelCell.Covered(redIndex), cellAt(restored.snapshot, position(0, 0)))
        assertEquals(PixelCell.Covered(greenIndex), cellAt(restored.snapshot, position(1, 0)))
        assertEquals(initial, restored)
    }

    @Test
    fun `erasing Empty cells is no effective change and leaves the document untouched`() {
        val initial = emptyState()
        val gateway = CommandGateway.create(initial)

        val rejection = rejected(gateway.execute(command(gateway, eraserStroke(initial.size, listOf(position(0, 0))))))

        assertEquals(RejectionReason.NoEffectiveChange, rejection)
        assertEquals(initial, gateway.runtimeState.documentState)
    }

    @Test
    fun `only paint is checked against the palette and erase reaches rasterization without an index`() {
        val initial = state(canvas(2, 1), indices = listOf(redIndex, greenIndex))
        val gateway = CommandGateway.create(initial)
        val outsidePalette = paletteIndex(DEFAULT_PALETTE_SIZE)

        assertInstanceOf(
            RejectionReason.InvalidIndexedValue::class.java,
            rejected(gateway.execute(command(gateway, stroke(initial.size, listOf(position(0, 0)), outsidePalette)))),
        )
        applied(gateway.execute(command(gateway, eraserStroke(initial.size, listOf(position(0, 0))))))

        assertEquals(PixelCell.Empty, cellAt(gateway.runtimeState.documentState.snapshot, position(0, 0)))
    }

    private fun emptyState(): DocumentState =
        when (
            val result =
                DocumentState.create(
                    defaultDocumentId,
                    Revision.initial(),
                    defaultDefinition,
                    PixelSnapshot.createEmpty(canvas(2, 1)),
                )
        ) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> fail("Test document was rejected: ${result.rejection}")
        }

    private fun command(
        gateway: CommandGateway,
        stroke: Stroke,
    ): ApplyStrokeCommand = ApplyStrokeCommand.create(gateway.captureSource(), stroke)

    private companion object {
        /** `defaultDefinition` holds black, red and green, so slot 3 is outside the palette. */
        const val DEFAULT_PALETTE_SIZE: Int = 3
    }
}
