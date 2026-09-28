package io.github.hideyukimori.nenepixel.core.projectformat

import io.github.hideyukimori.nenepixel.core.domain.layer.Layer
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerName
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import kotlin.text.CharacterCodingException

internal class ProjectFormatV3LayerDecoder(
    private val source: ProjectFormatBytes,
    private val header: ProjectFormatV3Header,
) {
    private val pixelCount: Int = header.size.pixelCount.toInt()

    fun decode(spans: List<ProjectFormatV3LayerSpan>): ProjectFormatResult<List<Layer>> {
        val layers = ArrayList<Layer>(spans.size)
        var rejection: ProjectFormatRejection? = null
        for ((layerIndex, span) in spans.withIndex()) {
            when (val result = decodeLayer(layerIndex, span, layers)) {
                is ProjectFormatResult.Accepted -> layers += result.value
                is ProjectFormatResult.Rejected -> rejection = result.rejection
            }
            if (rejection != null) break
        }
        return rejection?.let(::rejected) ?: accepted(layers)
    }

    private fun decodeLayer(
        layerIndex: Int,
        span: ProjectFormatV3LayerSpan,
        previous: List<Layer>,
    ): ProjectFormatResult<Layer> =
        decodeId(span, previous).andThen { id ->
            decodeVisibility(layerIndex, span).andThen { visibility ->
                decodeName(layerIndex, span).andThen { name ->
                    decodeSnapshot(layerIndex, span).andThen { snapshot ->
                        accepted(Layer.create(id, name, visibility, snapshot))
                    }
                }
            }
        }

    private fun decodeId(
        span: ProjectFormatV3LayerSpan,
        previous: List<Layer>,
    ): ProjectFormatResult<LayerId> {
        val wireValue = ProjectFormatBigEndian.readInt(source, span.offset).toLong() and U32_MASK
        return when {
            wireValue !in ProjectFormatV3Layout.MIN_LAYER_ID..ProjectFormatV3Layout.MAX_LAYER_ID -> {
                rejected(ProjectFormatRejection.InvalidLayerId(wireValue))
            }

            previous.any { layer -> layer.id.value.toLong() == wireValue } -> {
                rejected(ProjectFormatRejection.DuplicateLayerId(wireValue.toInt()))
            }

            else -> {
                accepted(created(LayerId.create(wireValue.toInt())))
            }
        }
    }

    private fun decodeVisibility(
        layerIndex: Int,
        span: ProjectFormatV3LayerSpan,
    ): ProjectFormatResult<LayerVisibility> {
        val flags = ProjectFormatV3Layout.unsignedByteAt(source, span.offset + ProjectFormatV3Layout.FLAGS_OFFSET)
        return when {
            flags and ProjectFormatV3Layout.VISIBLE_FLAG.inv() != 0 -> {
                rejected(ProjectFormatRejection.UnknownLayerFlags(layerIndex, flags))
            }

            flags == ProjectFormatV3Layout.VISIBLE_FLAG -> {
                accepted(LayerVisibility.Visible)
            }

            else -> {
                accepted(LayerVisibility.Hidden)
            }
        }
    }

    private fun decodeName(
        layerIndex: Int,
        span: ProjectFormatV3LayerSpan,
    ): ProjectFormatResult<LayerName> =
        if (span.nameByteCount > ProjectFormatV3Layout.MAX_NAME_BYTE_COUNT) {
            rejected(ProjectFormatRejection.InvalidLayerName(layerIndex))
        } else {
            decodeUtf8(layerIndex, readBytes(span.offset + ProjectFormatV3Layout.NAME_OFFSET, span.nameByteCount))
                .andThen { text ->
                    when (val result = LayerName.create(text)) {
                        is DomainValueResult.Created -> accepted(result.value)
                        is DomainValueResult.Rejected -> rejected(ProjectFormatRejection.InvalidLayerName(layerIndex))
                    }
                }
        }

    private fun decodeUtf8(
        layerIndex: Int,
        bytes: ByteArray,
    ): ProjectFormatResult<String> =
        try {
            accepted(bytes.decodeToString(throwOnInvalidSequence = true))
        } catch (_: CharacterCodingException) {
            rejected(ProjectFormatRejection.InvalidLayerName(layerIndex))
        }

    private fun decodeSnapshot(
        layerIndex: Int,
        span: ProjectFormatV3LayerSpan,
    ): ProjectFormatResult<PixelSnapshot> {
        val coverageOffset = span.offset + ProjectFormatV3Layout.NAME_OFFSET + span.nameByteCount
        val coverageByteCount = ProjectFormatV3Layout.coverageByteCount(pixelCount)
        val coverage = readBytes(coverageOffset, coverageByteCount)
        val indices = readBytes(coverageOffset + coverageByteCount, pixelCount)
        return when (val result = PixelSnapshot.createPackedCells(header.size, indices, coverage)) {
            is DomainValueResult.Created -> validatePalette(indices).andThen { accepted(result.value) }
            is DomainValueResult.Rejected -> rejected(ProjectFormatRejection.InvalidCoverage(layerIndex))
        }
    }

    private fun validatePalette(indices: ByteArray): ProjectFormatResult<Unit> {
        val entryCount = header.paletteEntryCount
        val pixelIndex = indices.indexOfFirst { index -> (index.toInt() and U8_MASK) >= entryCount }
        return if (pixelIndex < 0) {
            accepted(Unit)
        } else {
            rejected(
                ProjectFormatRejection.PixelIndexOutsidePalette(
                    ProjectFormatV3Layout.position(header.size, pixelIndex),
                    created(PaletteIndex.create(indices[pixelIndex].toInt() and U8_MASK)),
                    entryCount,
                ),
            )
        }
    }

    private fun readBytes(
        offset: Int,
        byteCount: Int,
    ): ByteArray = ByteArray(byteCount) { index -> source.byteAt(offset + index) }

    private fun <T> created(result: DomainValueResult<T>): T =
        when (result) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Validated v3 layer failed domain mapping: ${result.rejection}")
        }

    private companion object {
        const val U8_MASK: Int = 0xff
        const val U32_MASK: Long = 0xffff_ffffL
    }
}
