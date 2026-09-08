package io.github.hideyukimori.nenepixel.core.projectformat

import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

internal object ProjectFormatTestValues {
    const val MINIMAL_ID: String = "000102030405060708090a0b0c0d0e0f"
    const val RECTANGULAR_ID: String = "f0e0d0c0b0a090807060504030201000"

    val minimalDocument: DocumentState =
        document(
            id = MINIMAL_ID,
            size = canvas(1, 1),
            revision = 0L,
            packedPixels = intArrayOf(0x11223344),
        )

    val rectangularDocument: DocumentState =
        document(
            id = RECTANGULAR_ID,
            size = canvas(2, 3),
            revision = Long.MAX_VALUE,
            packedPixels =
                intArrayOf(
                    0xff0000ff.toInt(),
                    0x12345600,
                    0x00ff0080,
                    0x0000ffff,
                    0xffffffff.toInt(),
                    0x00000000,
                ),
        )

    fun maximumDocument(): DocumentState {
        val size = canvas(256, 256)
        val pixels = IntArray(size.pixelCount.toInt()) { index -> deterministicPixel(index) }
        return document(MINIMAL_ID, size, Long.MAX_VALUE, pixels)
    }

    fun carrier(bytes: ByteArray): ProjectFormatBytes =
        when (val result = ProjectFormatBytes.create(bytes)) {
            is ProjectFormatResult.Accepted -> result.value
            is ProjectFormatResult.Rejected -> error("Test bytes exceeded the probe bound: ${result.rejection}")
        }

    fun golden(name: String): ByteArray {
        val resource = requireNotNull(ProjectFormatTestValues::class.java.getResource("/golden/$name"))
        val hexadecimal = resource.readText().filterNot(Char::isWhitespace)
        check(hexadecimal.length % 2 == 0)
        return hexadecimal.chunked(2).map { pair -> pair.toInt(16).toByte() }.toByteArray()
    }

    fun document(
        id: String,
        size: CanvasSize,
        revision: Long,
        packedPixels: IntArray,
    ): DocumentState {
        val documentId = created(DocumentId.create(id))
        val snapshot = PixelSnapshot.createPackedRgba8888(size, created(Revision.create(revision)), packedPixels)
        return DocumentState.create(documentId, created(snapshot))
    }

    fun canvas(
        width: Int,
        height: Int,
    ): CanvasSize = CanvasSize.create(created(CanvasWidth.create(width)), created(CanvasHeight.create(height)))

    private fun deterministicPixel(index: Int): Int =
        ((index and 0xff) shl 24) or
            (((index * 3) and 0xff) shl 16) or
            (((index * 5) and 0xff) shl 8) or
            ((index * 7) and 0xff)

    private fun <T> created(result: DomainValueResult<T>): T =
        when (result) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Test domain value was rejected: ${result.rejection}")
        }
}
