package io.github.hideyukimori.nenepixel.core.projectformat.palette

import io.github.hideyukimori.nenepixel.core.projectformat.palette.PaletteJsonTestValues.MINIMAL
import io.github.hideyukimori.nenepixel.core.projectformat.palette.PaletteJsonTestValues.accepted
import io.github.hideyukimori.nenepixel.core.projectformat.palette.PaletteJsonTestValues.carrier
import io.github.hideyukimori.nenepixel.core.projectformat.palette.PaletteJsonTestValues.decode
import io.github.hideyukimori.nenepixel.core.projectformat.palette.PaletteJsonTestValues.rejected
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

internal class PaletteJsonBytesTest {
    @Test
    fun `carrier owns input and returns defensive copies with value equality`() {
        val source = MINIMAL.encodeToByteArray()
        val bytes = carrier(source)
        source.fill(0)
        bytes.copyBytes().fill(0)
        assertArrayEquals(MINIMAL.encodeToByteArray(), bytes.copyBytes())
        assertEquals(bytes, carrier(MINIMAL.encodeToByteArray()))
        assertEquals(bytes.hashCode(), carrier(MINIMAL.encodeToByteArray()).hashCode())
    }

    @Test
    fun `exact envelope accepts and maximum-plus-one rejects before invalid UTF8`() {
        val maximum = MINIMAL.padEnd(PaletteJsonBytes.MAX_FILE_BYTE_COUNT)
        assertEquals(accepted(decode(MINIMAL)), accepted(decode(maximum)))
        val probe = carrier(ByteArray(PaletteJsonBytes.MAX_PROBE_BYTE_COUNT) { 0xc0.toByte() })
        val rejected =
            assertInstanceOf(
                PaletteJsonRejection.ResourceLimitExceeded::class.java,
                rejected(PaletteJsonCodec.decode(probe)),
            )
        assertEquals(16_385, rejected.actualByteCount)
        assertEquals(16_384, rejected.maximumByteCount)
    }

    @Test
    fun `carrier refuses above probe without retaining input`() {
        val rejected =
            assertInstanceOf(
                PaletteJsonRejection.ResourceLimitExceeded::class.java,
                rejected(PaletteJsonBytes.create(ByteArray(16_386))),
            )
        assertEquals(16_386, rejected.actualByteCount)
        assertEquals(16_385, rejected.maximumByteCount)
    }
}
