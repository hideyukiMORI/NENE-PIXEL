package io.github.hideyukimori.nenepixel.core.domain.selection

import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

/** Exact orientations of an original rectangle; [then] operates on the displayed rectangle. */
public enum class PixelOrientation(
    private val xx: Int,
    private val xy: Int,
    private val yx: Int,
    private val yy: Int,
) {
    Identity(1, 0, 0, 1),
    RotateClockwise(0, -1, 1, 0),
    HalfTurn(-1, 0, 0, -1),
    RotateCounterClockwise(0, 1, -1, 0),
    FlipHorizontal(-1, 0, 0, 1),
    FlipVertical(1, 0, 0, -1),
    Transpose(0, 1, 1, 0),
    Transverse(0, -1, -1, 0),
    ;

    public val swapsAxes: Boolean
        get() = xx == 0

    /** Compose this orientation followed by [next], including noncommuting flips and rotations. */
    public fun then(next: PixelOrientation): PixelOrientation =
        entries.first {
            it.xx == next.xx * xx + next.xy * yx &&
                it.xy == next.xx * xy + next.xy * yy &&
                it.yx == next.yx * xx + next.yy * yx &&
                it.yy == next.yx * xy + next.yy * yy
        }

    /** Swapping validated axes retains the same bounded pixel count. */
    public fun orientedSize(source: CanvasSize): CanvasSize =
        if (swapsAxes) {
            CanvasSize.create(
                CanvasWidth.create(source.height.value).validated(),
                CanvasHeight.create(source.width.value).validated(),
            )
        } else {
            source
        }
}

private fun <T> DomainValueResult<T>.validated(): T =
    when (this) {
        is DomainValueResult.Created -> value
        is DomainValueResult.Rejected -> error("Validated axis rejected during orientation: $rejection")
    }
