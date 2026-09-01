package io.github.hideyukimori.nenepixel.core.domain.document

import io.github.hideyukimori.nenepixel.core.domain.DomainValueAssertions.created
import io.github.hideyukimori.nenepixel.core.domain.DomainValueTestValues.canvasSize
import io.github.hideyukimori.nenepixel.core.domain.DomainValueTestValues.color
import io.github.hideyukimori.nenepixel.core.domain.DomainValueTestValues.pixelPosition
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class DocumentStateTest {
    @Test
    fun `size and revision are always the snapshot values`() {
        val snapshot =
            created(
                PixelSnapshot.create(
                    size = canvasSize(2, 1),
                    revision = created(Revision.create(3L)),
                    pixels = listOf(BLACK, RED),
                ),
            )

        val state = DocumentState.create(DOCUMENT_ID, snapshot)

        assertEquals(DOCUMENT_ID, state.id)
        assertEquals(snapshot, state.snapshot)
        assertEquals(snapshot.size, state.size)
        assertEquals(snapshot.revision, state.revision)
    }

    @Test
    fun `construction is deterministic and retains snapshot value equality`() {
        val firstInput = mutableListOf(BLACK)
        val firstSnapshot =
            created(PixelSnapshot.create(canvasSize(1, 1), Revision.initial(), firstInput))
        val equalSnapshot =
            created(PixelSnapshot.create(canvasSize(1, 1), Revision.initial(), listOf(BLACK)))
        val laterSnapshot =
            created(PixelSnapshot.create(canvasSize(1, 1), created(Revision.create(1L)), listOf(BLACK)))
        val redSnapshot =
            created(PixelSnapshot.create(canvasSize(1, 1), Revision.initial(), listOf(RED)))
        val first = DocumentState.create(DOCUMENT_ID, firstSnapshot)
        val equal = DocumentState.create(DOCUMENT_ID, equalSnapshot)

        firstInput[0] = RED

        assertEquals(first, equal)
        assertEquals(first.hashCode(), equal.hashCode())
        assertEquals(BLACK, created(first.snapshot.colorAt(pixelPosition(0, 0))))
        assertNotEquals(first, DocumentState.create(OTHER_DOCUMENT_ID, equalSnapshot))
        assertNotEquals(first, DocumentState.create(DOCUMENT_ID, laterSnapshot))
        assertNotEquals(first, DocumentState.create(DOCUMENT_ID, redSnapshot))
    }

    private companion object {
        val DOCUMENT_ID = created(DocumentId.create("0".repeat(32)))
        val OTHER_DOCUMENT_ID = created(DocumentId.create("1".repeat(32)))
        val BLACK = color(0, 0, 0, 255)
        val RED = color(255, 0, 0, 255)
    }
}
