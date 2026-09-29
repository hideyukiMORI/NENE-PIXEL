package io.github.hideyukimori.nenepixel.core.pixelengine

import io.github.hideyukimori.nenepixel.core.domain.drawing.StrokeEffect

/** The cell every effective position of a stroke becomes: `Covered(index)` for Paint, `Empty` for Erase. */
internal class StrokeTarget(
    val covered: Boolean,
    val packedIndex: Byte,
) {
    fun matches(
        surface: PixelSurface,
        rowMajorIndex: Int,
    ): Boolean =
        surface.isCoveredAt(rowMajorIndex) == covered &&
            (!covered || surface.packedIndexAt(rowMajorIndex) == packedIndex)

    companion object {
        private val EMPTY = StrokeTarget(covered = false, packedIndex = 0)

        fun of(effect: StrokeEffect): StrokeTarget =
            when (effect) {
                is StrokeEffect.Paint -> {
                    StrokeTarget(covered = true, packedIndex = effect.targetIndex.value.toByte())
                }

                StrokeEffect.Erase -> {
                    EMPTY
                }
            }
    }
}
