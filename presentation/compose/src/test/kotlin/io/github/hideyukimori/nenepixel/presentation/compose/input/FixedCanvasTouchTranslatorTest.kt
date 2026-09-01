package io.github.hideyukimori.nenepixel.presentation.compose.input

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.canvas
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class FixedCanvasTouchTranslatorTest {
    private val translator = FixedCanvasTouchTranslator.create()
    private val canvas = canvas(16, 16)
    private val surface = Size(320.0f, 160.0f)

    @Test
    fun `every fixed canvas cell center maps to its typed pixel position`() {
        repeat(canvas.height.value) { y ->
            repeat(canvas.width.value) { x ->
                val offset = Offset(x * CELL_WIDTH + CELL_WIDTH / 2.0f, y * CELL_HEIGHT + CELL_HEIGHT / 2.0f)

                assertEquals(TouchTranslation.Mapped(position(x, y)), translator.translate(offset, surface, canvas))
            }
        }
    }

    @Test
    fun `mapping uses half open surface bounds without clamping`() {
        val outside =
            listOf(
                Offset(-0.01f, 0.0f),
                Offset(0.0f, -0.01f),
                Offset(surface.width, 0.0f),
                Offset(0.0f, surface.height),
                Offset(Float.NaN, 1.0f),
                Offset(1.0f, Float.POSITIVE_INFINITY),
            )

        outside.forEach { offset ->
            assertEquals(TouchTranslation.Outside, translator.translate(offset, surface, canvas))
        }
        assertEquals(
            TouchTranslation.Mapped(position(15, 15)),
            translator.translate(Offset(surface.width - 0.01f, surface.height - 0.01f), surface, canvas),
        )
        assertEquals(TouchTranslation.Outside, translator.translate(Offset.Zero, Size.Zero, canvas))
    }

    @Test
    fun `identical touch input translates deterministically`() {
        val input = Offset(211.25f, 103.75f)

        val first = translator.translate(input, surface, canvas)
        val second = translator.translate(input, surface, canvas)

        assertEquals(first, second)
    }

    private companion object {
        const val CELL_WIDTH: Float = 20.0f
        const val CELL_HEIGHT: Float = 10.0f
    }
}
