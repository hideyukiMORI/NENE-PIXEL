package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResultAssertions.applied
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResultAssertions.rejected
import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryAvailability
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerIndexChanges
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.layer.Layer
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerName
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteRemap
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class LayeredPaletteHistoryRetentionContractTest {
    @Test
    fun `full layered remap retains one dense transition per layer and exact inverse`() {
        val fixture = LayeredPaletteHistoryFixture()
        val initial = fixture.initialDocument()
        val gateway = CommandGateway.create(initial)

        val forward = fixture.applyNext(gateway, transitionIndex = 0)
        val changed = gateway.runtimeState.documentState
        val inverse = forward.inverse()

        assertEquals(LAYER_COUNT, forward.layerChanges.size)
        assertEquals(initial.layers.map(Layer::id), forward.layerChanges.map { it.layerId })
        forward.layerChanges.forEachIndexed { ordinal, change ->
            val dense = assertInstanceOf(LayerIndexChanges.Dense::class.java, change.changes)
            val reverse = assertInstanceOf(LayerIndexChanges.Dense::class.java, inverse.layerChanges[ordinal].changes)
            assertSame(initial.layers[ordinal].snapshot, dense.before)
            assertSame(changed.layers[ordinal].snapshot, dense.after)
            assertSame(dense.before, reverse.after)
            assertSame(dense.after, reverse.before)
        }
        assertEquals(EXPECTED_PIXEL_BYTES, forward.layerChanges.sumOf { it.changes.retainedByteCount })
        assertEquals(EXPECTED_PIXEL_BYTES + PALETTE_AND_TRANSITION_BYTES, forward.retainedByteCount)
        assertEquals(0, forward.retainedChangeCount)
        assertEquals(1, gateway.runtimeState.historyEntryCount)
        assertEquals(0, gateway.runtimeState.retainedHistoryChangeCount)
        assertEquals(EXPECTED_TOTAL_BYTES, gateway.runtimeState.retainedHistoryByteCount)
        fixture.assertRemapped(changed, expectedRevision = 1, expectedIndex = 2)

        applied(gateway.execute(UndoCommand.create(changed.id, changed.revision)))
        assertEquals(initial, gateway.runtimeState.documentState)
        assertEquals(HistoryAvailability.RedoAvailable, gateway.runtimeState.historyAvailability)
        applied(gateway.execute(RedoCommand.create(initial.id, initial.revision)))
        assertEquals(changed, gateway.runtimeState.documentState)
        assertEquals(HistoryAvailability.UndoAvailable, gateway.runtimeState.historyAvailability)
    }

    @Test
    fun `four full layered remaps evict only the oldest entry at eight mebibytes`() {
        val fixture = LayeredPaletteHistoryFixture()
        val gateway = CommandGateway.create(fixture.initialDocument())
        val states = mutableListOf(gateway.runtimeState.documentState)

        repeat(4) { transitionIndex ->
            val transition = fixture.applyNext(gateway, transitionIndex)
            assertEquals(EXPECTED_TOTAL_BYTES, transition.retainedByteCount)
            states += gateway.runtimeState.documentState
            fixture.assertRemapped(
                states.last(),
                expectedRevision = transitionIndex + 1,
                expectedIndex = if (transitionIndex % 2 == 0) 2 else 0,
            )
            val retainedEntries = minOf(transitionIndex + 1, 3)
            assertEquals(retainedEntries, gateway.runtimeState.historyEntryCount)
            assertEquals(retainedEntries * EXPECTED_TOTAL_BYTES, gateway.runtimeState.retainedHistoryByteCount)
            assertEquals(0, gateway.runtimeState.retainedHistoryChangeCount)
        }

        assertEquals(THREE_ENTRY_BYTES, gateway.runtimeState.retainedHistoryByteCount)
        for (revision in 4 downTo 2) {
            val current = gateway.runtimeState.documentState
            applied(gateway.execute(UndoCommand.create(current.id, current.revision)))
            assertEquals(states[revision - 1], gateway.runtimeState.documentState)
        }
        assertEquals(HistoryAvailability.RedoAvailable, gateway.runtimeState.historyAvailability)
        val oldestRetained = gateway.runtimeState.documentState
        assertEquals(
            RejectionReason.NoUndoAvailable,
            rejected(gateway.execute(UndoCommand.create(oldestRetained.id, oldestRetained.revision))),
        )
        assertEquals(states[1], gateway.runtimeState.documentState)

        for (revision in 2..4) {
            val current = gateway.runtimeState.documentState
            applied(gateway.execute(RedoCommand.create(current.id, current.revision)))
            assertEquals(states[revision], gateway.runtimeState.documentState)
        }
        assertEquals(HistoryAvailability.UndoAvailable, gateway.runtimeState.historyAvailability)
        assertEquals(3, gateway.runtimeState.historyEntryCount)
        assertEquals(THREE_ENTRY_BYTES, gateway.runtimeState.retainedHistoryByteCount)
    }

    private companion object {
        const val LAYER_COUNT: Int = 16
        const val EXPECTED_PIXEL_BYTES: Long = 2_359_296L
        const val PALETTE_AND_TRANSITION_BYTES: Long = 32L + 4L * (256L + 256L) + 8L
        const val EXPECTED_TOTAL_BYTES: Long = 2_361_384L
        const val THREE_ENTRY_BYTES: Long = 7_084_152L
    }
}

private class LayeredPaletteHistoryFixture {
    private val size = CanvasSize.create(CanvasWidth.create(256).value(), CanvasHeight.create(256).value())
    private val paletteA = definition(0)
    private val paletteB = definition(256)
    private val remapAtoB = remap(paletteA, paletteB) { source -> if (source < 2) 2 else source }
    private val remapBtoA = remap(paletteB, paletteA) { source -> if (source == 2) 0 else source }

    fun initialDocument(): DocumentState =
        DocumentState
            .createLayered(
                DocumentId.create("14514514514514514514514514514514").value(),
                Revision.initial(),
                paletteA,
                List(16) { ordinal ->
                    val pixels =
                        ByteArray(size.pixelCount.toInt()) { position ->
                            (((position shr ordinal) xor ordinal) and 1).toByte()
                        }
                    Layer.create(
                        LayerId.create(21 + ordinal * 2).value(),
                        LayerName.create("layer-$ordinal").value(),
                        if (ordinal % 2 == 0) LayerVisibility.Visible else LayerVisibility.Hidden,
                        PixelSnapshot.createPackedIndices(size, pixels).value(),
                    )
                },
            ).value()

    fun applyNext(
        gateway: CommandGateway,
        transitionIndex: Int,
    ) = applied(
        gateway.execute(
            ReplacePaletteCommand.create(
                gateway.captureSource(),
                if (transitionIndex % 2 == 0) remapAtoB else remapBtoA,
            ),
        ),
    )

    fun assertRemapped(
        document: DocumentState,
        expectedRevision: Int,
        expectedIndex: Int,
    ) {
        assertEquals(expectedRevision.toLong(), document.revision.value)
        assertEquals(if (expectedRevision % 2 == 0) paletteA else paletteB, document.definition)
        assertEquals(16, document.layers.size)
        document.layers.forEachIndexed { ordinal, layer ->
            assertEquals(LayerId.create(21 + ordinal * 2).value(), layer.id)
            assertEquals(LayerName.create("layer-$ordinal").value(), layer.name)
            assertEquals(if (ordinal % 2 == 0) LayerVisibility.Visible else LayerVisibility.Hidden, layer.visibility)
            assertEquals(
                size.pixelCount.toInt(),
                layer.snapshot.copyPackedIndices().count { (it.toInt() and 0xff) == expectedIndex },
            )
            assertEquals(size.pixelCount.toInt() / 8, layer.snapshot.copyCoverage().count { it == 0xff.toByte() })
        }
    }

    private fun definition(colorOffset: Int): PaletteDefinition =
        PaletteDefinition
            .create(
                Palette.create(List(256) { slot -> PixelColor.fromPackedRgba8888(colorOffset + slot) }).value(),
                PaletteIndex.first,
            ).value()

    private fun remap(
        before: PaletteDefinition,
        after: PaletteDefinition,
        destination: (Int) -> Int,
    ): PaletteRemap =
        PaletteRemap
            .create(before, after, List(256) { source -> PaletteIndex.create(destination(source)).value() })
            .value()
}

private fun <T> DomainValueResult<T>.value(): T =
    when (this) {
        is DomainValueResult.Created -> value
        is DomainValueResult.Rejected -> error("Invalid layered history fixture: $rejection")
    }
