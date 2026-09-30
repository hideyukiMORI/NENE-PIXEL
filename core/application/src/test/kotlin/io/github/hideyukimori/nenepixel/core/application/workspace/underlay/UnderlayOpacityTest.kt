package io.github.hideyukimori.nenepixel.core.application.workspace.underlay

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class UnderlayOpacityTest {
    @Test
    fun `constants are 26 255 and 128`() {
        assertEquals(26, UnderlayOpacity.MIN.alpha)
        assertEquals(255, UnderlayOpacity.MAX.alpha)
        assertEquals(128, UnderlayOpacity.DEFAULT.alpha)
    }

    @Test
    fun `values inside the range are kept`() {
        assertEquals(26, UnderlayOpacity.create(26).alpha)
        assertEquals(255, UnderlayOpacity.create(255).alpha)
    }

    @Test
    fun `values below the range clamp to the minimum`() {
        assertEquals(UnderlayOpacity.MIN, UnderlayOpacity.create(25))
        assertEquals(UnderlayOpacity.MIN, UnderlayOpacity.create(-1))
        assertEquals(UnderlayOpacity.MIN, UnderlayOpacity.create(Int.MIN_VALUE))
    }

    @Test
    fun `values above the range clamp to the maximum`() {
        assertEquals(UnderlayOpacity.MAX, UnderlayOpacity.create(256))
        assertEquals(UnderlayOpacity.MAX, UnderlayOpacity.create(Int.MAX_VALUE))
    }

    @Test
    fun `equality is by value`() {
        assertEquals(UnderlayOpacity.create(128), UnderlayOpacity.DEFAULT)
        assertEquals(UnderlayOpacity.create(128).hashCode(), UnderlayOpacity.DEFAULT.hashCode())
        assertNotEquals(UnderlayOpacity.create(127), UnderlayOpacity.DEFAULT)
    }
}
