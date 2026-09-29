package io.github.hideyukimori.nenepixel.core.projectformat

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

internal class ProjectFormatV3Header(
    val id: DocumentId,
    val size: CanvasSize,
    val revision: Revision,
    val definition: PaletteDefinition,
) {
    val paletteEntryCount: Int
        get() = definition.palette.entryCount
}

internal object ProjectFormatV3HeaderDecoder {
    private const val HEXADECIMAL: String = "0123456789abcdef"
    private const val NIBBLE_SHIFT: Int = 4
    private const val NIBBLE_MASK: Int = 0x0f

    fun decode(source: ProjectFormatBytes): ProjectFormatResult<ProjectFormatV3Header> =
        decodeCanvas(source).andThen { size ->
            decodeRevision(source).andThen { revision ->
                decodePaletteHeader(source).andThen { defaultIndex ->
                    requirePaletteAndLayerCount(source).andThen {
                        accepted(
                            ProjectFormatV3Header(
                                decodeDocumentId(source),
                                size,
                                revision,
                                createDefinition(source, defaultIndex),
                            ),
                        )
                    }
                }
            }
        }

    private fun decodeCanvas(source: ProjectFormatBytes): ProjectFormatResult<CanvasSize> {
        val widthResult =
            CanvasWidth.create(ProjectFormatBigEndian.readUnsignedShort(source, ProjectFormatV2Layout.WIDTH_OFFSET))
        val heightResult =
            CanvasHeight.create(ProjectFormatBigEndian.readUnsignedShort(source, ProjectFormatV2Layout.HEIGHT_OFFSET))
        return if (widthResult is DomainValueResult.Created && heightResult is DomainValueResult.Created) {
            accepted(CanvasSize.create(widthResult.value, heightResult.value))
        } else {
            rejected(ProjectFormatRejection.InvalidCanvas)
        }
    }

    private fun decodeRevision(source: ProjectFormatBytes): ProjectFormatResult<Revision> =
        when (
            val result =
                Revision.create(ProjectFormatBigEndian.readLong(source, ProjectFormatV2Layout.REVISION_OFFSET))
        ) {
            is DomainValueResult.Created -> accepted(result.value)
            is DomainValueResult.Rejected -> rejected(ProjectFormatRejection.InvalidRevision)
        }

    private fun decodePaletteHeader(source: ProjectFormatBytes): ProjectFormatResult<PaletteIndex> {
        val count = paletteEntryCount(source)
        val defaultIndex =
            created(
                PaletteIndex.create(
                    ProjectFormatV3Layout.unsignedByteAt(source, ProjectFormatV2Layout.DEFAULT_INDEX_OFFSET),
                ),
            )
        return when {
            count !in ProjectFormatV2Layout.MIN_PALETTE_ENTRY_COUNT..ProjectFormatV2Layout.MAX_PALETTE_ENTRY_COUNT -> {
                rejected(
                    ProjectFormatRejection.InvalidPaletteEntryCount(
                        count,
                        ProjectFormatV2Layout.MIN_PALETTE_ENTRY_COUNT,
                        ProjectFormatV2Layout.MAX_PALETTE_ENTRY_COUNT,
                    ),
                )
            }

            defaultIndex.value >= count -> {
                rejected(ProjectFormatRejection.DefaultIndexOutsidePalette(defaultIndex, count))
            }

            else -> {
                accepted(defaultIndex)
            }
        }
    }

    private fun requirePaletteAndLayerCount(source: ProjectFormatBytes): ProjectFormatResult<Unit> {
        val requiredByteCount =
            ProjectFormatV3Layout.layerCountOffset(paletteEntryCount(source)) +
                ProjectFormatV3Layout.LAYER_COUNT_BYTE_COUNT
        return if (source.byteCount < requiredByteCount) {
            rejected(ProjectFormatRejection.Truncated(source.byteCount, requiredByteCount))
        } else {
            accepted(Unit)
        }
    }

    private fun createDefinition(
        source: ProjectFormatBytes,
        defaultIndex: PaletteIndex,
    ): PaletteDefinition {
        val colors =
            List(paletteEntryCount(source)) { entry ->
                PixelColor.fromPackedRgba8888(
                    ProjectFormatBigEndian.readInt(
                        source,
                        ProjectFormatV2Layout.PALETTE_OFFSET + entry * ProjectFormatV2Layout.PALETTE_ENTRY_BYTE_COUNT,
                    ),
                )
            }
        return created(PaletteDefinition.create(created(Palette.create(colors)), defaultIndex))
    }

    private fun decodeDocumentId(source: ProjectFormatBytes): DocumentId {
        val value =
            buildString(ProjectFormatV2Layout.DOCUMENT_ID_BYTE_COUNT * 2) {
                repeat(ProjectFormatV2Layout.DOCUMENT_ID_BYTE_COUNT) { byteIndex ->
                    val byteOffset = ProjectFormatV2Layout.DOCUMENT_ID_OFFSET + byteIndex
                    val byte = ProjectFormatV3Layout.unsignedByteAt(source, byteOffset)
                    append(HEXADECIMAL[byte ushr NIBBLE_SHIFT])
                    append(HEXADECIMAL[byte and NIBBLE_MASK])
                }
            }
        return created(DocumentId.create(value))
    }

    private fun paletteEntryCount(source: ProjectFormatBytes): Int =
        ProjectFormatBigEndian.readUnsignedShort(source, ProjectFormatV2Layout.PALETTE_COUNT_OFFSET)

    private fun <T> created(result: DomainValueResult<T>): T =
        when (result) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Validated v3 header failed domain mapping: ${result.rejection}")
        }
}
