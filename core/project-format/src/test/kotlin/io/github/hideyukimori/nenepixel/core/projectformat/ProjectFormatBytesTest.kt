package io.github.hideyukimori.nenepixel.core.projectformat

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ProjectFormatBytesTest {
    @Test
    fun `carrier defensively owns source and bulk copies`() {
        val source = byteArrayOf(1, 2, 3)
        val carrier = accepted(ProjectFormatBytes.create(source))
        source[0] = 9
        val firstCopy = carrier.copyBytes()
        firstCopy[1] = 9

        assertArrayEquals(byteArrayOf(1, 2, 3), carrier.copyBytes())
    }

    @Test
    fun `content equality and hash use owned bytes`() {
        val first = accepted(ProjectFormatBytes.create(byteArrayOf(1, 2, 3)))
        val equal = accepted(ProjectFormatBytes.create(byteArrayOf(1, 2, 3)))
        val different = accepted(ProjectFormatBytes.create(byteArrayOf(1, 2, 4)))

        assertEquals(first, equal)
        assertEquals(first.hashCode(), equal.hashCode())
        assertNotEquals(first, different)
    }

    @Test
    fun `factory accepts probe maximum and rejects larger input before ownership`() {
        val probe = ProjectFormatBytes.create(ByteArray(ProjectFormatBytes.MAX_PROBE_BYTE_COUNT))
        val oversized = ProjectFormatBytes.create(ByteArray(ProjectFormatBytes.MAX_PROBE_BYTE_COUNT + 1))

        assertEquals(ProjectFormatBytes.MAX_PROBE_BYTE_COUNT, accepted(probe).byteCount)
        val rejection = rejected(oversized)
        assertTrue(rejection is ProjectFormatRejection.ResourceLimitExceeded)
        rejection as ProjectFormatRejection.ResourceLimitExceeded
        assertEquals(ProjectFormatBytes.MAX_PROBE_BYTE_COUNT + 1, rejection.actualByteCount)
        assertEquals(ProjectFormatBytes.MAX_PROBE_BYTE_COUNT, rejection.maximumByteCount)
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
