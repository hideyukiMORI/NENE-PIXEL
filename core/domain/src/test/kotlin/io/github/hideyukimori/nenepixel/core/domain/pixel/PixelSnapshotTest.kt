package io.github.hideyukimori.nenepixel.core.domain.pixel

import io.github.hideyukimori.nenepixel.core.domain.DomainValueAssertions.created
import io.github.hideyukimori.nenepixel.core.domain.DomainValueAssertions.rejected
import io.github.hideyukimori.nenepixel.core.domain.DomainValueTestValues.canvasSize
import io.github.hideyukimori.nenepixel.core.domain.DomainValueTestValues.color
import io.github.hideyukimori.nenepixel.core.domain.DomainValueTestValues.pixelPosition
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.Revision
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class PixelSnapshotTest {
    @Test
    fun `snapshot uses row major typed queries and value equality`() {
        val pixels = listOf(BLACK, RED, GREEN, BLUE)
        val first = created(PixelSnapshot.create(canvasSize(2, 2), Revision.initial(), pixels))
        val same = created(PixelSnapshot.create(canvasSize(2, 2), Revision.initial(), pixels))
        val later = created(PixelSnapshot.create(canvasSize(2, 2), created(Revision.create(1L)), pixels))

        assertEquals(BLACK, created(first.colorAt(pixelPosition(0, 0))))
        assertEquals(RED, created(first.colorAt(pixelPosition(1, 0))))
        assertEquals(GREEN, created(first.colorAt(pixelPosition(0, 1))))
        assertEquals(BLUE, created(first.colorAt(pixelPosition(1, 1))))
        assertEquals(first, same)
        assertEquals(first.hashCode(), same.hashCode())
        assertNotEquals(first, later)
    }

    @Test
    fun `snapshot rejects an inexact pixel count`() {
        val rejection = rejected(PixelSnapshot.create(canvasSize(2, 2), Revision.initial(), listOf(BLACK)))

        val mismatch = assertInstanceOf(DomainValueRejection.PixelSnapshotSizeMismatch::class.java, rejection)
        assertEquals(4L, mismatch.expectedPixelCount)
        assertEquals(1, mismatch.actualPixelCount)
    }

    @Test
    fun `maximum canvas mismatch is rejected before reading or copying an element`() {
        val canvas = canvasSize(PixelLimits.MAX_CANVAS_AXIS, PixelLimits.MAX_CANVAS_AXIS)
        val sentinel =
            object : AbstractList<PixelColor>() {
                override val size: Int = 1

                override fun get(index: Int): PixelColor = error("Snapshot mismatch read element $index.")
            }

        val rejection = rejected(PixelSnapshot.create(canvas, Revision.initial(), sentinel))
        val mismatch = assertInstanceOf(DomainValueRejection.PixelSnapshotSizeMismatch::class.java, rejection)

        assertEquals(PixelLimits.MAX_CANVAS_PIXELS.toLong(), canvas.pixelCount)
        assertEquals(PixelLimits.MAX_CANVAS_PIXELS.toLong(), mismatch.expectedPixelCount)
        assertEquals(1, mismatch.actualPixelCount)
    }

    @Test
    fun `snapshot rejects an outside typed position`() {
        val snapshot = created(PixelSnapshot.create(canvasSize(1, 1), Revision.initial(), listOf(BLACK)))

        assertInstanceOf(
            DomainValueRejection.PixelPositionOutsideCanvas::class.java,
            rejected(snapshot.colorAt(pixelPosition(1, 0))),
        )
    }

    @Test
    fun `snapshot defensively owns mutable factory input`() {
        val input = mutableListOf(BLACK)
        val snapshot = created(PixelSnapshot.create(canvasSize(1, 1), Revision.initial(), input))

        input[0] = RED

        assertEquals(BLACK, created(snapshot.colorAt(pixelPosition(0, 0))))
    }

    @Test
    fun `packed snapshot inputs and bulk reads are defensive`() {
        val packed = intArrayOf(BLACK.toPackedRgba8888(), TRANSPARENT_RED.toPackedRgba8888())
        val snapshot =
            created(
                PixelSnapshot.createPackedRgba8888(
                    canvasSize(2, 1),
                    Revision.initial(),
                    packed,
                ),
            )

        packed[0] = RED.toPackedRgba8888()
        val copy = snapshot.copyPackedRgba8888()
        copy[1] = BLACK.toPackedRgba8888()

        assertEquals(BLACK, created(snapshot.colorAt(pixelPosition(0, 0))))
        assertEquals(TRANSPARENT_RED, created(snapshot.colorAt(pixelPosition(1, 0))))
        assertEquals(TRANSPARENT_RED.toPackedRgba8888(), created(snapshot.packedRgba8888At(pixelPosition(1, 0))))
    }

    @Test
    fun `packed mapping builds one row major successor without changing its source`() {
        val original =
            created(
                PixelSnapshot.createPackedRgba8888(
                    canvasSize(2, 1),
                    Revision.initial(),
                    intArrayOf(BLACK.toPackedRgba8888(), GREEN.toPackedRgba8888()),
                ),
            )
        val visited = mutableListOf<Int>()

        val mapped =
            original.mapPackedRgba8888(created(Revision.create(1L))) { index, packed ->
                visited += index
                if (index == 1) RED.toPackedRgba8888() else packed
            }

        assertEquals(listOf(0, 1), visited)
        assertEquals(GREEN, created(original.colorAt(pixelPosition(1, 0))))
        assertEquals(RED, created(mapped.colorAt(pixelPosition(1, 0))))
        assertEquals(1L, mapped.revision.value)
    }

    private companion object {
        val BLACK = color(0, 0, 0, 255)
        val RED = color(255, 0, 0, 255)
        val GREEN = color(0, 255, 0, 255)
        val BLUE = color(0, 0, 255, 255)
        val TRANSPARENT_RED = color(255, 0, 0, 0)
    }
}
