package io.github.hideyukimori.nenepixel.core.domain.color

import io.github.hideyukimori.nenepixel.core.domain.DomainValueAssertions.created
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class PixelColorSemanticCompatibilityTest {
    @Test
    fun `named RGBA value identity preserves the complete edge Cartesian product`() {
        val colors = edgeColors()

        assertEquals(EXPECTED_COLOR_COUNT, colors.size)
        colors.forEach(::assertValueIdentity)
    }

    @Test
    fun `alpha zero retains hidden RGB in value identity regardless of hash collisions`() {
        val transparentBlack = pixelColor(RgbaChannels(0, 0, 0, 0))
        val transparentRed = pixelColor(RgbaChannels(255, 0, 0, 0))

        assertNotEquals(transparentBlack, transparentRed)
        assertEquals(2, setOf(transparentBlack, transparentRed).size)
        assertEquals(UByte.MIN_VALUE, transparentBlack.alpha.value)
        assertEquals(UByte.MIN_VALUE, transparentRed.alpha.value)
    }

    private fun assertValueIdentity(channels: RgbaChannels) {
        val first = pixelColor(channels)
        val equal = pixelColor(channels)

        assertEquals(channels.red.toUByte(), first.red.value)
        assertEquals(channels.green.toUByte(), first.green.value)
        assertEquals(channels.blue.toUByte(), first.blue.value)
        assertEquals(channels.alpha.toUByte(), first.alpha.value)
        assertEquals(first, equal)
        assertEquals(first.hashCode(), equal.hashCode())
    }

    private fun pixelColor(channels: RgbaChannels): PixelColor =
        PixelColor.create(
            created(ColorChannel.create(channels.red)),
            created(ColorChannel.create(channels.green)),
            created(ColorChannel.create(channels.blue)),
            created(ColorChannel.create(channels.alpha)),
        )

    private fun edgeColors(): List<RgbaChannels> =
        CHANNEL_EDGES.flatMap { red ->
            CHANNEL_EDGES.flatMap { green ->
                CHANNEL_EDGES.flatMap { blue ->
                    CHANNEL_EDGES.map { alpha -> RgbaChannels(red, green, blue, alpha) }
                }
            }
        }

    private data class RgbaChannels(
        val red: Int,
        val green: Int,
        val blue: Int,
        val alpha: Int,
    )

    private companion object {
        const val EXPECTED_COLOR_COUNT: Int = 1_296
        val CHANNEL_EDGES: List<Int> = listOf(0, 1, 127, 128, 254, 255)
    }
}
