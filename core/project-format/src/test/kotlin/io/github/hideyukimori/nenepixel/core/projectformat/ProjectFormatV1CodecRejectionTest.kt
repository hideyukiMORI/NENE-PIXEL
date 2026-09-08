package io.github.hideyukimori.nenepixel.core.projectformat

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class ProjectFormatV1CodecRejectionTest {
    private val minimal: ByteArray = ProjectFormatTestValues.golden("minimal-v1.hex")

    @Test
    fun `maximum plus one reaches codec resource rejection`() {
        val source = ProjectFormatTestValues.carrier(ByteArray(ProjectFormatBytes.MAX_PROBE_BYTE_COUNT))
        val rejection = rejected(ProjectFormatV1Codec.decode(source))

        val resource = assertInstanceOf(ProjectFormatRejection.ResourceLimitExceeded::class.java, rejection)
        assertEquals(ProjectFormatBytes.MAX_PROBE_BYTE_COUNT, resource.actualByteCount)
        assertEquals(ProjectFormatBytes.MAX_FILE_BYTE_COUNT, resource.maximumByteCount)
    }

    @Test
    fun `every byte truncation before the minimum length is typed`() {
        minimal.indices.forEach { byteCount ->
            val rejection = rejected(ProjectFormatV1Codec.decode(carrier(minimal.copyOf(byteCount))))
            val truncated = assertInstanceOf(ProjectFormatRejection.Truncated::class.java, rejection)
            assertEquals(byteCount, truncated.actualByteCount)
            val required =
                if (byteCount < ProjectFormatV1Layout.PIXEL_OFFSET) {
                    ProjectFormatV1Layout.PIXEL_OFFSET
                } else {
                    minimal.size
                }
            assertEquals(required, truncated.requiredByteCount)
        }
    }

    @Test
    fun `bad magic and unsupported U16 versions are distinct`() {
        val badMagic = minimal.copyOf().also { bytes -> bytes[0] = 0 }
        assertSame(ProjectFormatRejection.InvalidMagic, rejected(decode(badMagic)))

        listOf(0, 2, UShort.MAX_VALUE.toInt()).forEach { wireVersion ->
            val unsupportedBytes =
                minimal.copyOf().also { bytes ->
                    writeUnsignedShort(bytes, ProjectFormatV1Layout.VERSION_OFFSET, wireVersion)
                }
            val unsupported =
                assertInstanceOf(
                    ProjectFormatRejection.UnsupportedVersion::class.java,
                    rejected(decode(unsupportedBytes)),
                )
            assertEquals(wireVersion.toUShort(), unsupported.actualVersion.value)
        }
    }

    @Test
    fun `invalid axes area and unsigned revision are typed`() {
        val zeroWidth =
            minimal.copyOf().also { bytes ->
                writeUnsignedShort(bytes, ProjectFormatV1Layout.WIDTH_OFFSET, 0)
            }
        val excessiveArea =
            minimal.copyOf().also { bytes ->
                writeUnsignedShort(bytes, ProjectFormatV1Layout.HEIGHT_OFFSET, 257)
            }
        val highBitRevision =
            minimal.copyOf().also { bytes ->
                bytes[ProjectFormatV1Layout.REVISION_OFFSET] = 0x80.toByte()
            }

        assertSame(ProjectFormatRejection.InvalidCanvas, rejected(decode(zeroWidth)))
        assertSame(ProjectFormatRejection.InvalidCanvas, rejected(decode(excessiveArea)))
        assertSame(ProjectFormatRejection.InvalidRevision, rejected(decode(highBitRevision)))
    }

    @Test
    fun `trailing byte is distinct from resource excess`() {
        val withTrailingByte = minimal + 0
        val rejection = rejected(decode(withTrailingByte))
        val trailing = assertInstanceOf(ProjectFormatRejection.TrailingData::class.java, rejection)

        assertEquals(minimal.size + 1, trailing.actualByteCount)
        assertEquals(minimal.size, trailing.expectedByteCount)
    }

    @Test
    fun `header and pixel corruption are checksum mismatches`() {
        val identity = minimal.copyOf().also { bytes -> bytes[ProjectFormatV1Layout.DOCUMENT_ID_OFFSET] = 0x7f }
        val pixel = minimal.copyOf().also { bytes -> bytes[ProjectFormatV1Layout.PIXEL_OFFSET] = 0x7f }

        assertInstanceOf(ProjectFormatRejection.ChecksumMismatch::class.java, rejected(decode(identity)))
        assertInstanceOf(ProjectFormatRejection.ChecksumMismatch::class.java, rejected(decode(pixel)))
    }

    @Test
    fun `stored checksum byte order is interpreted as unsigned big endian`() {
        val corrupted = minimal.copyOf()
        val checksumOffset = corrupted.size - Int.SIZE_BYTES
        corrupted[checksumOffset] = 0x80.toByte()
        corrupted[checksumOffset + 1] = 0
        corrupted[checksumOffset + 2] = 0
        corrupted[checksumOffset + 3] = 1

        val mismatch =
            assertInstanceOf(ProjectFormatRejection.ChecksumMismatch::class.java, rejected(decode(corrupted)))
        assertEquals(0x80000001u, mismatch.storedChecksum)
        assertEquals(0xeeecf0a6u, mismatch.computedChecksum)
    }

    @Test
    fun `pixel mapper is unreachable until structure length and checksum pass`() {
        var mapperCalls = 0
        val mapper =
            ProjectFormatV1DomainMapper { _, _ ->
                mapperCalls += 1
                ProjectFormatTestValues.minimalDocument
            }
        val decoder = ProjectFormatV1Decoder(mapper)
        val invalidInputs =
            listOf(
                minimal.copyOf(10),
                minimal.copyOf().also { bytes -> bytes[0] = 0 },
                minimal.copyOf(minimal.size - 1),
                minimal.copyOf().also { bytes -> bytes[ProjectFormatV1Layout.PIXEL_OFFSET] = 0 },
            )

        invalidInputs.forEach { bytes -> rejected(decoder.decode(carrier(bytes))) }
        assertEquals(0, mapperCalls)
        assertEquals(ProjectFormatTestValues.minimalDocument, accepted(decoder.decode(carrier(minimal))))
        assertEquals(1, mapperCalls)
    }

    private fun decode(bytes: ByteArray): ProjectFormatResult<DocumentState> =
        ProjectFormatV1Codec.decode(carrier(bytes))

    private fun carrier(bytes: ByteArray): ProjectFormatBytes = ProjectFormatTestValues.carrier(bytes)

    private fun writeUnsignedShort(
        destination: ByteArray,
        offset: Int,
        value: Int,
    ) {
        ProjectFormatBigEndian.writeUnsignedShort(destination, offset, value)
    }

    private fun <T> accepted(result: ProjectFormatResult<T>): T =
        when (result) {
            is ProjectFormatResult.Accepted -> result.value
            is ProjectFormatResult.Rejected -> error("Expected accepted result, got ${result.rejection}")
        }

    private fun rejected(result: ProjectFormatResult<*>): ProjectFormatRejection =
        when (result) {
            is ProjectFormatResult.Accepted -> error("Expected rejection")
            is ProjectFormatResult.Rejected -> result.rejection
        }
}
