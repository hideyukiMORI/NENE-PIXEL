package io.github.hideyukimori.nenepixel.core.projectformat

internal class ProjectFormatV3LayerSpan(
    val offset: Int,
    val nameByteCount: Int,
)

internal object ProjectFormatV3Structure {
    fun scan(
        source: ProjectFormatBytes,
        header: ProjectFormatV3Header,
    ): ProjectFormatResult<List<ProjectFormatV3LayerSpan>> {
        val layerCountOffset = ProjectFormatV3Layout.layerCountOffset(header.paletteEntryCount)
        val layerCount = ProjectFormatV3Layout.unsignedByteAt(source, layerCountOffset)
        return if (layerCount in ProjectFormatV3Layout.MIN_LAYER_COUNT..ProjectFormatV3Layout.MAX_LAYER_COUNT) {
            scanLayers(
                source,
                header.size.pixelCount.toInt(),
                layerCountOffset + ProjectFormatV3Layout.LAYER_COUNT_BYTE_COUNT,
                layerCount,
            )
        } else {
            rejected(ProjectFormatRejection.InvalidLayerCount(layerCount))
        }
    }

    private fun scanLayers(
        source: ProjectFormatBytes,
        pixelCount: Int,
        firstLayerOffset: Int,
        layerCount: Int,
    ): ProjectFormatResult<List<ProjectFormatV3LayerSpan>> {
        val spans = ArrayList<ProjectFormatV3LayerSpan>(layerCount)
        var offset = firstLayerOffset
        var truncation: ProjectFormatRejection? = null
        while (truncation == null && spans.size < layerCount) {
            val requiredByteCount = offset + ProjectFormatV3Layout.LAYER_FIXED_BYTE_COUNT
            if (source.byteCount < requiredByteCount) {
                truncation = ProjectFormatRejection.Truncated(source.byteCount, requiredByteCount)
            } else {
                val nameByteCount =
                    ProjectFormatV3Layout.unsignedByteAt(source, offset + ProjectFormatV3Layout.NAME_LENGTH_OFFSET)
                spans += ProjectFormatV3LayerSpan(offset, nameByteCount)
                offset += ProjectFormatV3Layout.layerByteCount(nameByteCount, pixelCount)
            }
        }
        return truncation?.let(::rejected) ?: verifyLength(source, spans, offset + Int.SIZE_BYTES)
    }

    private fun verifyLength(
        source: ProjectFormatBytes,
        spans: List<ProjectFormatV3LayerSpan>,
        expectedByteCount: Int,
    ): ProjectFormatResult<List<ProjectFormatV3LayerSpan>> =
        when {
            source.byteCount < expectedByteCount -> {
                rejected(ProjectFormatRejection.Truncated(source.byteCount, expectedByteCount))
            }

            source.byteCount > expectedByteCount -> {
                rejected(ProjectFormatRejection.TrailingData(source.byteCount, expectedByteCount))
            }

            else -> {
                verifyChecksum(source, spans, expectedByteCount)
            }
        }

    private fun verifyChecksum(
        source: ProjectFormatBytes,
        spans: List<ProjectFormatV3LayerSpan>,
        expectedByteCount: Int,
    ): ProjectFormatResult<List<ProjectFormatV3LayerSpan>> {
        val checksumOffset = expectedByteCount - Int.SIZE_BYTES
        val computed = Crc32IsoHdlc.checksum(source, checksumOffset)
        val stored = ProjectFormatBigEndian.readInt(source, checksumOffset).toUInt()
        return if (computed == stored) {
            accepted(spans)
        } else {
            rejected(ProjectFormatRejection.ChecksumMismatch(computed, stored))
        }
    }
}
