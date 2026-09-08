package io.github.hideyukimori.nenepixel.core.projectformat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class Crc32IsoHdlcTest {
    @Test
    fun `CRC32 ISO HDLC matches the canonical check vector`() {
        assertEquals(0xcbf43926u, Crc32IsoHdlc.checksum("123456789".encodeToByteArray()))
    }
}
