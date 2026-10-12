package io.github.hideyukimori.nenepixel.core.projectformat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

internal class ProjectFormatV4AllocationTest {
    @Test
    fun `late invalid coverage and palette index reject before any snapshot mapping`() {
        listOf(92 to 0x80, 98 to 3).forEach { (offset, value) ->
            val bytes = ProjectFormatTestValues.golden("two-frames-v4.hex")
            bytes[offset] = value.toByte()
            assertNoMappings(ProjectFormatV4TestValues.checksum(bytes))
        }
    }

    @Test
    fun `late malformed UTF8 rejects before earlier valid frame snapshots`() {
        val original = ProjectFormatTestValues.golden("two-frames-v4.hex")
        val bytes = original.copyOfRange(0, 92) + byteArrayOf(0xff.toByte()) + original.copyOfRange(92, original.size)
        bytes[91] = 1
        val result = assertNoMappings(ProjectFormatV4TestValues.checksum(bytes))
        assertEquals(ProjectFormatRejection.InvalidLayerName(1), result.rejection)
    }

    @Test
    fun `global record excess is rejected from last frame header before any pixel allocation`() {
        val bytes = ProjectFormatCodec.encode(ProjectFormatV4TestValues.maximumFrames()).copyBytes()
        val lastFrame = 1_066 + 15 * (9 + 6 + 128 + 8_192 + 65_536)
        bytes[lastFrame + 8] = 2
        val result = assertNoMappings(ProjectFormatV4TestValues.checksum(bytes))
        assertEquals(ProjectFormatRejection.InvalidTotalLayerCount(17), result.rejection)
    }

    private fun assertNoMappings(bytes: ByteArray): ProjectFormatResult.Rejected {
        var mappings = 0
        val mapper =
            ProjectFormatV4SnapshotMapper { source, size, layer ->
                mappings++
                CanonicalProjectFormatV4SnapshotMapper.map(source, size, layer)
            }
        val result = ProjectFormatV4Decoder(mapper).decode(ProjectFormatTestValues.carrier(bytes))
        assertEquals(0, mappings)
        return assertInstanceOf(ProjectFormatResult.Rejected::class.java, result)
    }
}
