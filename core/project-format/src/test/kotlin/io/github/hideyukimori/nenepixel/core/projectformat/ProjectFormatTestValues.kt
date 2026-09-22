package io.github.hideyukimori.nenepixel.core.projectformat

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.LegacyRgbaSource
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

internal object ProjectFormatTestValues {
    const val MINIMAL_ID: String = "000102030405060708090a0b0c0d0e0f"
    const val RECTANGULAR_ID: String = "f0e0d0c0b0a090807060504030201000"
    private const val V2_GOLDEN_ID: String = "00000000000000000000000000000001"

    val minimalDocument: DocumentState =
        indexedDocument(
            id = MINIMAL_ID,
            size = canvas(1, 1),
            revision = 0L,
            colors = intArrayOf(0x00000000, 0xff0000ff.toInt()),
            indices = byteArrayOf(1),
        )

    val rectangularDocument: DocumentState =
        indexedDocument(
            id = RECTANGULAR_ID,
            size = canvas(3, 2),
            revision = Long.MAX_VALUE,
            colors =
                intArrayOf(
                    0x11223300,
                    0xff0000ff.toInt(),
                    0xff0000ff.toInt(),
                ),
            indices = byteArrayOf(0, 1, 2, 2, 1, 0),
        )

    val v2MinimalDocument: DocumentState =
        indexedDocument(V2_GOLDEN_ID, canvas(1, 1), 0L, intArrayOf(0x00000000, 0xff0000ff.toInt()), byteArrayOf(1))

    val v2RectangularDocument: DocumentState =
        indexedDocument(
            V2_GOLDEN_ID,
            canvas(3, 2),
            0L,
            intArrayOf(0x11223300, 0xff0000ff.toInt(), 0xff0000ff.toInt()),
            byteArrayOf(0, 1, 2, 2, 1, 0),
            defaultIndex = 2,
        )

    fun maximumDocument(): DocumentState {
        val size = canvas(256, 256)
        val colors = IntArray(256) { index -> deterministicPixel(index) }
        val indices = ByteArray(size.pixelCount.toInt()) { index -> (index and 0xff).toByte() }
        return indexedDocument(MINIMAL_ID, size, Long.MAX_VALUE, colors, indices)
    }

    fun minimalLegacySource(): LegacyRgbaSource = legacySource(MINIMAL_ID, canvas(1, 1), 0L, intArrayOf(0x11223344))

    fun rectangularLegacySource(): LegacyRgbaSource =
        legacySource(
            RECTANGULAR_ID,
            canvas(2, 3),
            Long.MAX_VALUE,
            intArrayOf(0xff0000ff.toInt(), 0x12345600, 0x00ff0080, 0x0000ffff, 0xffffffff.toInt(), 0x00000000),
        )

    fun maximumLegacySource(): LegacyRgbaSource {
        val size = canvas(256, 256)
        val pixels = IntArray(size.pixelCount.toInt()) { index -> deterministicPixel(index) }
        return legacySource(
            MINIMAL_ID,
            size,
            Long.MAX_VALUE,
            pixels,
        )
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

    private fun indexedDocument(
        id: String,
        size: CanvasSize,
        revision: Long,
        colors: IntArray,
        indices: ByteArray,
        defaultIndex: Int = 0,
    ): DocumentState {
        val documentId = created(DocumentId.create(id))
        val palette = created(Palette.create(colors.map(PixelColor::fromPackedRgba8888)))
        val definition = created(PaletteDefinition.create(palette, created(PaletteIndex.create(defaultIndex))))
        val snapshot = created(PixelSnapshot.createPackedIndices(size, created(Revision.create(revision)), indices))
        return created(DocumentState.create(documentId, definition, snapshot))
    }

    private fun legacySource(
        id: String,
        size: CanvasSize,
        revision: Long,
        packedPixels: IntArray,
    ): LegacyRgbaSource =
        created(
            LegacyRgbaSource.createPackedRgba8888(
                created(DocumentId.create(id)),
                created(Revision.create(revision)),
                size,
                packedPixels,
            ),
        )

    private fun <T> created(result: DomainValueResult<T>): T =
        when (result) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Test domain value was rejected: ${result.rejection}")
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
}
