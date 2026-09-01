package io.github.hideyukimori.nenepixel.core.pixelengine

import io.github.hideyukimori.nenepixel.core.domain.color.ColorChannel
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.drawing.Stroke
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelRegion
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.jupiter.api.fail

internal object PixelEngineTestValues {
    val black: PixelColor = color(0, 0, 0, 255)
    val red: PixelColor = color(255, 0, 0, 255)
    val green: PixelColor = color(0, 255, 0, 255)

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
        color: PixelColor,
    ): Stroke = Stroke.create(canvas, path, color).value()

    fun region(
        canvas: CanvasSize,
        origin: PixelPosition,
        size: CanvasSize,
    ): PixelRegion = PixelRegion.create(canvas, origin, size).value()

    fun snapshot(
        canvas: CanvasSize,
        revision: Revision = Revision.initial(),
        pixels: List<PixelColor> = List(canvas.pixelCount.toInt()) { black },
    ): PixelSnapshot = PixelSnapshot.create(canvas, revision, pixels).value()

    fun colorAt(
        snapshot: PixelSnapshot,
        position: PixelPosition,
    ): PixelColor = snapshot.colorAt(position).value()

    private fun color(
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int,
    ): PixelColor =
        PixelColor.create(
            ColorChannel.create(red).value(),
            ColorChannel.create(green).value(),
            ColorChannel.create(blue).value(),
            ColorChannel.create(alpha).value(),
        )

    private fun <T> DomainValueResult<T>.value(): T =
        when (this) {
            is DomainValueResult.Created -> value
            is DomainValueResult.Rejected -> fail("Test input was rejected: $rejection")
        }
}
