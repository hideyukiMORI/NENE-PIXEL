package io.github.hideyukimori.nenepixel.core.pixelengine

import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.drawing.Stroke
import io.github.hideyukimori.nenepixel.core.domain.drawing.StrokeEffect
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelRegion
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.jupiter.api.fail

internal object PixelEngineTestValues {
    val black: PaletteIndex = index(0)
    val red: PaletteIndex = index(1)
    val green: PaletteIndex = index(2)

    fun canvas(
        width: Int,
        height: Int,
    ): CanvasSize = CanvasSize.create(CanvasWidth.create(width).value(), CanvasHeight.create(height).value())

    fun position(
        x: Int,
        y: Int,
    ): PixelPosition = PixelPosition.create(PixelX.create(x).value(), PixelY.create(y).value())

    fun revision(value: Long): Revision = Revision.create(value).value()

    fun stroke(
        canvas: CanvasSize,
        path: List<PixelPosition>,
        index: PaletteIndex,
    ): Stroke = Stroke.create(canvas, path, StrokeEffect.Paint(index)).value()

    fun eraserStroke(
        canvas: CanvasSize,
        path: List<PixelPosition>,
    ): Stroke = Stroke.create(canvas, path, StrokeEffect.Erase(black)).value()

    fun region(
        canvas: CanvasSize,
        origin: PixelPosition,
        size: CanvasSize,
    ): PixelRegion = PixelRegion.create(canvas, origin, size).value()

    fun snapshot(
        canvas: CanvasSize,
        revision: Revision = Revision.initial(),
        pixels: List<PaletteIndex> = List(canvas.pixelCount.toInt()) { black },
    ): PixelSnapshot = PixelSnapshot.create(canvas, revision, pixels).value()

    fun indexAt(
        snapshot: PixelSnapshot,
        position: PixelPosition,
    ): PaletteIndex = snapshot.indexAt(position).value()

    fun index(value: Int): PaletteIndex = PaletteIndex.create(value).value()

    private fun <T> DomainValueResult<T>.value(): T =
        when (this) {
            is DomainValueResult.Created -> value
            is DomainValueResult.Rejected -> fail("Test input was rejected: $rejection")
        }
}
