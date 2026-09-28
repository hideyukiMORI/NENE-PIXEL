package io.github.hideyukimori.nenepixel.core.projectformat

import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * Offsets in layered-v3.hex: layer count 49; layer 0 at 50 (flags 54, name length 55, name 56,
 * coverage 57, indices 58..60); layer 1 at 61 (flags 65, name length 66, name 67..68, coverage 69,
 * indices 70..72); CRC32 73..76. Each mutation rewrites the CRC unless the CRC is under test.
 */
internal class ProjectFormatV3CodecRejectionTest {
    private val layered: ByteArray = ProjectFormatTestValues.golden("layered-v3.hex")

    @Test
    fun `layer count outside one to sixteen is rejected`() {
        listOf(0, 17, 255).forEach { count ->
            assertEquals(ProjectFormatRejection.InvalidLayerCount(count), decode(mutated { it[49] = count.toByte() }))
        }
    }

    @Test
    fun `layer id zero and ids from two to the thirty first are rejected with the wire value`() {
        listOf(0L, 0x8000_0000L, 0xffff_ffffL).forEach { wire ->
            val bytes = mutated { ProjectFormatBigEndian.writeInt(it, 50, wire.toInt()) }
            assertEquals(ProjectFormatRejection.InvalidLayerId(wire), decode(bytes))
        }
    }

    @Test
    fun `duplicate layer id is rejected`() {
        val bytes = mutated { ProjectFormatBigEndian.writeInt(it, 61, 1) }

        assertEquals(ProjectFormatRejection.DuplicateLayerId(1), decode(bytes))
    }

    @Test
    fun `unknown flag bits are rejected`() {
        assertEquals(ProjectFormatRejection.UnknownLayerFlags(0, 3), decode(mutated { it[54] = 3 }))
        assertEquals(ProjectFormatRejection.UnknownLayerFlags(1, 0x80), decode(mutated { it[65] = 0x80.toByte() }))
    }

    @Test
    fun `invalid UTF-8 names are rejected`() {
        assertEquals(ProjectFormatRejection.InvalidLayerName(0), decode(mutated { it[56] = 0x80.toByte() }))
        assertEquals(ProjectFormatRejection.InvalidLayerName(1), decode(mutated { it[68] = 0x41 }))
    }

    @Test
    fun `names over the byte limit or rejected by the domain are rejected`() {
        val surrogate = byteArrayOf(0xed.toByte(), 0xa0.toByte(), 0x80.toByte())
        listOf(
            ByteArray(129) { 0x61 },
            ByteArray(33) { 0x61 },
            byteArrayOf(0x07),
            surrogate,
        ).forEach { name ->
            assertEquals(ProjectFormatRejection.InvalidLayerName(0), decode(withBottomName(name)))
        }
    }

    @Test
    fun `thirty two four byte code points fill the name limit exactly`() {
        val name = ProjectFormatV3TestValues.longestName.encodeToByteArray()

        assertEquals(128, name.size)
        val result = ProjectFormatCodec.decode(ProjectFormatTestValues.carrier(withBottomName(name)))
        assertInstanceOf(ProjectFormatResult.Accepted::class.java, result)
    }

    @Test
    fun `coverage bits after the last pixel and indexed empty cells are rejected`() {
        assertEquals(ProjectFormatRejection.InvalidCoverage(0), decode(mutated { it[57] = 0x0d }))
        assertEquals(ProjectFormatRejection.InvalidCoverage(1), decode(mutated { it[70] = 1 }))
    }

    @Test
    fun `covered index outside the palette names its position`() {
        val rejection = decode(mutated { it[60] = 2 })

        assertEquals(
            ProjectFormatRejection.PixelIndexOutsidePalette(
                PixelPosition.create(created(PixelX.create(2)), created(PixelY.create(0))),
                created(PaletteIndex.create(2)),
                2,
            ),
            rejection,
        )
    }

    @Test
    fun `short files are truncated at the first missing field`() {
        assertEquals(ProjectFormatRejection.Truncated(40, 41), decode(layered.copyOf(40)))
        assertEquals(ProjectFormatRejection.Truncated(49, 50), decode(layered.copyOf(49)))
        assertEquals(ProjectFormatRejection.Truncated(52, 56), decode(layered.copyOf(52)))
        assertEquals(ProjectFormatRejection.Truncated(76, 77), decode(layered.copyOf(76)))
    }

    @Test
    fun `checksum mismatch is rejected`() {
        val bytes = layered.copyOf().also { it[76] = (it[76].toInt() xor 1).toByte() }

        assertInstanceOf(ProjectFormatRejection.ChecksumMismatch::class.java, decode(bytes))
    }

    @Test
    fun `header rejections match v2`() {
        assertSame(ProjectFormatRejection.InvalidCanvas, decode(mutated { it[11] = 0 }))
        assertSame(ProjectFormatRejection.InvalidRevision, decode(mutated { it[30] = 0x80.toByte() }))
        assertInstanceOf(
            ProjectFormatRejection.InvalidPaletteEntryCount::class.java,
            decode(mutated { it[39] = 1 }),
        )
        assertInstanceOf(
            ProjectFormatRejection.DefaultIndexOutsidePalette::class.java,
            decode(mutated { it[40] = 2 }),
        )
    }

    private fun mutated(change: (ByteArray) -> Unit): ByteArray =
        ProjectFormatV3TestValues.withChecksum(layered.copyOf().also(change))

    /** Replaces the bottom layer name (length at 55, one byte at 56) and keeps the rest of the file. */
    private fun withBottomName(name: ByteArray): ByteArray {
        val bytes = layered.copyOfRange(0, 55) + byteArrayOf(name.size.toByte()) + name + layered.copyOfRange(57, 77)
        return ProjectFormatV3TestValues.withChecksum(bytes)
    }

    private fun decode(bytes: ByteArray): ProjectFormatRejection =
        when (val result = ProjectFormatCodec.decode(ProjectFormatTestValues.carrier(bytes))) {
            is ProjectFormatResult.Accepted -> error("Expected rejection")
            is ProjectFormatResult.Rejected -> result.rejection
        }

    private fun <T> created(result: DomainValueResult<T>): T =
        when (result) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Expected a valid domain value: ${result.rejection}")
        }
}
