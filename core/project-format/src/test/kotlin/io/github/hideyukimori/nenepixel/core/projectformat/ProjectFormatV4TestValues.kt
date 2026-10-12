package io.github.hideyukimori.nenepixel.core.projectformat

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.frame.Frame
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameDuration
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

internal object ProjectFormatV4TestValues {
    fun twoFrames(): DocumentState {
        val size = ProjectFormatTestValues.canvas(3, 2)
        val palette =
            value(
                Palette.create(
                    listOf(0x11223300, 0xff0000ff.toInt(), 0xff0000ff.toInt()).map(PixelColor::fromPackedRgba8888),
                ),
            )
        val definition = value(PaletteDefinition.create(palette, value(PaletteIndex.create(2))))
        val bottom =
            ProjectFormatV3TestValues.layer(
                7,
                "A",
                LayerVisibility.Visible,
                size,
                listOf(0, null, 2, 1, null, 0),
            )
        val top = ProjectFormatV3TestValues.layer(9, "", LayerVisibility.Hidden, size, listOf(0, 1, 2, 2, 1, 0))
        val frames =
            listOf(
                value(Frame.create(value(FrameId.create(4)), FrameDuration.default, listOf(bottom))),
                value(Frame.create(value(FrameId.create(2)), value(FrameDuration.create(250)), listOf(top))),
            )
        return value(
            DocumentState.create(ProjectFormatTestValues.v2MinimalDocument.id, Revision.initial(), definition, frames),
        )
    }

    fun maximumFrames(): DocumentState {
        val stack = ProjectFormatV3TestValues.maximumLayeredDocument()
        val frames =
            stack.frames.single().layers.mapIndexed { index, layer ->
                value(
                    Frame.create(value(FrameId.create(index + 1)), value(FrameDuration.create(60_000)), listOf(layer)),
                )
            }
        return value(DocumentState.create(stack.id, stack.revision, stack.definition, frames))
    }

    fun checksum(bytes: ByteArray): ByteArray {
        val offset = bytes.size - Int.SIZE_BYTES
        ProjectFormatBigEndian.writeInt(bytes, offset, Crc32IsoHdlc.checksum(bytes, offset).toInt())
        return bytes
    }

    fun <T> value(result: DomainValueResult<T>): T =
        when (result) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Invalid frame fixture: ${result.rejection}")
        }
}
