package io.github.hideyukimori.nenepixel.core.domain.frame

import io.github.hideyukimori.nenepixel.core.domain.color.ColorChannel
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
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
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class FrameHierarchyTest {
    @Test fun `duration and identity bounds are typed and default is100 milliseconds`() {
        assertEquals(100, FrameDuration.default.value)
        assertEquals(10, value(FrameDuration.create(10)).value)
        assertEquals(60_000, value(FrameDuration.create(60_000)).value)
        assertEquals(DomainValueRejection.InvalidFrameDuration(9), rejection(FrameDuration.create(9)))
        assertEquals(DomainValueRejection.InvalidFrameDuration(60_001), rejection(FrameDuration.create(60_001)))
        assertEquals(DomainValueRejection.InvalidFrameId(0), rejection(FrameId.create(0)))
        assertEquals(DomainValueRejection.FrameIdOverflow, rejection(value(FrameId.create(Int.MAX_VALUE)).next()))
        assertEquals(2, value(FrameId.first().next()).value)
    }

    @Test fun `frame count rejects before traversing unbounded input and global records cannot exceed16`() {
        val unbounded =
            object : AbstractList<Frame>() {
                override val size: Int = 17

                override fun get(index: Int): Frame = error("Out-of-budget input must not be traversed")
            }
        assertEquals(DomainValueRejection.DocumentFrameCountOutOfRange(17), rejection(document(unbounded)))
        assertEquals(DomainValueRejection.DocumentFrameCountOutOfRange(0), rejection(document(emptyList())))
        val first = frame(1, (1..8).map { layer(it) })
        val second = frame(2, (9..17).map { layer(it) })
        assertEquals(
            DomainValueRejection.DocumentLayerRecordCountOutOfRange(17),
            rejection(document(listOf(first, second))),
        )
        val valid = value(document((1..16).map { frame(it, listOf(layer(it))) }))
        assertEquals(16, valid.totalLayerCount)
    }

    @Test fun `hierarchy rejects duplicate IDs and cross-frame canvas or palette mismatch`() {
        val first = frame(1, listOf(layer(1)))
        assertEquals(DomainValueRejection.DuplicateFrameId(1), rejection(document(listOf(first, first))))
        assertEquals(
            DomainValueRejection.DuplicateLayerId(1),
            rejection(document(listOf(first, frame(2, listOf(layer(1)))))),
        )
        val otherSize = size(2)
        val mismatch = frame(2, listOf(layer(2, PixelSnapshot.createEmpty(otherSize))))
        assertInstanceOf(
            DomainValueRejection.LayerSizeMismatch::class.java,
            rejection(document(listOf(first, mismatch))),
        )
        val badIndex = value(PaletteIndex.create(2))
        val invalid = frame(2, listOf(layer(2, value(PixelSnapshot.create(size(), listOf(badIndex))))))
        assertInstanceOf(
            DomainValueRejection.PaletteIndexOutsidePalette::class.java,
            rejection(document(listOf(first, invalid))),
        )
    }

    @Test fun `frames own list copies retain shared snapshots and global lookup preserves identity`() {
        val layers = mutableListOf(layer(4))
        val first = frame(3, layers)
        layers.clear()
        val frames = mutableListOf(first, frame(5, listOf(layer(8, first.layers.single().snapshot))))
        val document = value(document(frames))
        frames.clear()
        assertEquals(listOf(3, 5), document.frames.map { it.id.value })
        assertSame(first, document.frameById(value(FrameId.create(3))))
        assertSame(first, document.frameContainingLayer(value(LayerId.create(4))))
        assertSame(
            first.layers.single().snapshot,
            document.frames[1]
                .layers
                .single()
                .snapshot,
        )
        assertNull(document.frameById(value(FrameId.create(99))))
    }

    private fun size(width: Int = 1): CanvasSize =
        CanvasSize.create(value(CanvasWidth.create(width)), value(CanvasHeight.create(1)))

    private fun layer(
        id: Int,
        pixels: PixelSnapshot = PixelSnapshot.createEmpty(size()),
    ): Layer = Layer.create(value(LayerId.create(id)), LayerName.empty, LayerVisibility.Visible, pixels)

    private fun frame(
        id: Int,
        layers: List<Layer>,
    ): Frame = value(Frame.create(value(FrameId.create(id)), FrameDuration.default, layers))

    private fun document(frames: List<Frame>): DomainValueResult<DocumentState> {
        val channel = value(ColorChannel.create(0))
        val color = PixelColor.create(channel, channel, channel, channel)
        val palette = value(Palette.create(listOf(color, color)))
        return DocumentState.create(
            value(DocumentId.create("0".repeat(32))),
            Revision.initial(),
            value(PaletteDefinition.create(palette, PaletteIndex.first)),
            frames,
        )
    }

    private fun rejection(result: DomainValueResult<*>): DomainValueRejection =
        when (result) {
            is DomainValueResult.Rejected -> result.rejection
            is DomainValueResult.Created -> error("Expected rejection")
        }

    private fun <T> value(result: DomainValueResult<T>): T =
        when (result) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Invalid fixture: ${result.rejection}")
        }
}
