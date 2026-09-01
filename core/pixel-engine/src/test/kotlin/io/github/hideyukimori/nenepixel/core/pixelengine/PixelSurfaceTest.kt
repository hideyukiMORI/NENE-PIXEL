package io.github.hideyukimori.nenepixel.core.pixelengine

import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.black
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.canvas
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.position
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.red
import io.github.hideyukimori.nenepixel.core.pixelengine.PixelEngineTestValues.snapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

internal class PixelSurfaceTest {
    @Test
    fun `surfaces privately own independent storage from the same snapshot`() {
        val original = snapshot(canvas(1, 1))
        val changedSurface = PixelSurface.from(original)
        val untouchedSurface = PixelSurface.from(original)

        changedSurface.write(PixelChange.create(position(0, 0), black, red))

        assertEquals(red, changedSurface.snapshot(original.revision).onlyColor())
        assertEquals(black, untouchedSurface.snapshot(original.revision).onlyColor())
        assertEquals(black, original.onlyColor())
    }

    private fun io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot.onlyColor():
        io.github.hideyukimori.nenepixel.core.domain.color.PixelColor =
        when (val result = colorAt(position(0, 0))) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> fail("Test position was rejected: ${result.rejection}")
        }
}
