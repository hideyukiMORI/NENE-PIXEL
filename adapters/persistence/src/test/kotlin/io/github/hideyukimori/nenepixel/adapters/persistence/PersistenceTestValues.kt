package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGeneration
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGenerationResult
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

internal object PersistenceTestValues {
    val minimalDocument: DocumentState =
        document(
            id = "000102030405060708090a0b0c0d0e0f",
            width = 1,
            height = 1,
            revision = 0L,
            pixels = intArrayOf(0x11223344),
        )

    fun maximumDocument(): DocumentState {
        val pixels = IntArray(256 * 256) { index -> index * 0x10203 or 0xff }
        return document(
            id = "f0e0d0c0b0a090807060504030201000",
            width = 256,
            height = 256,
            revision = Long.MAX_VALUE,
            pixels = pixels,
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
        pixels: IntArray,
    ): DocumentState {
        val documentId = created(DocumentId.create(id))
        val size = CanvasSize.create(created(CanvasWidth.create(width)), created(CanvasHeight.create(height)))
        val snapshot = created(PixelSnapshot.createPackedRgba8888(size, created(Revision.create(revision)), pixels))
        return DocumentState.create(documentId, snapshot)
    }

    private fun <T> created(result: DomainValueResult<T>): T =
        when (result) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Invalid test value: ${result.rejection}")
        }
}
