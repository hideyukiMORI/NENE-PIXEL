package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.projectformat.ProjectFormatBytes
import io.github.hideyukimori.nenepixel.core.projectformat.ProjectFormatCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.util.HexFormat

/** Distinct v4 maximum-byte correctness identity; not the historical v3 drawing/measurement asset. */
internal class FrameFormatMaximumFixtureTest {
    @Test
    fun `sixteen frames have sixteen logical records and exactly current max file Candidate bytes`() {
        val document = PersistenceTestValues.maximumFramesDocument()
        val bytes = ProjectFormatCodec.encode(document)
        assertEquals(16, document.frames.size)
        assertEquals(16, document.totalLayerCount)
        assertEquals(1_183_006, bytes.byteCount)
        assertEquals(ProjectFormatBytes.MAX_FILE_BYTE_COUNT, bytes.byteCount)
        val hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.copyBytes()))
        assertEquals("afd2561493ace80955ecd37472e3d7c54e00728636004ae934fa259e6bf57b38", hash)
        val candidate =
            assertInstanceOf(
                RecoveryEncodeResult.Encoded::class.java,
                RecoveryRecordCodec.encodeCandidate(PersistenceTestValues.generation(1), document),
            )
        assertEquals(1_183_029, candidate.bytes.size)
        assertEquals(
            "ad506b7a2d034fc5935d773086f4a3b4ad0cce25f0b1859d575ed3cd5de4d145",
            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(candidate.bytes)),
        )
    }
}
