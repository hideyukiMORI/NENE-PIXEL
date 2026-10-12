package io.github.hideyukimori.nenepixel.core.projectformat

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentImportSource
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

internal class ProjectFormatV4CodecGoldenTest {
    @Test
    fun `independent minimal and two-frame goldens encode and decode exact chronology cells and metadata`() {
        listOf(
            "minimal-v4.hex" to ProjectFormatTestValues.v2MinimalDocument,
            "two-frames-v4.hex" to ProjectFormatV4TestValues.twoFrames(),
        ).forEach { (name, document) ->
            val golden = ProjectFormatTestValues.golden(name)
            assertArrayEquals(golden, ProjectFormatCodec.encode(document).copyBytes())
            val result =
                assertInstanceOf(
                    ProjectFormatResult.Accepted::class.java,
                    ProjectFormatCodec.decode(ProjectFormatTestValues.carrier(golden)),
                )
            assertEquals(DocumentImportSource.Current(document), result.value)
        }
    }

    @Test
    fun `sixteen frames reach current maximum while one-frame sixteen-layer stack is135 bytes smaller`() {
        val maximum = ProjectFormatV4TestValues.maximumFrames()
        val encoded = ProjectFormatCodec.encode(maximum)
        assertEquals(1_183_006, encoded.byteCount)
        assertEquals(ProjectFormatBytes.MAX_FILE_BYTE_COUNT, encoded.byteCount)
        val decoded = assertInstanceOf(ProjectFormatResult.Accepted::class.java, ProjectFormatCodec.decode(encoded))
        assertEquals(DocumentImportSource.Current(maximum), decoded.value)
        val stack = ProjectFormatCodec.encode(ProjectFormatV3TestValues.maximumLayeredDocument())
        assertEquals(1_182_871, stack.byteCount)
        assertEquals(135, encoded.byteCount - stack.byteCount)
        assertEquals(16, maximum.totalLayerCount)
    }

    @Test
    fun `old v2 v3 migration remains exact and only current re-encoding becomes v4`() {
        listOf("minimal-v2.hex", "minimal-v3.hex").forEach { name ->
            val read =
                assertInstanceOf(
                    ProjectFormatResult.Accepted::class.java,
                    ProjectFormatCodec.decode(ProjectFormatTestValues.carrier(ProjectFormatTestValues.golden(name))),
                )
            assertEquals(DocumentImportSource.Current(ProjectFormatTestValues.v2MinimalDocument), read.value)
            assertArrayEquals(
                ProjectFormatTestValues.golden("minimal-v4.hex"),
                ProjectFormatCodec.encode(ProjectFormatTestValues.v2MinimalDocument).copyBytes(),
            )
        }
    }
}
