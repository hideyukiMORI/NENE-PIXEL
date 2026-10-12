package io.github.hideyukimori.nenepixel.core.projectformat

import io.github.hideyukimori.nenepixel.core.domain.layer.LayerName
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import kotlin.text.CharacterCodingException

/** All layer semantics are admitted before the mapper is allowed to construct its first snapshot. */
internal class ProjectFormatV4LayerValidation(
    private val source: ProjectFormatBytes,
    private val header: ProjectFormatIndexedHeader,
) {
    fun validate(frames: List<ProjectFormatV4FrameSpan>): ProjectFormatResult<List<ProjectFormatV4ValidatedFrame>> {
        val admitted = ArrayList<ProjectFormatV4ValidatedFrame>(frames.size)
        var rejection: ProjectFormatRejection? = null
        for (frame in frames) {
            when (val result = validateFrame(frame)) {
                is ProjectFormatResult.Accepted -> admitted.add(result.value)
                is ProjectFormatResult.Rejected -> rejection = result.rejection
            }
            if (rejection != null) break
        }
        return rejection?.let(::rejected) ?: accepted(admitted)
    }

    private fun validateFrame(frame: ProjectFormatV4FrameSpan): ProjectFormatResult<ProjectFormatV4ValidatedFrame> {
        val layers = ArrayList<ProjectFormatV4ValidatedLayer>(frame.layers.size)
        var rejection: ProjectFormatRejection? = null
        for (span in frame.layers) {
            when (val result = validateLayer(span)) {
                is ProjectFormatResult.Accepted -> layers.add(result.value)
                is ProjectFormatResult.Rejected -> rejection = result.rejection
            }
            if (rejection != null) break
        }
        return rejection?.let(::rejected) ?: accepted(ProjectFormatV4ValidatedFrame(frame, layers))
    }

    private fun validateLayer(span: ProjectFormatV4LayerSpan): ProjectFormatResult<ProjectFormatV4ValidatedLayer> =
        name(span).andThen { name -> pixels(span).andThen { accepted(ProjectFormatV4ValidatedLayer(span, name)) } }

    private fun name(span: ProjectFormatV4LayerSpan): ProjectFormatResult<LayerName> {
        val count = ProjectFormatV3Layout.unsignedByteAt(source, span.offset + ProjectFormatV3Layout.NAME_LENGTH_OFFSET)
        val bytes = ByteArray(count) { source.byteAt(span.offset + ProjectFormatV3Layout.NAME_OFFSET + it) }
        return try {
            when (val name = LayerName.create(bytes.decodeToString(throwOnInvalidSequence = true))) {
                is DomainValueResult.Created -> accepted(name.value)
                is DomainValueResult.Rejected -> rejected(ProjectFormatRejection.InvalidLayerName(span.index))
            }
        } catch (_: CharacterCodingException) {
            rejected(ProjectFormatRejection.InvalidLayerName(span.index))
        }
    }

    private fun pixels(span: ProjectFormatV4LayerSpan): ProjectFormatResult<Unit> {
        val nameBytes =
            ProjectFormatV3Layout.unsignedByteAt(
                source,
                span.offset + ProjectFormatV3Layout.NAME_LENGTH_OFFSET,
            )
        val coverageStart = span.offset + ProjectFormatV3Layout.NAME_OFFSET + nameBytes
        val count = header.size.pixelCount.toInt()
        val indexStart = coverageStart + ProjectFormatV3Layout.coverageByteCount(count)
        val padding = paddingRejection(span.index, coverageStart, count)
        if (padding != null) return rejected(padding)
        var rejection: ProjectFormatRejection? = null
        var pixel = 0
        while (pixel < count && rejection == null) {
            val covered =
                (source.byteAt(coverageStart + pixel / Byte.SIZE_BITS).toInt() ushr (pixel % Byte.SIZE_BITS)) and 1 == 1
            val index = ProjectFormatV3Layout.unsignedByteAt(source, indexStart + pixel)
            rejection = cellRejection(span.index, pixel, index, covered)
            pixel++
        }
        return rejection?.let(::rejected) ?: accepted(Unit)
    }

    private fun paddingRejection(
        layer: Int,
        start: Int,
        count: Int,
    ): ProjectFormatRejection? {
        val tail = count % Byte.SIZE_BITS
        val last = source.byteAt(start + ProjectFormatV3Layout.coverageByteCount(count) - 1).toInt() and U8_MASK
        return if (tail != 0 &&
            last and (U8_MASK shl tail) != 0
        ) {
            ProjectFormatRejection.InvalidCoverage(layer)
        } else {
            null
        }
    }

    private fun cellRejection(
        layer: Int,
        pixel: Int,
        index: Int,
        covered: Boolean,
    ): ProjectFormatRejection? =
        when {
            !covered && index != 0 -> {
                ProjectFormatRejection.InvalidCoverage(layer)
            }

            covered && index >= header.paletteEntryCount -> {
                ProjectFormatRejection.PixelIndexOutsidePalette(
                    ProjectFormatV3Layout.position(header.size, pixel),
                    when (val slot = PaletteIndex.create(index)) {
                        is DomainValueResult.Created -> slot.value
                        is DomainValueResult.Rejected -> error("U8 slot rejected")
                    },
                    header.paletteEntryCount,
                )
            }

            else -> {
                null
            }
        }

    private companion object {
        const val U8_MASK: Int = 0xff
    }
}

internal class ProjectFormatV4ValidatedFrame(
    val span: ProjectFormatV4FrameSpan,
    val layers: List<ProjectFormatV4ValidatedLayer>,
)

internal class ProjectFormatV4ValidatedLayer(
    val span: ProjectFormatV4LayerSpan,
    val name: LayerName,
)
