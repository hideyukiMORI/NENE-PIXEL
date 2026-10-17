package io.github.hideyukimori.nenepixel

import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.frame.Frame
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameDuration
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameId
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

/** Fixed synthetic identity, two distinct frames and a hidden layer; no ordinary artwork (#247). */
internal object RecoveryFaultDocument {
    fun create(): DocumentState {
        val size = CanvasSize.create(required(CanvasWidth.create(2)), required(CanvasHeight.create(1)))
        val first = required(PixelSnapshot.createPackedIndices(size, byteArrayOf(1, 0)))
        val second = required(PixelSnapshot.createPackedIndices(size, byteArrayOf(0, 1)))
        val frames =
            listOf(
                required(Frame.create(FrameId.first(), FrameDuration.default, listOf(layer(1, first)))),
                required(
                    Frame.create(
                        required(FrameId.create(2)),
                        required(FrameDuration.create(250)),
                        listOf(layer(2, second), layer(3, first, LayerVisibility.Hidden)),
                    ),
                ),
            )
        return required(
            DocumentState.create(
                required(DocumentId.create("000102030405060708090a0b0c0d0e0f")),
                Revision.initial(),
                definition(),
                frames,
            ),
        )
    }

    private fun definition(): PaletteDefinition =
        required(
            PaletteDefinition.create(
                required(Palette.create(listOf(PixelColor.blank, PixelColor.fromPackedRgba8888(0x112233ff)))),
                PaletteIndex.first,
            ),
        )

    private fun layer(
        id: Int,
        snapshot: PixelSnapshot,
        visibility: LayerVisibility = LayerVisibility.Visible,
    ): Layer = Layer.create(required(LayerId.create(id)), LayerName.empty, visibility, snapshot)

    private fun <T> required(value: DomainValueResult<T>): T =
        when (value) {
            is DomainValueResult.Created -> value.value
            is DomainValueResult.Rejected -> error("Synthetic recovery document rejected: ${value.rejection}")
        }
}
