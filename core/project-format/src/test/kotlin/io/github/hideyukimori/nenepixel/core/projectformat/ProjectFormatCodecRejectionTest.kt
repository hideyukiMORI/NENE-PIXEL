package io.github.hideyukimori.nenepixel.core.projectformat

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentImportSource
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

internal class ProjectFormatCodecRejectionTest {
    private val minimalV2: ByteArray = ProjectFormatTestValues.golden("minimal-v2.hex")

    @Test
    fun `common prefix truncation precedes magic and version`() {
        (0 until ProjectFormatV1Layout.PIXEL_OFFSET).forEach { byteCount ->
            val source = ProjectFormatTestValues.carrier(minimalV2.copyOf(byteCount))
            val rejection = rejected(ProjectFormatCodec.decode(source))
            val truncated = assertInstanceOf(ProjectFormatRejection.Truncated::class.java, rejection)
            assertEquals(byteCount, truncated.actualByteCount)
            assertEquals(ProjectFormatV1Layout.PIXEL_OFFSET, truncated.requiredByteCount)
        }
    }

    @Test
    fun `unsupported version precedes version specific truncation`() {
        val bytes = minimalV2.copyOf(40)
        ProjectFormatBigEndian.writeUnsignedShort(bytes, ProjectFormatV1Layout.VERSION_OFFSET, 3)

        val unsupported =
            assertInstanceOf(
                ProjectFormatRejection.UnsupportedVersion::class.java,
                rejected(ProjectFormatCodec.decode(ProjectFormatTestValues.carrier(bytes))),
            )
        assertEquals(3u.toUShort(), unsupported.actualVersion.value)
    }

    @Test
    fun `v2 count and default are validated before body allocation`() {
        val invalidCount =
            minimalV2.copyOf().also {
                ProjectFormatBigEndian.writeUnsignedShort(it, ProjectFormatV2Layout.PALETTE_COUNT_OFFSET, 1)
            }
        val invalidDefault =
            minimalV2.copyOf().also {
                it[ProjectFormatV2Layout.DEFAULT_INDEX_OFFSET] = 2
            }

        assertEquals(
            ProjectFormatRejection.InvalidPaletteEntryCount(1, 2, 256),
            rejected(ProjectFormatCodec.decode(ProjectFormatTestValues.carrier(invalidCount))),
        )
        assertEquals(
            ProjectFormatRejection.DefaultIndexOutsidePalette(PaletteIndex.create(2).createdValue(), 2),
            rejected(ProjectFormatCodec.decode(ProjectFormatTestValues.carrier(invalidDefault))),
        )
    }

    @Test
    fun `v2 maximum and carrier maximum remain separate`() {
        val v2Oversized = minimalV2.copyOf(ProjectFormatV2Layout.MAX_FILE_BYTE_COUNT + 1)
        val v2Rejection =
            assertInstanceOf(
                ProjectFormatRejection.ResourceLimitExceeded::class.java,
                rejected(ProjectFormatCodec.decode(ProjectFormatTestValues.carrier(v2Oversized))),
            )
        assertEquals(ProjectFormatV2Layout.MAX_FILE_BYTE_COUNT, v2Rejection.maximumByteCount)

        val v1 = ProjectFormatCodec.encodeLegacySource(ProjectFormatTestValues.maximumLegacySource())
        assertEquals(ProjectFormatBytes.MAX_FILE_BYTE_COUNT, v1.byteCount)
        assertInstanceOf(
            DocumentImportSource.Legacy::class.java,
            accepted(ProjectFormatCodec.decode(v1)),
        )
    }

    private fun accepted(result: ProjectFormatResult<*>): Any =
        when (result) {
            is ProjectFormatResult.Accepted -> result.value ?: error("Expected non-null accepted result")
            is ProjectFormatResult.Rejected -> error("Expected accepted result")
        }

    private fun rejected(result: ProjectFormatResult<*>): ProjectFormatRejection =
        when (result) {
            is ProjectFormatResult.Accepted -> error("Expected rejection")
            is ProjectFormatResult.Rejected -> result.rejection
        }
}

private fun <T> DomainValueResult<T>.createdValue(): T =
    when (this) {
        is DomainValueResult.Created -> value
        is DomainValueResult.Rejected -> error("Expected created value")
    }
