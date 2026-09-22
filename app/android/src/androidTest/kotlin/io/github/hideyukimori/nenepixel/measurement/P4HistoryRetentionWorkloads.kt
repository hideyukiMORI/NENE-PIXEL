package io.github.hideyukimori.nenepixel.measurement

import io.github.hideyukimori.nenepixel.core.application.document.command.ApplyStrokeCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandGateway
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.command.RedoCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.RejectionReason
import io.github.hideyukimori.nenepixel.core.application.document.command.ReplacePaletteCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.UndoCommand
import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryAvailability
import io.github.hideyukimori.nenepixel.core.domain.color.ColorChannel
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.drawing.Stroke
import io.github.hideyukimori.nenepixel.core.domain.drawing.StrokeEffect
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteRemap
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelLimits
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue

internal interface P4HistoryRetentionWorkload {
    val gateway: CommandGateway
    val schema: String
    val family: String
    val logicalBytes: Long

    fun populate()

    fun assertExactRoundTrip()

    fun exerciseUndoRedoCycles()

    fun assertOwnerInventory()
}

internal class P4CommonIndexedHistoryWorkload : P4HistoryRetentionWorkload {
    private val values = P4HistoryValues()
    override val gateway: CommandGateway = CommandGateway.create(values.initialDocument())
    override val schema: String = P4_INDEXED_HISTORY_SCHEMA
    override val family: String = P4_CANDIDATE_COMMON_HISTORY
    override val logicalBytes: Long =
        PixelLimits.MAX_RETAINED_CHANGES.toLong() * INDEX_CHANGE_LOGICAL_BYTES +
            PixelLimits.MAX_HISTORY_ENTRIES * TRANSITION_LOGICAL_BYTES

    override fun populate() {
        val path = values.firstChangedPositions()
        repeat(PixelLimits.MAX_HISTORY_ENTRIES) { transition ->
            val target = if (transition % 2 == 0) values.redIndex else values.greenIndex
            values.assertApplied(
                gateway.execute(
                    ApplyStrokeCommand.create(
                        gateway.captureSource(),
                        Stroke.create(values.canvas, path, StrokeEffect.Paint(target)).required(),
                    ),
                ),
            )
        }
        assertEquals(PixelLimits.MAX_HISTORY_ENTRIES.toLong(), gateway.runtimeState.documentState.revision.value)
        assertEquals(HistoryAvailability.UndoAvailable, gateway.runtimeState.historyAvailability)
        values.assertCommonState(gateway.runtimeState.documentState, PixelLimits.MAX_HISTORY_ENTRIES)
    }

    override fun assertExactRoundTrip() {
        values.assertFullUndoRedo(gateway) { document, transition ->
            values.assertCommonState(document, transition)
        }
    }

    override fun exerciseUndoRedoCycles() {
        values.exerciseUndoRedoCycles(gateway)
        values.assertCommonState(gateway.runtimeState.documentState, PixelLimits.MAX_HISTORY_ENTRIES)
    }

    override fun assertOwnerInventory() {
        assertEquals(HistoryAvailability.UndoAvailable, gateway.runtimeState.historyAvailability)
        assertEquals(PixelLimits.MAX_HISTORY_ENTRIES.toLong(), gateway.runtimeState.documentState.revision.value)
    }
}

internal class P4PaletteHistoryWorkload : P4HistoryRetentionWorkload {
    private val values = P4HistoryValues()
    override val gateway: CommandGateway = CommandGateway.create(values.initialPaletteDocument())
    override val schema: String = P4_PALETTE_HISTORY_SCHEMA
    override val family: String = P4_CANDIDATE_PALETTE_HISTORY
    override val logicalBytes: Long = P4_REACHABLE_PALETTE_HISTORY_BYTES

    override fun populate() {
        repeat(PixelLimits.MAX_HISTORY_ENTRIES) { transition ->
            val remap = if (transition % 2 == 0) values.remapAtoB else values.remapBtoA
            values.assertApplied(
                gateway.execute(ReplacePaletteCommand.create(gateway.captureSource(), remap)),
            )
            values.assertPaletteState(gateway.runtimeState.documentState, transition + 1)
        }
        assertEquals(HistoryAvailability.UndoAvailable, gateway.runtimeState.historyAvailability)
    }

    override fun assertExactRoundTrip() {
        values.assertFullUndoRedo(gateway) { document, transition ->
            values.assertPaletteState(document, transition)
        }
    }

    override fun exerciseUndoRedoCycles() {
        values.exerciseUndoRedoCycles(gateway)
        values.assertPaletteState(gateway.runtimeState.documentState, PixelLimits.MAX_HISTORY_ENTRIES)
    }

    override fun assertOwnerInventory() {
        assertEquals(HistoryAvailability.UndoAvailable, gateway.runtimeState.historyAvailability)
        assertEquals(values.paletteA, gateway.runtimeState.documentState.definition)
        assertEquals(PixelLimits.MAX_HISTORY_ENTRIES.toLong(), gateway.runtimeState.documentState.revision.value)
    }
}

private class P4HistoryValues {
    val canvas: CanvasSize =
        CanvasSize.create(
            CanvasWidth.create(PixelLimits.MAX_CANVAS_AXIS).required(),
            CanvasHeight.create(PixelLimits.MAX_CANVAS_AXIS).required(),
        )
    val redIndex: PaletteIndex = paletteIndex(1)
    val greenIndex: PaletteIndex = paletteIndex(2)
    val paletteA: PaletteDefinition = paletteDefinition(colorOffset = 0)
    private val paletteB: PaletteDefinition = paletteDefinition(colorOffset = PALETTE_SIZE)
    val remapAtoB: PaletteRemap =
        PaletteRemap
            .create(paletteA, paletteB, List(PALETTE_SIZE) { slot -> paletteIndex(if (slot <= 1) 2 else slot) })
            .required()
    val remapBtoA: PaletteRemap =
        PaletteRemap
            .create(paletteB, paletteA, List(PALETTE_SIZE) { slot -> paletteIndex(if (slot == 2) 0 else slot) })
            .required()
    private val documentId = DocumentId.create(P4_HISTORY_DOCUMENT_ID).required()

    fun initialDocument(): DocumentState =
        document(
            paletteDefinition(
                listOf(
                    PixelColor.blank,
                    color(CHANNEL_MAX, CHANNEL_MIN, CHANNEL_MIN),
                    color(CHANNEL_MIN, CHANNEL_MAX, CHANNEL_MIN),
                ),
            ),
            Revision.initial(),
            List(canvas.pixelCount.toInt()) { PaletteIndex.first },
        )

    fun initialPaletteDocument(): DocumentState =
        document(
            paletteA,
            Revision.initial(),
            List(canvas.pixelCount.toInt()) { position ->
                if (position < P4_CHANGES_PER_ENTRY) paletteIndex(position % 2) else paletteIndex(UNCHANGED_SLOT)
            },
        )

    fun firstChangedPositions(): List<PixelPosition> =
        List(P4_CHANGES_PER_ENTRY) { position ->
            PixelPosition.create(
                PixelX.create(position % canvas.width.value).required(),
                PixelY.create(position / canvas.width.value).required(),
            )
        }

    fun assertCommonState(
        document: DocumentState,
        transitionCount: Int,
    ) {
        assertEquals(transitionCount.toLong(), document.revision.value)
        val expectedChanged =
            when {
                transitionCount == 0 -> 0
                transitionCount % 2 == 0 -> 2
                else -> 1
            }
        assertIndices(document.snapshot, expectedChanged, 0)
    }

    fun assertPaletteState(
        document: DocumentState,
        transitionCount: Int,
    ) {
        assertEquals(transitionCount.toLong(), document.revision.value)
        assertSame(if (transitionCount % 2 == 0) paletteA else paletteB, document.definition)
        val expectedChanged =
            when {
                transitionCount == 0 -> MIXED_INDEX_SENTINEL
                transitionCount % 2 == 0 -> 0
                else -> 2
            }
        assertIndices(document.snapshot, expectedChanged, UNCHANGED_SLOT)
    }

    fun assertFullUndoRedo(
        gateway: CommandGateway,
        assertState: (DocumentState, Int) -> Unit,
    ) {
        repeat(PixelLimits.MAX_HISTORY_ENTRIES) { offset ->
            executeUndo(gateway)
            assertState(gateway.runtimeState.documentState, PixelLimits.MAX_HISTORY_ENTRIES - offset - 1)
        }
        assertEquals(
            RejectionReason.NoUndoAvailable,
            assertRejected(
                gateway.execute(
                    UndoCommand.create(
                        gateway.runtimeState.documentState.id,
                        gateway.runtimeState.documentState.revision,
                    ),
                ),
            ),
        )
        repeat(PixelLimits.MAX_HISTORY_ENTRIES) { offset ->
            executeRedo(gateway)
            assertState(gateway.runtimeState.documentState, offset + 1)
        }
        assertEquals(
            RejectionReason.NoRedoAvailable,
            assertRejected(
                gateway.execute(
                    RedoCommand.create(
                        gateway.runtimeState.documentState.id,
                        gateway.runtimeState.documentState.revision,
                    ),
                ),
            ),
        )
    }

    fun exerciseUndoRedoCycles(gateway: CommandGateway) {
        repeat(P4_UNDO_REDO_CYCLES) {
            executeUndo(gateway)
            executeRedo(gateway)
        }
    }

    fun assertApplied(result: CommandResult) {
        assertTrue("Expected applied command but was $result", result is CommandResult.Applied)
    }

    private fun executeUndo(gateway: CommandGateway) {
        val current = gateway.runtimeState.documentState
        assertApplied(gateway.execute(UndoCommand.create(current.id, current.revision)))
    }

    private fun executeRedo(gateway: CommandGateway) {
        val current = gateway.runtimeState.documentState
        assertApplied(gateway.execute(RedoCommand.create(current.id, current.revision)))
    }

    private fun assertRejected(result: CommandResult): RejectionReason {
        assertTrue("Expected rejected command but was $result", result is CommandResult.Rejected)
        return (result as CommandResult.Rejected).reason
    }

    private fun assertIndices(
        snapshot: PixelSnapshot,
        changedIndex: Int,
        unchangedIndex: Int,
    ) {
        snapshot.copyPackedIndices().forEachIndexed { position, packed ->
            val expected =
                when {
                    position >= P4_CHANGES_PER_ENTRY -> unchangedIndex
                    changedIndex == MIXED_INDEX_SENTINEL -> position % 2
                    else -> changedIndex
                }
            assertEquals(expected, packed.toInt() and UBYTE_MASK)
        }
    }

    private fun paletteDefinition(colorOffset: Int): PaletteDefinition =
        paletteDefinition(List(PALETTE_SIZE) { slot -> PixelColor.fromPackedRgba8888(colorOffset + slot) })

    private fun paletteDefinition(colors: List<PixelColor>): PaletteDefinition =
        PaletteDefinition.create(Palette.create(colors).required(), PaletteIndex.first).required()

    private fun document(
        definition: PaletteDefinition,
        revision: Revision,
        indices: List<PaletteIndex>,
    ): DocumentState =
        DocumentState
            .create(
                documentId,
                definition,
                PixelSnapshot.create(canvas, revision, indices).required(),
            ).required()

    private fun color(
        red: Int,
        green: Int,
        blue: Int,
    ): PixelColor =
        PixelColor.create(
            ColorChannel.create(red).required(),
            ColorChannel.create(green).required(),
            ColorChannel.create(blue).required(),
            ColorChannel.create(CHANNEL_MAX).required(),
        )

    private fun paletteIndex(value: Int): PaletteIndex = PaletteIndex.create(value).required()

    private companion object {
        const val PALETTE_SIZE: Int = 256
        const val UNCHANGED_SLOT: Int = 3
        const val MIXED_INDEX_SENTINEL: Int = -1
        const val UBYTE_MASK: Int = 0xff
        const val CHANNEL_MIN: Int = 0
        const val CHANNEL_MAX: Int = 255
    }
}

internal fun <T> DomainValueResult<T>.required(): T =
    when (this) {
        is DomainValueResult.Created -> value
        is DomainValueResult.Rejected -> error("Invalid P4 memory fixture: $rejection")
    }

internal const val P4_INDEXED_HISTORY_SCHEMA: String = "nene-pixel-p4-indexed-history-retention-v1"
internal const val P4_PALETTE_HISTORY_SCHEMA: String = "nene-pixel-p4-palette-history-retention-v1"
internal const val P4_LEGACY_IMPORT_SCHEMA: String = "nene-pixel-p4-legacy-import-retention-v1"
internal const val P4_CANDIDATE_COMMON_HISTORY: String = "candidate-common-indexed-history"
internal const val P4_CANDIDATE_PALETTE_HISTORY: String = "candidate-palette-history"
internal const val P4_CANDIDATE_LEGACY_IMPORT: String = "candidate-legacy-import"
internal const val P4_MEMORY_FAMILY_ARGUMENT: String = "nene.p4.memoryFamily"
internal const val P4_MEMORY_RUN_INDEX_ARGUMENT: String = "nene.p4.memoryRunIndex"
internal const val P4_MEMORY_BUILD_COMMIT_ARGUMENT: String = "nene.p4.measurementBuildCommit"
internal const val P4_MEMORY_RUN_COUNT: Int = 5
internal const val P4_CHANGES_PER_ENTRY: Int =
    PixelLimits.MAX_RETAINED_CHANGES / PixelLimits.MAX_HISTORY_ENTRIES
internal const val P4_UNDO_REDO_CYCLES: Int = 10
internal const val INDEX_CHANGE_LOGICAL_BYTES: Long = 6L
internal const val TRANSITION_LOGICAL_BYTES: Long = 32L
internal const val P4_REACHABLE_PALETTE_HISTORY_BYTES: Long = 3_279_360L
private const val P4_HISTORY_DOCUMENT_ID: String = "44444444444444444444444444444444"
