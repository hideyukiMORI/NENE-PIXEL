package io.github.hideyukimori.nenepixel.core.application.workspace.underlay

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class RememberedPlacementTest {
    @Test
    fun `finite offsets and a positive scale are created`() {
        val result = RememberedPlacement.create(-12.5, 300.25, 0.5)

        check(result is RememberedPlacementResult.Created) { "expected Created, got $result" }
        assertEquals(-12.5, result.placement.left)
        assertEquals(300.25, result.placement.top)
        assertEquals(0.5, result.placement.scale)
    }

    @Test
    fun `a NaN or infinite left is rejected`() {
        for (left in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertRejected(RememberedPlacementRejection.NonFiniteOffset, RememberedPlacement.create(left, 0.0, 1.0))
        }
    }

    @Test
    fun `a NaN or infinite top is rejected`() {
        for (top in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertRejected(RememberedPlacementRejection.NonFiniteOffset, RememberedPlacement.create(0.0, top, 1.0))
        }
    }

    @Test
    fun `a NaN or infinite scale is rejected`() {
        for (scale in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertRejected(RememberedPlacementRejection.InvalidScale, RememberedPlacement.create(0.0, 0.0, scale))
        }
    }

    @Test
    fun `a zero or negative scale is rejected`() {
        for (scale in listOf(0.0, -0.0, -1.0, -Double.MIN_VALUE)) {
            assertRejected(RememberedPlacementRejection.InvalidScale, RememberedPlacement.create(0.0, 0.0, scale))
        }
    }

    @Test
    fun `equal values are equal and a negative zero offset equals zero`() {
        val first = created(-0.0, -0.0, 2.0)
        val second = created(0.0, 0.0, 2.0)

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertEquals("RememberedPlacement(left=0.0, top=0.0, scale=2.0)", first.toString())
    }

    private fun assertRejected(
        expected: RememberedPlacementRejection,
        result: RememberedPlacementResult,
    ) {
        assertEquals(RememberedPlacementResult.Rejected(expected), result)
    }

    private fun created(
        left: Double,
        top: Double,
        scale: Double,
    ): RememberedPlacement {
        val result = RememberedPlacement.create(left, top, scale)
        check(result is RememberedPlacementResult.Created) { "expected Created, got $result" }
        return result.placement
    }
}
