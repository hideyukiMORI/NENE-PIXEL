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
    fun `extreme rectangular mismatch is rejected before reading or copying an element`() {
        val canvas = canvasSize(Int.MAX_VALUE, 2)
        val sentinel =
            object : AbstractList<PixelColor>() {
                override val size: Int = 1

                override fun get(index: Int): PixelColor = error("Snapshot mismatch read element $index.")
            }

        val rejection = rejected(PixelSnapshot.create(canvas, Revision.initial(), sentinel))
        val mismatch = assertInstanceOf(DomainValueRejection.PixelSnapshotSizeMismatch::class.java, rejection)

        assertEquals(EXTREME_RECTANGULAR_PIXEL_COUNT, canvas.pixelCount)
        assertEquals(EXTREME_RECTANGULAR_PIXEL_COUNT, mismatch.expectedPixelCount)
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

    private companion object {
        const val EXTREME_RECTANGULAR_PIXEL_COUNT: Long = 4_294_967_294L
        val BLACK = color(0, 0, 0, 255)
        val RED = color(255, 0, 0, 255)
        val GREEN = color(0, 255, 0, 255)
        val BLUE = color(0, 0, 255, 255)
    }
}
