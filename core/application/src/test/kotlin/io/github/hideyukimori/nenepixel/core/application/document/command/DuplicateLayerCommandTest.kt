package io.github.hideyukimori.nenepixel.core.application.document.command

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResultAssertions.applied
import io.github.hideyukimori.nenepixel.core.application.document.command.LayerCommandTestValues.applyUndoRedo
import io.github.hideyukimori.nenepixel.core.application.document.command.LayerCommandTestValues.ids
import io.github.hideyukimori.nenepixel.core.application.document.command.LayerCommandTestValues.redo
import io.github.hideyukimori.nenepixel.core.application.document.command.LayerCommandTestValues.undo
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.black
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.blackIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.cellAt
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.defaultDocumentId
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.definition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.green
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.red
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.redIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.stroke
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.assertFullCanvasInvalidation
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layer
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layerId
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layeredState
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.value
import io.github.hideyukimori.nenepixel.core.application.document.transition.singleFrameLayers
import io.github.hideyukimori.nenepixel.core.application.document.transition.singleFrameSnapshot
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelCell
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/** Issue #208: exact immutable duplication through the sole layer structure/history path. */
internal class DuplicateLayerCommandTest {
    @Test
    fun `hidden named layer keeps exact covered alpha-zero and Empty cells with one Added transition`() {
        val original = sourceDocument()
        val gateway = CommandGateway.create(original)
        val source = original.singleFrameLayers()[1]
        val change = applied(gateway.execute(DuplicateLayerCommand.create(gateway.captureSource(), source.id)))
        val after = gateway.runtimeState.documentState
        val duplicate = after.singleFrameLayers()[2]

        assertEquals(listOf(1, 7, 8, 4), ids(after))
        assertEquals(source.name, duplicate.name)
        assertEquals(LayerVisibility.Hidden, duplicate.visibility)
        assertSame(source.snapshot, duplicate.snapshot)
        assertSame(source, after.singleFrameLayers()[1])
        assertSame(original.definition, after.definition)
        assertEquals(cellAt(source.snapshot, position(0, 0)), cellAt(duplicate.snapshot, position(0, 0)))
        assertEquals(PixelCell.Empty, cellAt(duplicate.snapshot, position(2, 0)))
        assertEquals(1, gateway.runtimeState.historyEntryCount)
        assertEquals(0, gateway.runtimeState.retainedHistoryChangeCount)
        assertEquals(
            32L + 4L + 3L + 1L +
                source.name.value
                    .encodeToByteArray()
                    .size,
            change.retainedByteCount,
        )
        assertFullCanvasInvalidation(change)
        undo(gateway)
        assertEquals(original, gateway.runtimeState.documentState)
        redo(gateway)
        assertEquals(after, gateway.runtimeState.documentState)
        assertSame(
            source.snapshot,
            gateway.runtimeState.documentState
                .singleFrameLayers()[2]
                .snapshot,
        )
    }

    @Test
    fun `unnamed empty layer duplication remains effective and undo redo restore the same ID and snapshot`() {
        val original = layeredState(listOf(layer(5, "")))
        val gateway = CommandGateway.create(original)
        val after = applyUndoRedo(gateway, DuplicateLayerCommand.create(gateway.captureSource(), layerId(5)))

        assertEquals(listOf(5, 6), ids(after))
        assertEquals(
            original
                .singleFrameLayers()
                .single()
                .name,
            after
                .singleFrameLayers()[1]
                .name,
        )
        assertSame(
            original.singleFrameSnapshot(),
            after
                .singleFrameLayers()[1]
                .snapshot,
        )
        assertEquals(
            LayerVisibility.Visible,
            after
                .singleFrameLayers()[1]
                .visibility,
        )
        assertEquals(
            PixelCell.Empty,
            cellAt(
                after
                    .singleFrameLayers()[1]
                    .snapshot,
                position(0, 0),
            ),
        )
        assertEquals(36L, gateway.runtimeState.retainedHistoryByteCount)
        assertEquals(1, gateway.runtimeState.historyEntryCount)
    }

    @Test
    fun `later strokes replace only the edited layer snapshot in either direction`() {
        val original = layeredState(listOf(layer(1, "ink")))
        val gateway = CommandGateway.create(original)
        applied(gateway.execute(DuplicateLayerCommand.create(gateway.captureSource(), layerId(1))))
        val shared =
            gateway.runtimeState.documentState
                .singleFrameLayers()[0]
                .snapshot
        val paint = stroke(original.size, listOf(position(0, 0)), redIndex)
        applied(gateway.execute(ApplyStrokeCommand.create(gateway.captureSource(), layerId(2), paint)))

        val paintedLayers = gateway.runtimeState.documentState.singleFrameLayers()
        val paintedCopy = paintedLayers[1].snapshot
        assertSame(shared, paintedLayers[0].snapshot)
        assertEquals(PixelCell.Empty, cellAt(shared, position(0, 0)))
        assertEquals(PixelCell.Covered(redIndex), cellAt(paintedCopy, position(0, 0)))
        applied(gateway.execute(ApplyStrokeCommand.create(gateway.captureSource(), layerId(1), paint)))
        assertSame(
            paintedCopy,
            gateway.runtimeState.documentState
                .singleFrameLayers()[1]
                .snapshot,
        )
        undo(gateway)
        val undoneLayers = gateway.runtimeState.documentState.singleFrameLayers()
        assertEquals(shared, undoneLayers[0].snapshot)
        assertSame(paintedCopy, undoneLayers[1].snapshot)
    }

    private fun sourceDocument(): DocumentState {
        val size = canvas(3, 1)
        val pixels = PixelSnapshot.createPackedCells(size, byteArrayOf(3, 1, 0), byteArrayOf(3)).value()
        val colors = definition(blackIndex, black, red, green, PixelColor.fromPackedRgba8888(0x12345600))
        val layers =
            listOf(
                layer(1, "bottom", PixelSnapshot.createEmpty(size)),
                layer(7, "原画✨", pixels, LayerVisibility.Hidden),
                layer(4, "top", PixelSnapshot.createEmpty(size)),
            )
        return DocumentState.createSingleFrame(defaultDocumentId, Revision.initial(), colors, layers).value()
    }
}
