package io.github.hideyukimori.nenepixel.core.domain.document

import io.github.hideyukimori.nenepixel.core.domain.DomainValueAssertions.created
import io.github.hideyukimori.nenepixel.core.domain.DomainValueAssertions.rejected
import io.github.hideyukimori.nenepixel.core.domain.DomainValueTestValues.canvasSize
import io.github.hideyukimori.nenepixel.core.domain.DomainValueTestValues.color
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.pixel.PixelSnapshot
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueRejection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class DocumentStateTest {
    @Test
    fun `document owns definition and derives size and revision from snapshot`() {
        val definition = definition(3)
        val snapshot =
            created(
                PixelSnapshot.create(canvasSize(2, 1), created(Revision.create(3)), listOf(index(0), index(2))),
            )
        val state = created(DocumentState.create(DOCUMENT_ID, definition, snapshot))

        assertEquals(DOCUMENT_ID, state.id)
        assertEquals(definition, state.definition)
        assertEquals(snapshot, state.snapshot)
        assertEquals(snapshot.size, state.size)
        assertEquals(snapshot.revision, state.revision)
        assertEquals(state, created(DocumentState.create(DOCUMENT_ID, definition, snapshot)))
        assertNotEquals(state, created(DocumentState.create(OTHER_DOCUMENT_ID, definition, snapshot)))
    }

    @Test
    fun `document factory rejects maximum used slot outside actual definition`() {
        val snapshot = created(PixelSnapshot.create(canvasSize(2, 1), Revision.initial(), listOf(index(0), index(2))))
        val rejection = rejected(DocumentState.create(DOCUMENT_ID, definition(2), snapshot))

        val outside = assertInstanceOf(DomainValueRejection.PaletteIndexOutsidePalette::class.java, rejection)
        assertEquals(index(2), outside.attemptedIndex)
        assertEquals(2, outside.entryCount)
    }

    private fun definition(count: Int): PaletteDefinition =
        created(
            PaletteDefinition.create(
                created(Palette.create(List(count) { color(it, it, it, 255) })),
                PaletteIndex.first,
            ),
        )

    private fun index(value: Int): PaletteIndex = created(PaletteIndex.create(value))

    private companion object {
        val DOCUMENT_ID = created(DocumentId.create("0".repeat(32)))
        val OTHER_DOCUMENT_ID = created(DocumentId.create("1".repeat(32)))
    }
}
