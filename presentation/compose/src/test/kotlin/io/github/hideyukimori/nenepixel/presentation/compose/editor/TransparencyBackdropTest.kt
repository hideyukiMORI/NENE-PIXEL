package io.github.hideyukimori.nenepixel.presentation.compose.editor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class TransparencyBackdropTest {
    @Test
    fun `cell px is 8 dp rounded to whole pixels`() {
        assertEquals(8, TransparencyBackdrop.cellPx(1.0f))
        assertEquals(14, TransparencyBackdrop.cellPx(1.75f))
        assertEquals(21, TransparencyBackdrop.cellPx(2.625f))
    }

    @Test
    fun `cell px is at least 2`() {
        assertEquals(2, TransparencyBackdrop.cellPx(0.2f))
    }

    @Test
    fun `origin is light`() {
        assertEquals(LIGHT, TransparencyBackdrop.colorAt(0, 0, CELL))
    }

    @Test
    fun `cell boundary switches from light to dark`() {
        assertEquals(LIGHT, TransparencyBackdrop.colorAt(CELL - 1, 0, CELL))
        assertEquals(DARK, TransparencyBackdrop.colorAt(CELL, 0, CELL))
        assertEquals(LIGHT, TransparencyBackdrop.colorAt(0, CELL - 1, CELL))
        assertEquals(DARK, TransparencyBackdrop.colorAt(0, CELL, CELL))
    }

    @Test
    fun `second cell diagonal and third cell alternate`() {
        assertEquals(LIGHT, TransparencyBackdrop.colorAt(CELL, CELL, CELL))
        assertEquals(LIGHT, TransparencyBackdrop.colorAt(2 * CELL, 0, CELL))
        assertEquals(DARK, TransparencyBackdrop.colorAt(2 * CELL, CELL, CELL))
    }

    private companion object {
        const val CELL: Int = 14
        const val LIGHT: Int = 0xFFFFFFFF.toInt()
        const val DARK: Int = 0xFFD9D9D9.toInt()
    }
}
