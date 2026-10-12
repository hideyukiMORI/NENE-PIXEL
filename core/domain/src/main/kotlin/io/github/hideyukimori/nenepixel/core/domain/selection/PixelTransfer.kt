package io.github.hideyukimori.nenepixel.core.domain.selection

import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelRegion
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

/** Validated exact source and destination geometry; direct command inputs never silently clamp. */
public class PixelTransfer private constructor(
    public val canvas: CanvasSize,
    public val source: PixelRegion,
    private val target: TransferTarget,
    public val mode: PixelTransferMode,
) {
    public val destination: PixelPosition
        get() = target.placement.position

    public val destinationRegion: PixelRegion
        get() = target.region

    public val orientation: PixelOrientation
        get() = target.placement.orientation

    public val isIdentity: Boolean
        get() = source.origin == destination && orientation == PixelOrientation.Identity

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is PixelTransfer && canvas == other.canvas && source == other.source &&
                    target == other.target && mode == other.mode
            )

    override fun hashCode(): Int =
        HASH_MULTIPLIER * (
            HASH_MULTIPLIER * (HASH_MULTIPLIER * canvas.hashCode() + source.hashCode()) +
                target.hashCode()
        ) + mode.hashCode()

    public companion object {
        private const val HASH_MULTIPLIER: Int = 31

        /** Checks both rectangles against [canvas], including a source originally made for another canvas. */
        public fun create(
            canvas: CanvasSize,
            source: PixelRegion,
            placement: PixelPlacement,
            mode: PixelTransferMode,
        ): DomainValueResult<PixelTransfer> =
            when (val admitted = PixelRegion.create(canvas, source.origin, source.size)) {
                is DomainValueResult.Rejected -> {
                    admitted
                }

                is DomainValueResult.Created -> {
                    when (
                        val target =
                            PixelRegion.create(
                                canvas,
                                placement.position,
                                placement.orientation.orientedSize(source.size),
                            )
                    ) {
                        is DomainValueResult.Rejected -> {
                            target
                        }

                        is DomainValueResult.Created -> {
                            DomainValueResult.Created(
                                PixelTransfer(canvas, source, TransferTarget(placement, target.value), mode),
                            )
                        }
                    }
                }
            }
    }
}

/** The admitted placement and its derived rectangle are one validated target geometry. */
private data class TransferTarget(
    val placement: PixelPlacement,
    val region: PixelRegion,
)
