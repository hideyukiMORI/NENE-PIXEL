package io.github.hideyukimori.nenepixel.core.domain.geometry

import io.github.hideyukimori.nenepixel.core.domain.DomainValueTestValues.canvasSize
import io.github.hideyukimori.nenepixel.core.domain.DomainValueTestValues.pixelPosition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class DocumentPointTest {
    @Test
    fun `points keep signed and beyond-canvas values with value equality`() {
        val negative = DocumentPoint.create(-3, -1)
        val beyond = DocumentPoint.create(Int.MAX_VALUE, 300)

        assertEquals(-3, negative.x)
        assertEquals(-1, negative.y)
        assertEquals(Int.MAX_VALUE, beyond.x)
        assertEquals(300, beyond.y)
        assertEquals(DocumentPoint.create(-3, -1), negative)
        assertEquals(DocumentPoint.create(-3, -1).hashCode(), negative.hashCode())
        assertNotEquals(DocumentPoint.create(-1, -3), negative)
    }

    @Test
    fun `canvas contains only the half-open rectangle of signed points`() {
        val size = canvasSize(4, 3)

        assertTrue(size.contains(DocumentPoint.create(0, 0)))
        assertTrue(size.contains(DocumentPoint.create(3, 2)))
        assertFalse(size.contains(DocumentPoint.create(-1, 0)))
        assertFalse(size.contains(DocumentPoint.create(0, -1)))
        assertFalse(size.contains(DocumentPoint.create(4, 0)))
        assertFalse(size.contains(DocumentPoint.create(0, 3)))
        assertFalse(size.contains(DocumentPoint.create(Int.MIN_VALUE, Int.MAX_VALUE)))
    }

    @Test
    fun `in-canvas positions convert to the same document point`() {
        val size = canvasSize(4, 3)
        val position = pixelPosition(3, 2)
        val point = position.toDocumentPoint()

        assertEquals(DocumentPoint.create(3, 2), point)
        assertEquals(size.contains(position), size.contains(point))
        assertEquals(DocumentPoint.create(0, 0), pixelPosition(0, 0).toDocumentPoint())
    }
}
