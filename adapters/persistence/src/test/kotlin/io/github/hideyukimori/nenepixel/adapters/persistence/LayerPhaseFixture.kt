package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGeneration
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGenerationResult
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentImportSource
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.layer.Layer
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerName
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.core.projectformat.ProjectFormatBytes
import io.github.hideyukimori.nenepixel.core.projectformat.ProjectFormatCodec
import io.github.hideyukimori.nenepixel.core.projectformat.ProjectFormatResult
import java.nio.charset.StandardCharsets

/** One canonical drawing-capable maximum project for the prospective #145 phase. */
internal object LayerPhaseFixture {
    private const val DOCUMENT_ID: String = "14500000000000000000000000000000"
    private const val AXIS: Int = 256
    private const val LAYER_COUNT: Int = 16
    private const val PROJECT_BYTE_COUNT: Int = 1_182_862
    private const val CANDIDATE_BYTE_COUNT: Int = 1_182_885
    private const val OPAQUE_BLACK: Int = 0x000000ff
    private const val GRAYSCALE_CHANNELS: Int = 0x01010100
    private const val PARTIAL_ALPHA: Int = 128
    private val layerName: String = "\uD83D\uDE00".repeat(32)

    fun document(): DocumentState {
        val size = CanvasSize.create(created(CanvasWidth.create(AXIS)), created(CanvasHeight.create(AXIS)))
        val palette =
            created(
                Palette.create(
                    List(AXIS) { index ->
                        PixelColor.fromPackedRgba8888(
                            if (index == 0) OPAQUE_BLACK else index * GRAYSCALE_CHANNELS or PARTIAL_ALPHA,
                        )
                    },
                ),
            )
        val definition = created(PaletteDefinition.create(palette, created(PaletteIndex.create(0))))
        val name = created(LayerName.create(layerName))
        val layers =
            (1..LAYER_COUNT).map { layerId ->
                Layer.create(
                    created(LayerId.create(layerId)),
                    name,
                    LayerVisibility.Visible,
                    created(PixelSnapshot.createFilled(size, created(PaletteIndex.create(layerId)))),
                )
            }
        return created(
            DocumentState.createLayered(
                created(DocumentId.create(DOCUMENT_ID)),
                Revision.initial(),
                definition,
                layers,
            ),
        )
    }

    fun verifiedProjectBytes(): ByteArray {
        val source = document()
        verifyFacts(source)
        val bytes = ProjectFormatCodec.encode(source).copyBytes()
        check(bytes.size == PROJECT_BYTE_COUNT)
        check(bytes.size == ProjectFormatBytes.MAX_FILE_BYTE_COUNT)
        verifyDecoded(bytes)
        return bytes
    }

    fun verifyDecoded(bytes: ByteArray) {
        check(bytes.size == PROJECT_BYTE_COUNT)
        val encoded =
            when (val result = ProjectFormatBytes.create(bytes)) {
                is ProjectFormatResult.Accepted -> result.value
                is ProjectFormatResult.Rejected -> error("Fixture bytes rejected: ${result.rejection}")
            }
        val decoded =
            when (val result = ProjectFormatCodec.decode(encoded)) {
                is ProjectFormatResult.Accepted -> result.value
                is ProjectFormatResult.Rejected -> error("Fixture decode rejected: ${result.rejection}")
            }
        check(decoded is DocumentImportSource.Current)
        verifyFacts(decoded.document)
    }

    fun verifyCandidate(document: DocumentState = document()) {
        val generation =
            when (val result = RecoveryGeneration.create(1L)) {
                is RecoveryGenerationResult.Created -> result.generation
                RecoveryGenerationResult.Rejected -> error("Fixture generation rejected")
            }
        val encoded = RecoveryRecordCodec.encodeCandidate(generation, document)
        check(encoded is RecoveryEncodeResult.Encoded)
        check(encoded.bytes.size == CANDIDATE_BYTE_COUNT)
        check(encoded.bytes.size == RecoveryRecordCodec.MAX_RECORD_BYTE_COUNT)
        val decoded = RecoveryRecordCodec.decode(encoded.bytes)
        check(decoded is RecoveryDecodeResult.Accepted)
        check(decoded.record == RecoveryRecord.Candidate(generation, DocumentImportSource.Current(document)))
    }

    private fun verifyFacts(document: DocumentState) {
        check(document.id == created(DocumentId.create(DOCUMENT_ID)))
        check(document.revision.value == 0L)
        check(document.revision.advance() is DomainValueResult.Created)
        check(document.size.width.value == AXIS && document.size.height.value == AXIS)
        check(document.definition.defaultIndex.value == 0)
        val palette = document.definition.palette
        check(palette.entryCount == AXIS)
        palette.entries().forEachIndexed { index, entry ->
            val expected = if (index == 0) OPAQUE_BLACK else index * GRAYSCALE_CHANNELS or PARTIAL_ALPHA
            check(entry.index.value == index && entry.color.toPackedRgba8888() == expected)
        }
        check(layerName.toByteArray(StandardCharsets.UTF_8).size == 128)
        check(document.layers.size == LAYER_COUNT)
        document.layers.forEachIndexed { index, layer ->
            val id = index + 1
            check(layer.id.value == id)
            check(layer.visibility == LayerVisibility.Visible)
            check(layer.name.value == layerName)
            val nameBytes = layer.name.value.toByteArray(StandardCharsets.UTF_8)
            check(nameBytes.size == 128)
            val indices = layer.snapshot.copyPackedIndices()
            val coverage = layer.snapshot.copyCoverage()
            check(indices.all { pixel -> (pixel.toInt() and 0xff) == id })
            check(coverage.all { byte -> (byte.toInt() and 0xff) == 0xff })
        }
        // The canonical load policy selects the last layer. Its index 16 differs from pencil index 0
        // at every cell, including DOWN (one cell) and all 256 cells of the diagonal.
        val top = document.layers.last()
        check(top.id.value == LAYER_COUNT)
        val topIndices = top.snapshot.copyPackedIndices()
        check((topIndices[0].toInt() and 0xff) != 0)
        check((0 until AXIS).count { (topIndices[it * AXIS + it].toInt() and 0xff) != 0 } == AXIS)
    }

    private fun <T> created(result: DomainValueResult<T>): T =
        when (result) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Invalid fixture value: ${result.rejection}")
        }
}
