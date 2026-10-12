package io.github.hideyukimori.nenepixel.core.projectformat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

internal class ProjectFormatV4CodecRejectionTest {
    @Test
    fun `unsigned frame ID duration and globally duplicate IDs reject without narrowing or substitution`() {
        listOf(
            Fault(54, 0, ProjectFormatRejection.InvalidFrameId(0)),
            Fault(54, Int.MIN_VALUE, ProjectFormatRejection.InvalidFrameId(2_147_483_648L)),
            Fault(77, 4, ProjectFormatRejection.DuplicateFrameId(4)),
            Fault(58, 9, ProjectFormatRejection.InvalidFrameDuration(0, 9)),
            Fault(58, 60_001, ProjectFormatRejection.InvalidFrameDuration(0, 60_001)),
            Fault(58, -1, ProjectFormatRejection.InvalidFrameDuration(0, 4_294_967_295L)),
            Fault(86, 7, ProjectFormatRejection.DuplicateLayerId(7)),
            Fault(63, Int.MIN_VALUE, ProjectFormatRejection.InvalidLayerId(2_147_483_648L)),
        ).forEach { fault ->
            val bytes = ProjectFormatTestValues.golden("two-frames-v4.hex")
            ProjectFormatBigEndian.writeInt(bytes, fault.offset, fault.value)
            assertEquals(fault.expected, reject(ProjectFormatV4TestValues.checksum(bytes)))
        }
    }

    @Test
    fun `count flags and name byte bounds are admitted before accepting payload spans`() {
        listOf(
            ByteFault(53, 0, ProjectFormatRejection.InvalidFrameCount(0)),
            ByteFault(53, 17, ProjectFormatRejection.InvalidFrameCount(17)),
            ByteFault(62, 0, ProjectFormatRejection.InvalidLayerCount(0)),
            ByteFault(62, 17, ProjectFormatRejection.InvalidLayerCount(17)),
            ByteFault(67, 2, ProjectFormatRejection.UnknownLayerFlags(0, 2)),
            ByteFault(68, 129, ProjectFormatRejection.InvalidLayerName(0)),
        ).forEach { fault ->
            val bytes = ProjectFormatTestValues.golden("two-frames-v4.hex")
            bytes[fault.offset] = fault.value.toByte()
            assertEquals(fault.expected, reject(ProjectFormatV4TestValues.checksum(bytes)))
        }
    }

    @Test
    fun `common prefix version and v4 complete span diagnostics retain exact precedence`() {
        val bytes = ProjectFormatTestValues.golden("two-frames-v4.hex")
        assertEquals(ProjectFormatRejection.Truncated(37, 38), reject(bytes.copyOf(37)))
        assertEquals(ProjectFormatRejection.Truncated(40, 41), reject(bytes.copyOf(40)))
        assertEquals(ProjectFormatRejection.Truncated(99, 103), reject(bytes.copyOf(99)))
        assertEquals(ProjectFormatRejection.TrailingData(104, 103), reject(bytes.copyOf(104)))
        listOf(0, 5).forEach { version ->
            val unknown = bytes.copyOf()
            ProjectFormatBigEndian.writeUnsignedShort(unknown, 8, version)
            assertEquals(
                ProjectFormatRejection.UnsupportedVersion(ProjectFormatVersion.fromWireValue(version)),
                reject(unknown),
            )
        }
        val badPixelAndCrc = bytes.copyOf().also { it[98] = 3 }
        assertInstanceOf(ProjectFormatRejection.ChecksumMismatch::class.java, reject(badPixelAndCrc))
    }

    @Test
    fun `current maximum probe rejects before dispatch and two bytes more reject at carrier`() {
        val maximum = ProjectFormatCodec.encode(ProjectFormatV4TestValues.maximumFrames()).copyBytes()
        assertEquals(
            ProjectFormatRejection.ResourceLimitExceeded(1_183_007, 1_183_006),
            reject(maximum.copyOf(maximum.size + 1)),
        )
        val rejected =
            assertInstanceOf(
                ProjectFormatResult.Rejected::class.java,
                ProjectFormatBytes.create(
                    maximum.copyOf(maximum.size + 2),
                ),
            )
        assertEquals(ProjectFormatRejection.ResourceLimitExceeded(1_183_008, 1_183_007), rejected.rejection)
    }

    private fun reject(bytes: ByteArray): ProjectFormatRejection =
        assertInstanceOf(
            ProjectFormatResult.Rejected::class.java,
            ProjectFormatCodec.decode(ProjectFormatTestValues.carrier(bytes)),
        ).rejection

    private data class Fault(
        val offset: Int,
        val value: Int,
        val expected: ProjectFormatRejection,
    )

    private data class ByteFault(
        val offset: Int,
        val value: Int,
        val expected: ProjectFormatRejection,
    )
}
