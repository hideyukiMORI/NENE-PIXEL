package io.github.hideyukimori.nenepixel.core.application.render

import io.github.hideyukimori.nenepixel.core.application.document.command.ApplyStrokeCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandGateway
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResultAssertions.applied
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.canvas
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.defaultDocumentId
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.definition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.paletteIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.position
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layerId
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.value
import io.github.hideyukimori.nenepixel.core.application.workspace.ToolGesture
import io.github.hideyukimori.nenepixel.core.application.workspace.ToolGestureExtensionResult
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.drawing.StrokeEffect
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.layer.Layer
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerName
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class StrokePreviewCompositeTest {
    private val size: CanvasSize = canvas(4, 2)

    @Test
    fun `paint preview matches the committed composite on the bottom middle and top layers`() {
        (1..3).forEach { target -> assertPreviewMatchesCommit(target, StrokeEffect.Paint(paletteIndex(1))) }
    }

    @Test
    fun `erase preview matches the committed composite on the bottom middle and top layers`() {
        (1..3).forEach { target -> assertPreviewMatchesCommit(target, StrokeEffect.Erase) }
    }

    @Test
    fun `a gesture on another canvas size is an invariant violation`() {
        val document = document(LayerVisibility.Visible)
        val gesture = gesture(document, canvas(2, 1), 2, StrokeEffect.Erase, listOf(position(0, 0)))
        assertThrows<IllegalStateException> { StrokePreviewComposite.prepare(document, gesture) }
    }

    @Test
    fun `a gesture on a hidden layer is an invariant violation`() {
        val document = document(LayerVisibility.Hidden)
        val gesture = gesture(document, size, 2, StrokeEffect.Erase, listOf(position(0, 0)))
        assertThrows<IllegalStateException> { StrokePreviewComposite.prepare(document, gesture) }
    }

    @Test
    fun `a position outside the canvas is refused`() {
        val document = document(LayerVisibility.Visible)
        val preview =
            StrokePreviewComposite.prepare(document, gesture(document, size, 1, StrokeEffect.Erase, path))
        assertThrows<IllegalArgumentException> { preview.packedRgba8888At(position(4, 0)) }
    }

    private fun assertPreviewMatchesCommit(
        target: Int,
        effect: StrokeEffect,
    ) {
        val document = document(LayerVisibility.Visible)
        val gateway = CommandGateway.create(document)
        val gesture = gesture(document, size, target, effect, path, gateway)
        val preview = StrokePreviewComposite.prepare(document, gesture)
        applied(gateway.execute(ApplyStrokeCommand.create(gesture.admission, layerId(target), gesture.prepareStroke())))
        val committed = DocumentComposite.render(gateway.runtimeState.documentState).copyPackedRgba8888()
        gesture.forEachPosition { position ->
            val pixel = position.y.value * size.width.value + position.x.value
            assertEquals(committed[pixel], preview.packedRgba8888At(position), "target=$target $effect at $position")
        }
    }

    private fun gesture(
        document: DocumentState,
        canvas: CanvasSize,
        target: Int,
        effect: StrokeEffect,
        positions: List<PixelPosition>,
        gateway: CommandGateway = CommandGateway.create(document),
    ): ToolGesture =
        positions.drop(1).fold(
            ToolGesture.begin(canvas, positions.first(), effect, layerId(target), gateway.captureSource()),
        ) { gesture, next ->
            (gesture.extend(next) as ToolGestureExtensionResult.Extended).gesture
        }

    /** Three layers with translucent, opaque and Empty cells; the middle layer has [middleVisibility]. */
    private fun document(middleVisibility: LayerVisibility): DocumentState =
        DocumentState
            .createLayered(
                defaultDocumentId,
                Revision.initial(),
                definition(paletteIndex(0), *palette),
                listOf(
                    layer(1, LayerVisibility.Visible, listOf(0, null, 1, 2, null, 0, 3, null)),
                    layer(2, middleVisibility, listOf(1, 1, null, null, 2, null, 0, null)),
                    layer(3, LayerVisibility.Visible, listOf(null, 2, 2, null, 1, null, null, null)),
                ),
            ).value()

    private fun layer(
        id: Int,
        visibility: LayerVisibility,
        cells: List<Int?>,
    ): Layer {
        val indices = ByteArray(cells.size) { (cells[it] ?: 0).toByte() }
        var coverage = 0
        cells.forEachIndexed { pixel, cell -> if (cell != null) coverage = coverage or (1 shl pixel) }
        val snapshot = PixelSnapshot.createPackedCells(size, indices, byteArrayOf(coverage.toByte())).value()
        return Layer.create(layerId(id), LayerName.empty, visibility, snapshot)
    }

    private companion object {
        /** Covers every pixel but (0, 1), with forward and backward segments. */
        val path: List<PixelPosition> = listOf(position(0, 0), position(3, 0), position(3, 1), position(1, 1))

        val palette: Array<PixelColor> =
            arrayOf(0x204060ff, 0xa0c0e080.toInt(), 0xff000040.toInt(), 0x00ff00ff)
                .map(PixelColor::fromPackedRgba8888)
                .toTypedArray()
    }
}
