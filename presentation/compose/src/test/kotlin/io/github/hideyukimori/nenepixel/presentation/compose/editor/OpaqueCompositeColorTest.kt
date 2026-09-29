package io.github.hideyukimori.nenepixel.presentation.compose.editor

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test

internal class OpaqueCompositeColorTest {
    @Test
    fun `run of one colour matches direct blend for every pixel`() {
        assertMatchesDirectBlend(IntArray(RUN_LENGTH) { HALF_RED })
    }

    @Test
    fun `alternating colours match direct blend for every pixel`() {
        assertMatchesDirectBlend(IntArray(RUN_LENGTH) { index -> if (index % 2 == 0) HALF_RED else OPAQUE_BLUE })
    }

    @Test
    fun `leading zero is blended rather than taken from initial state`() {
        assertMatchesDirectBlend(intArrayOf(0, 0, HALF_RED, 0, 0))
    }

    @Test
    fun `alpha zero opaque and partial values match direct blend`() {
        assertMatchesDirectBlend(
            intArrayOf(TRANSPARENT_GREEN, TRANSPARENT_GREEN, OPAQUE_BLUE, HALF_RED, LOW_ALPHA_WHITE, HIGH_ALPHA_GRAY),
        )
    }

    private fun assertMatchesDirectBlend(input: IntArray) {
        BACKGROUNDS.forEach { backgroundArgb ->
            val transform = OpaqueCompositeColor(backgroundArgb)
            val expected = IntArray(input.size) { index -> input[index].rgbaOverOpaqueArgb(backgroundArgb) }
            val actual = IntArray(input.size) { index -> transform.map(input[index]) }

            assertArrayEquals(expected, actual)
        }
    }
}

private const val RUN_LENGTH: Int = 8
private const val HALF_RED: Int = 0xff000080.toInt()
private const val OPAQUE_BLUE: Int = 0x0000ffff
private const val TRANSPARENT_GREEN: Int = 0x00ff0000
private const val LOW_ALPHA_WHITE: Int = 0xffffff01.toInt()
private const val HIGH_ALPHA_GRAY: Int = 0x808080fe.toInt()
private val BACKGROUNDS: List<Int> = listOf(0xffffffff.toInt(), 0xff000000.toInt(), 0xff336699.toInt())
