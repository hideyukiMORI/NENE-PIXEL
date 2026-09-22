package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResultAssertions.applied
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteRemap
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelLimits
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class PaletteHistoryRetentionContractTest {
    @Test
    fun `reachable palette history maximum retains exact many to one inverse and logical bytes`() {
        val fixture = PaletteHistoryFixture()
        val initial = fixture.initialDocument()
        val gateway = CommandGateway.create(initial)

        fixture.applyNext(gateway, transitionIndex = 0)
        val firstForward = gateway.runtimeState.documentState
        fixture.assertState(firstForward, transitionCount = 1)
        applied(gateway.execute(UndoCommand.create(firstForward.id, firstForward.revision)))
        assertEquals(initial, gateway.runtimeState.documentState)
        applied(gateway.execute(RedoCommand.create(initial.id, initial.revision)))
        assertEquals(firstForward, gateway.runtimeState.documentState)

        repeat(PixelLimits.MAX_HISTORY_ENTRIES - 1) { offset ->
            fixture.applyNext(gateway, transitionIndex = offset + 1)
        }

        assertEquals(PixelLimits.MAX_HISTORY_ENTRIES, gateway.runtimeState.historyEntryCount)
        assertEquals(PixelLimits.MAX_RETAINED_CHANGES, gateway.runtimeState.retainedHistoryChangeCount)
        assertEquals(EXPECTED_REACHABLE_LOGICAL_BYTES, gateway.runtimeState.retainedHistoryByteCount)
        fixture.assertState(gateway.runtimeState.documentState, PixelLimits.MAX_HISTORY_ENTRIES)
    }

    private companion object {
        const val EXPECTED_REACHABLE_LOGICAL_BYTES: Long = 3_279_360L
    }
}

private class PaletteHistoryFixture {
    private val size =
        CanvasSize.create(
            CanvasWidth.create(PixelLimits.MAX_CANVAS_AXIS).value(),
            CanvasHeight.create(PixelLimits.MAX_CANVAS_AXIS).value(),
        )
    private val paletteA = definition(colorOffset = 0)
    private val paletteB = definition(colorOffset = 256)
    private val remapAtoB =
        PaletteRemap
            .create(
                paletteA,
                paletteB,
                List(PALETTE_SIZE) { slot -> index(if (slot <= 1) 2 else slot) },
            ).value()
    private val remapBtoA =
        PaletteRemap
            .create(
                paletteB,
                paletteA,
                List(PALETTE_SIZE) { slot -> index(if (slot == 2) 0 else slot) },
            ).value()

    fun initialDocument(): DocumentState =
        document(
            paletteA,
            Revision.initial(),
            List(size.pixelCount.toInt()) { position ->
                if (position < CHANGES_PER_ENTRY) index(position % 2) else index(UNCHANGED_SLOT)
            },
        )

    fun applyNext(
        gateway: CommandGateway,
        transitionIndex: Int,
    ) {
        val remap = if (transitionIndex % 2 == 0) remapAtoB else remapBtoA
        applied(gateway.execute(ReplacePaletteCommand.create(gateway.captureSource(), remap)))
    }

    fun assertState(
        document: DocumentState,
        transitionCount: Int,
    ) {
        assertEquals(transitionCount.toLong(), document.revision.value)
        assertEquals(if (transitionCount % 2 == 0) paletteA else paletteB, document.definition)
        val expectedChangedIndex = if (transitionCount % 2 == 0) 0 else 2
        document.snapshot.copyPackedIndices().forEachIndexed { position, packed ->
            val expected =
                if (position < CHANGES_PER_ENTRY) {
                    expectedChangedIndex
                } else {
                    UNCHANGED_SLOT
                }
            assertEquals(expected, packed.toInt() and UBYTE_MASK)
        }
    }

    private fun definition(colorOffset: Int): PaletteDefinition =
        PaletteDefinition
            .create(
                Palette
                    .create(
                        List(PALETTE_SIZE) { slot ->
                            PixelColor.fromPackedRgba8888(colorOffset + slot)
                        },
                    ).value(),
                PaletteIndex.first,
            ).value()

    private fun document(
        definition: PaletteDefinition,
        revision: Revision,
        indices: List<PaletteIndex>,
    ): DocumentState =
        DocumentState
            .create(
                DocumentId.create(DOCUMENT_ID).value(),
                definition,
                PixelSnapshot.create(size, revision, indices).value(),
            ).value()

    private fun index(value: Int): PaletteIndex = PaletteIndex.create(value).value()

    private companion object {
        const val PALETTE_SIZE: Int = 256
        const val CHANGES_PER_ENTRY: Int =
            PixelLimits.MAX_RETAINED_CHANGES / PixelLimits.MAX_HISTORY_ENTRIES
        const val UNCHANGED_SLOT: Int = 3
        const val UBYTE_MASK: Int = 0xff
        const val DOCUMENT_ID: String = "77777777777777777777777777777777"
    }
}

private fun <T> DomainValueResult<T>.value(): T =
    when (this) {
        is DomainValueResult.Created -> value
        is DomainValueResult.Rejected -> error("Invalid palette-history retention fixture: $rejection")
    }
