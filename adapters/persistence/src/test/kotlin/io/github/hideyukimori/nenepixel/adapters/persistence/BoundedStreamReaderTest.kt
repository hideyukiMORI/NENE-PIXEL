package io.github.hideyukimori.nenepixel.adapters.persistence

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

internal class BoundedStreamReaderTest {
    @Test
    fun `maximum bytes complete after one eof probe`() {
        val bytes = ByteArray(8) { it.toByte() }

        val result = BoundedStreamReader.read(ByteArrayInputStream(bytes), null, bytes.size)

        val accepted = result as BoundedReadResult.Bytes
        assertArrayEquals(bytes, accepted.value)
    }

    @Test
    fun `maximum plus one stops at the probe byte`() {
        val source = CountingInputStream(ByteArray(32) { it.toByte() })

        val result = BoundedStreamReader.read(source, null, 8)

        assertSame(BoundedReadResult.ResourceLimitExceeded, result)
        assertEquals(9, source.consumed)
    }

    @Test
    fun `zero progress is distinct`() {
        val result = BoundedStreamReader.read(ZeroProgressInputStream(), null, 8)

        assertSame(BoundedReadResult.ZeroProgress, result)
    }

    @Test
    fun `known early eof is premature`() {
        val result = BoundedStreamReader.read(ByteArrayInputStream(byteArrayOf(1, 2)), 3L, 8)

        assertSame(BoundedReadResult.PrematureEnd, result)
    }
}

private class CountingInputStream(
    private val bytes: ByteArray,
) : InputStream() {
    var consumed: Int = 0
        private set

    override fun read(): Int =
        if (consumed == bytes.size) {
            -1
        } else {
            bytes[consumed++].toInt() and 0xff
        }
}

private class ZeroProgressInputStream : InputStream() {
    override fun read(): Int = -1

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int = 0
}
