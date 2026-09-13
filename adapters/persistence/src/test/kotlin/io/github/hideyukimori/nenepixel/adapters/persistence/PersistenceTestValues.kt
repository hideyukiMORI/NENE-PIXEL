package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGeneration
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGenerationResult
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

internal object PersistenceTestValues {
    val minimalDocument: DocumentState =
        document(
            id = "000102030405060708090a0b0c0d0e0f",
            width = 1,
            height = 1,
            revision = 0L,
            colors = intArrayOf(0x00000000, 0x11223344),
            defaultIndex = 0,
            indices = byteArrayOf(1),
        )

    fun maximumDocument(): DocumentState {
        val colors = IntArray(256) { index -> index * 0x01010100 or 0xff }
        val indices = ByteArray(256 * 256) { index -> index.toByte() }
        return document(
            id = "f0e0d0c0b0a090807060504030201000",
            width = 256,
            height = 256,
            revision = Long.MAX_VALUE,
            colors = colors,
            defaultIndex = 255,
            indices = indices,
        )
    }

    fun maximumLegacySource(): LegacyRgbaSource {
        val id = created(DocumentId.create("f0e0d0c0b0a090807060504030201000"))
        val size = CanvasSize.create(created(CanvasWidth.create(256)), created(CanvasHeight.create(256)))
        val pixels = IntArray(256 * 256) { index -> index shl Byte.SIZE_BITS or 0xff }
        return created(
            LegacyRgbaSource.createPackedRgba8888(id, created(Revision.create(Long.MAX_VALUE)), size, pixels),
        )
    }

    fun minimalLegacySource(): LegacyRgbaSource {
        val id = created(DocumentId.create("000102030405060708090a0b0c0d0e0f"))
        val size = CanvasSize.create(created(CanvasWidth.create(1)), created(CanvasHeight.create(1)))
        return created(
            LegacyRgbaSource.createPackedRgba8888(
                id,
                Revision.initial(),
                size,
                intArrayOf(0x11223344),
            ),
        )
    }

    fun generation(value: Long): RecoveryGeneration =
        when (val result = RecoveryGeneration.create(value)) {
            is RecoveryGenerationResult.Created -> result.generation
            RecoveryGenerationResult.Rejected -> error("Invalid test recovery generation: $value")
        }

    private fun document(
        id: String,
        width: Int,
        height: Int,
        revision: Long,
        colors: IntArray,
        defaultIndex: Int,
        indices: ByteArray,
    ): DocumentState {
        val documentId = created(DocumentId.create(id))
        val size = CanvasSize.create(created(CanvasWidth.create(width)), created(CanvasHeight.create(height)))
        val palette = created(Palette.create(colors.map(PixelColor::fromPackedRgba8888)))
        val definition = created(PaletteDefinition.create(palette, created(PaletteIndex.create(defaultIndex))))
        val snapshot = created(PixelSnapshot.createPackedIndices(size, created(Revision.create(revision)), indices))
        return created(DocumentState.create(documentId, definition, snapshot))
    }

    private fun <T> created(result: DomainValueResult<T>): T =
        when (result) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Invalid test value: ${result.rejection}")
        }
}
