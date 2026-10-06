package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResultAssertions.applied
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResultAssertions.rejected
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.DocumentIdentity
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.black
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.cellAt
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.definition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.green
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.paletteIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.red
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.revision
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.snapshot
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.state
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerIndexChanges
import io.github.hideyukimori.nenepixel.core.application.document.transition.PaletteTransition
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.layer.Layer
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerName
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteRemap
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelCell
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelLimits
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

internal class ReplacePaletteCommandTest {
    @Test
    fun `equal RGBA in different slots remains an effective exact index change`() {
        val definition = definition(paletteIndex(0), black, red, red)
        val initial =
            state(
                canvas(3, 1),
                indices = listOf(paletteIndex(1), paletteIndex(0), paletteIndex(0)),
                definition = definition,
            )
        val gateway = CommandGateway.create(initial)
        val result =
            applied(gateway.execute(command(gateway, remap(definition, definition, 0, 2, 2))))

        assertInstanceOf(LayerIndexChanges.Sparse::class.java, result.layerChanges.single().changes)
        assertEquals(PaletteTransition.Unchanged, result.paletteTransition)
        assertEquals(
            PixelCell.Covered(paletteIndex(2)),
            cellAt(
                gateway.runtimeState.documentState.layers
                    .single()
                    .snapshot,
                position(0, 0),
            ),
        )
    }

    @Test
    fun `palette only transition advances revision and invalidates full canvas without empty patch`() {
        val source = definition(paletteIndex(0), black, red)
        val target = definition(paletteIndex(0), black, green)
        val initial = state(canvas(2, 1), indices = listOf(paletteIndex(0), paletteIndex(1)), definition = source)
        val gateway = CommandGateway.create(initial)
        val result = applied(gateway.execute(command(gateway, remap(source, target, 0, 1))))

        assertEquals(emptyList<Any>(), result.layerChanges)
        assertInstanceOf(PaletteTransition.Changed::class.java, result.paletteTransition)
        assertEquals(initial.size, result.renderInvalidation.size)
        assertEquals(1L, gateway.runtimeState.documentState.revision.value)
        assertEquals(target, gateway.runtimeState.documentState.definition)
        assertEquals(
            initial.layers
                .single()
                .snapshot
                .copyPackedIndices()
                .toList(),
            gateway.runtimeState.documentState.layers
                .single()
                .snapshot
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
        assertEquals(
            listOf(1, 1),
            changed.layers
                .single()
                .snapshot
                .copyPackedIndices()
                .map { it.toInt() and 0xff },
        )

        applied(gateway.execute(UndoCommand.create(changed.id, changed.revision)))
        assertEquals(initial, gateway.runtimeState.documentState)
        applied(gateway.execute(RedoCommand.create(initial.id, initial.revision)))
        assertEquals(changed, gateway.runtimeState.documentState)
    }

    @Test
    fun `full canvas remap is retained dense and round trips through undo redo`() {
        val source = definition(paletteIndex(0), black, red)
        val target = definition(paletteIndex(0), red, black)
        val size = canvas(PixelLimits.MAX_CANVAS_AXIS, PixelLimits.MAX_CANVAS_AXIS)
        val initial =
            state(size, indices = List(PixelLimits.MAX_CANVAS_PIXELS) { paletteIndex(0) }, definition = source)
        val gateway = CommandGateway.create(initial)
        val result = applied(gateway.execute(command(gateway, remap(source, target, 1, 0))))
        val changed = gateway.runtimeState.documentState

        assertInstanceOf(LayerIndexChanges.Dense::class.java, result.layerChanges.single().changes)
        assertEquals(0, result.retainedChangeCount)
        assertEquals(32L + 147_456L + (4L * (2 + 2) + 8L), result.retainedByteCount)
        assertEquals(size, result.renderInvalidation.size)
        assertEquals(PixelCell.Covered(paletteIndex(1)), cellAt(changed.layers.single().snapshot, position(255, 255)))
        applied(gateway.execute(UndoCommand.create(changed.id, changed.revision)))
        assertEquals(initial, gateway.runtimeState.documentState)
        applied(gateway.execute(RedoCommand.create(initial.id, initial.revision)))
        assertEquals(changed, gateway.runtimeState.documentState)
    }

    @Test
    fun `remap records every changed layer including hidden ones`() {
        val source = definition(paletteIndex(0), black, red)
        val target = definition(paletteIndex(0), red, black)
        val size = canvas(2, 1)
        val hiddenId = LayerId.create(2).value()
        val initial =
            DocumentState
                .createLayered(
                    state(size).id,
                    state(size).revision,
                    source,
                    listOf(
                        Layer.create(LayerId.first(), LayerName.empty, LayerVisibility.Visible, snapshot(size)),
                        Layer.create(hiddenId, LayerName.empty, LayerVisibility.Hidden, snapshot(size)),
                    ),
                ).value()
        val gateway = CommandGateway.create(initial)
        val result = applied(gateway.execute(command(gateway, remap(source, target, 1, 0))))

        assertEquals(listOf(LayerId.first(), hiddenId), result.layerChanges.map { it.layerId })
        val changed = gateway.runtimeState.documentState
        applied(gateway.execute(UndoCommand.create(changed.id, changed.revision)))
        assertEquals(initial, gateway.runtimeState.documentState)
    }

    @Test
    fun `effective remap at maximum document revision rejects atomically`() {
        val definition = definition(paletteIndex(0), black, red)
        val initial =
            state(
                canvas(2, 1),
                indices = listOf(paletteIndex(0), paletteIndex(1)),
                definition = definition,
                identity = DocumentIdentity(revision = revision(Long.MAX_VALUE)),
            )
        val gateway = CommandGateway.create(initial)

        assertEquals(
            RejectionReason.RevisionOverflow,
            rejected(gateway.execute(command(gateway, remap(definition, definition, 1, 0)))),
        )
        assertEquals(initial, gateway.runtimeState.documentState)
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
