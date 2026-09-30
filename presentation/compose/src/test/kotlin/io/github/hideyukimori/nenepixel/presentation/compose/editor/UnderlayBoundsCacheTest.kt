package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImage
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImageResult
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.canvas
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/** Issue #170 A6b: a steady frame reuses the last underlay bounds instead of computing them (QLT-019). */
internal class UnderlayBoundsCacheTest {
    @Test
    fun `unchanged inputs reuse the last result and any change computes once`() {
        val cache = UnderlayBoundsCache()
        val underlay = ReferenceUnderlay.placed(image(2, 2), CANVAS)
        val edges = floatArrayOf(10f, 20f, 50f, 50f)

        val first = cache.resolve(edges, CANVAS, underlay.image, underlay.placement)
        val again = cache.resolve(edges, CANVAS, underlay.image, underlay.placement)
        assertSame(first, again)
        assertEquals(1, cache.computations)

        edges[UnderlayBoundsCache.RIGHT] = 90f
        val wider = cache.resolve(edges, CANVAS, underlay.image, underlay.placement)
        val expected = underlayBounds(UnderlayBounds(10f, 20f, 90f, 50f), CANVAS, underlay.image, underlay.placement)
        assertEquals(expected, wider)
        cache.resolve(edges, CANVAS, underlay.image, underlay.withPlacement(0.0, 0.0, 1.0).placement)
        cache.forget()
        cache.resolve(edges, CANVAS, underlay.image, underlay.withPlacement(0.0, 0.0, 1.0).placement)
        assertEquals(4, cache.computations)
    }

    private companion object {
        val CANVAS = canvas(4, 3)

        fun image(
            width: Int,
            height: Int,
        ): ReferenceImage =
            when (val result = ReferenceImage.create(width, height, IntArray(width * height))) {
                is ReferenceImageResult.Created -> result.image
                is ReferenceImageResult.Rejected -> error("Invalid underlay image: ${result.reason}")
            }
    }
}
