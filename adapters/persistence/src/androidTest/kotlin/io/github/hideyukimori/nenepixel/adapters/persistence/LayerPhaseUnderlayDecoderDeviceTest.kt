package io.github.hideyukimori.nenepixel.adapters.persistence

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.security.MessageDigest

/** Functional fixture admission only; no timing sample or performance verdict is produced. */
@RunWith(AndroidJUnit4::class)
public class LayerPhaseUnderlayDecoderDeviceTest {
    @Test
    public fun packagedPngDecodesToThePinnedRgbaPixels() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("p4LayerFixtureCheck") == "true")
        val bytes =
            InstrumentationRegistry
                .getInstrumentation()
                .context.assets
                .open("underlay-grid.png")
                .use { it.readBytes() }
        assertEquals(184_323, bytes.size)
        assertEquals("05efb3fc8edf43f45dc5a8b7cae3be148680c5694b47c47d1cf4eb7cbe26c6fb", sha256(bytes))

        val result = AndroidBitmapReferenceImageDecoder.decode(bytes)
        assertTrue("expected Decoded, was $result", result is ReferenceImageDecodeResult.Decoded)
        val decoded = result as ReferenceImageDecodeResult.Decoded
        assertEquals(1024, decoded.width)
        assertEquals(1024, decoded.height)
        assertEquals(1024 * 1024, decoded.packedRgba8888.size)
        val packed = ByteBuffer.allocate(decoded.packedRgba8888.size * 4)
        decoded.packedRgba8888.forEach { packed.putInt(it) }
        assertEquals("f107eb10700c55df2cb3a4dd1b2723b14bc773dcd59866f4a65a5b94d583aa78", sha256(packed.array()))
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
            (it.toInt() and 0xFF).toString(16).padStart(2, '0')
        }
}
