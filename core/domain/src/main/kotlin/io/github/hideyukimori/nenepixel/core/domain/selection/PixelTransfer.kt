package io.github.hideyukimori.nenepixel.core.domain.selection

import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelRegion
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

/** Validated exact source and destination geometry; direct command inputs never silently clamp. */
public class PixelTransfer private constructor(
    public val canvas: CanvasSize,
    public val source: PixelRegion,
    public val destinationRegion: PixelRegion,
    public val mode: PixelTransferMode,
) {
    public val destination: PixelPosition
        get() = destinationRegion.origin

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is PixelTransfer && canvas == other.canvas && source == other.source &&
                    destinationRegion == other.destinationRegion && mode == other.mode
            )

    override fun hashCode(): Int =
        HASH_MULTIPLIER * (
            HASH_MULTIPLIER * (HASH_MULTIPLIER * canvas.hashCode() + source.hashCode()) +
                destinationRegion.hashCode()
        ) + mode.hashCode()

    public companion object {
        private const val HASH_MULTIPLIER: Int = 31

        /** Checks both rectangles against [canvas], including a source originally made for another canvas. */
        public fun create(
            canvas: CanvasSize,
            source: PixelRegion,
            destination: PixelPosition,
            mode: PixelTransferMode,
        ): DomainValueResult<PixelTransfer> =
            when (val admitted = PixelRegion.create(canvas, source.origin, source.size)) {
                is DomainValueResult.Rejected -> {
                    admitted
                }

                is DomainValueResult.Created -> {
                    when (val target = PixelRegion.create(canvas, destination, source.size)) {
                        is DomainValueResult.Rejected -> {
                            target
                        }

                        is DomainValueResult.Created -> {
                            DomainValueResult.Created(
                                PixelTransfer(canvas, source, target.value, mode),
                            )
                        }
                    }
                }
            }
    }
}
