package io.github.hideyukimori.nenepixel.core.application.document.transition

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
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelChange
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelPatch
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelPatchApplicationResult
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelPatchCreationResult
import org.junit.jupiter.api.fail

internal object ApplicationTestValues {
    val black: PixelColor = color(0, 0, 0, 255)
    val red: PixelColor = color(255, 0, 0, 255)
    val green: PixelColor = color(0, 255, 0, 255)
    val blackIndex: PaletteIndex = paletteIndex(0)
    val redIndex: PaletteIndex = paletteIndex(1)
    val greenIndex: PaletteIndex = paletteIndex(2)
    val defaultDefinition: PaletteDefinition = definition(blackIndex, black, red, green)

    fun canvas(
        width: Int,
        height: Int,
    ): CanvasSize = CanvasSize.create(CanvasWidth.create(width).value(), CanvasHeight.create(height).value())

    fun position(
        x: Int,
        y: Int,
    ): PixelPosition = PixelPosition.create(PixelX.create(x).value(), PixelY.create(y).value())

    fun revision(value: Long): Revision = Revision.create(value).value()

    fun palette(vararg colors: PixelColor): Palette = Palette.create(colors.toList()).value()

    fun definition(
        defaultIndex: PaletteIndex,
        vararg colors: PixelColor,
    ): PaletteDefinition = PaletteDefinition.create(palette(*colors), defaultIndex).value()

    fun paletteIndex(value: Int): PaletteIndex = PaletteIndex.create(value).value()

    fun snapshot(
        canvas: CanvasSize,
        revision: Revision = Revision.initial(),
        indices: List<PaletteIndex> = List(canvas.pixelCount.toInt()) { blackIndex },
    ): PixelSnapshot = PixelSnapshot.create(canvas, revision, indices).value()

    fun state(
        canvas: CanvasSize,
        revision: Revision = Revision.initial(),
        indices: List<PaletteIndex> = List(canvas.pixelCount.toInt()) { blackIndex },
        documentId: DocumentId = defaultDocumentId,
        definition: PaletteDefinition = defaultDefinition,
    ): DocumentState = DocumentState.create(documentId, definition, snapshot(canvas, revision, indices)).value()

    fun stroke(
        canvas: CanvasSize,
        path: List<PixelPosition>,
        index: PaletteIndex,
    ): Stroke = Stroke.create(canvas, path, StrokeEffect.Paint(index)).value()

    fun eraserStroke(
        canvas: CanvasSize,
        path: List<PixelPosition>,
        targetIndex: PaletteIndex = blackIndex,
    ): Stroke = Stroke.create(canvas, path, StrokeEffect.Erase(targetIndex)).value()

    fun patch(
        canvas: CanvasSize,
        beforeRevision: Revision,
        changes: List<PixelChange>,
    ): PixelPatch =
        when (val result = PixelPatch.create(canvas, beforeRevision, changes)) {
            is PixelPatchCreationResult.Created -> result.patch
            is PixelPatchCreationResult.Rejected -> fail("Test patch was rejected: ${result.rejection}")
        }

    fun appliedSnapshot(result: PixelPatchApplicationResult): PixelSnapshot =
        when (result) {
            is PixelPatchApplicationResult.Applied -> result.snapshot
            is PixelPatchApplicationResult.Rejected -> fail("Test application was rejected: ${result.rejection}")
        }

    fun indexAt(
        snapshot: PixelSnapshot,
        position: PixelPosition,
    ): PaletteIndex = snapshot.indexAt(position).value()

    private fun color(
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int,
    ): PixelColor =
        PixelColor.create(
            red = ColorChannel.create(red).value(),
            green = ColorChannel.create(green).value(),
            blue = ColorChannel.create(blue).value(),
            alpha = ColorChannel.create(alpha).value(),
        )

    private fun <T> DomainValueResult<T>.value(): T =
        when (this) {
            is DomainValueResult.Created -> value
            is DomainValueResult.Rejected -> fail("Test value was rejected: $rejection")
        }

    val defaultDocumentId: DocumentId = DocumentId.create("0".repeat(32)).value()
    val otherDocumentId: DocumentId = DocumentId.create("1".repeat(32)).value()
}
