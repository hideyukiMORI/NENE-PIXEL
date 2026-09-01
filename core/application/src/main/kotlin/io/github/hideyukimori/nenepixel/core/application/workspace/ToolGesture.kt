package io.github.hideyukimori.nenepixel.core.application.workspace

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.drawing.Stroke
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

public class ToolGesture private constructor(
    public val canvas: CanvasSize,
    private val path: List<PixelPosition>,
    public val color: PixelColor,
) {
    public val positionCount: Int
        get() = path.size

    internal val lastPosition: PixelPosition
        get() = path.last()

    public fun forEachPosition(action: (PixelPosition) -> Unit) {
        path.forEach(action)
    }

    internal fun extend(position: PixelPosition): ToolGesture = ToolGesture(canvas, path + position, color)

    internal fun prepareStroke(): Stroke =
        when (val result = Stroke.create(canvas, path, color)) {
            is DomainValueResult.Created -> {
                result.value
            }

            is DomainValueResult.Rejected -> {
                error(
                    "Validated ToolGesture produced an invalid Stroke: ${result.rejection}",
                )
            }
        }

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is ToolGesture && canvas == other.canvas && path == other.path && color == other.color)

    override fun hashCode(): Int =
        ((canvas.hashCode() * HASH_MULTIPLIER) + path.hashCode()) * HASH_MULTIPLIER + color.hashCode()

    override fun toString(): String = "ToolGesture(canvas=$canvas, positionCount=$positionCount, color=$color)"

    internal companion object {
        private const val HASH_MULTIPLIER: Int = 31

        fun begin(
            canvas: CanvasSize,
            position: PixelPosition,
            color: PixelColor,
        ): ToolGesture = ToolGesture(canvas, listOf(position), color)
    }
}
