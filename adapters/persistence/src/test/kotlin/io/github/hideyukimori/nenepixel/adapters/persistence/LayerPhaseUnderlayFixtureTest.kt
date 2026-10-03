package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImage
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImageResult
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayInteraction
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayVisibility
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

internal class LayerPhaseUnderlayFixtureTest {
    @Test
    fun `checked PNG has the pinned bytes metadata and every canonical pixel`() {
        val bytes = Files.readAllBytes(checkedAsset())
        assertEquals(184_323, bytes.size)
        assertEquals(
            "05efb3fc8edf43f45dc5a8b7cae3be148680c5694b47c47d1cf4eb7cbe26c6fb",
            LayerPhaseUnderlayFixture.sha256(bytes),
        )
        assertArrayEquals(LayerPhaseUnderlayFixture.pngBytes(), bytes)
        assertPngChunks(bytes)

        val image = ImageIO.read(ByteArrayInputStream(bytes))
        assertEquals(1024, image.width)
        assertEquals(1024, image.height)
        val argb = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
        val rgba = IntArray(argb.size) { (argb[it] shl 8) or (argb[it] ushr 24) }
        assertArrayEquals(LayerPhaseUnderlayFixture.rgbaPixels(), rgba)
        assertEquals(
            "f107eb10700c55df2cb3a4dd1b2723b14bc773dcd59866f4a65a5b94d583aa78",
            LayerPhaseUnderlayFixture.rgbaSha256(rgba),
        )
        assertTrue(rgba.all { it and 0xFF == 255 && it ushr 8 != 0 })
    }

    @Test
    fun `production placement fits the fixed image with exact default alpha and resting state`() {
        val pixels = LayerPhaseUnderlayFixture.rgbaPixels()
        val image = (ReferenceImage.create(1024, 1024, pixels) as ReferenceImageResult.Created).image
        val canvas =
            CanvasSize.create(
                (CanvasWidth.create(256) as DomainValueResult.Created).value,
                (CanvasHeight.create(256) as DomainValueResult.Created).value,
            )

        val underlay = ReferenceUnderlay.placed(image, canvas)

        assertSame(image, underlay.image)
        assertEquals(canvas, underlay.canvas)
        assertArrayEquals(pixels, underlay.image.copyPackedRgba8888())
        assertEquals(0.0, underlay.placement.left)
        assertEquals(0.0, underlay.placement.top)
        assertEquals(0.25, underlay.placement.scale)
        assertEquals(128, underlay.opacity.alpha)
        assertSame(UnderlayVisibility.Shown, underlay.visibility)
        assertSame(UnderlayInteraction.Resting, underlay.interaction)
    }

    private fun assertPngChunks(bytes: ByteArray) {
        val input = ByteBuffer.wrap(bytes)
        val signature = ByteArray(8).also(input::get)
        assertArrayEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 13, 10, 26, 10), signature)
        val kinds = mutableListOf<String>()
        while (input.hasRemaining()) {
            val length = input.int
            assertTrue(length >= 0 && length <= input.remaining() - 8)
            val kind = String(ByteArray(4).also(input::get), Charsets.US_ASCII)
            kinds.add(kind)
            val data = ByteArray(length).also(input::get)
            input.int // CRC is bound by the exact encoded hash above.
            if (kind == "IHDR") {
                assertArrayEquals(byteArrayOf(0, 0, 4, 0, 0, 0, 4, 0, 8, 2, 0, 0, 0), data)
            }
        }
        assertEquals("IHDR", kinds.first())
        assertEquals("IEND", kinds.last())
        assertTrue(kinds.subList(1, kinds.lastIndex).isNotEmpty())
        assertTrue(kinds.subList(1, kinds.lastIndex).all { it == "IDAT" })
    }

    private fun checkedAsset(): Path {
        val relative = Path.of("docs/quality/fixtures/p4-layer-phase/underlay-grid.png")
        return generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
            .map { it.resolve(relative) }
            .firstOrNull(Files::isRegularFile)
            ?: error("Checked #145 underlay fixture is missing")
    }
}
