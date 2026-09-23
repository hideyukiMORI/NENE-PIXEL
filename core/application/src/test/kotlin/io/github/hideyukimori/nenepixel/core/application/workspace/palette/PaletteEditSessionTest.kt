package io.github.hideyukimori.nenepixel.core.application.workspace.palette

import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryPosition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.defaultDocumentId
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.definition
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.green
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.otherDocumentId
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.paletteIndex
import io.github.hideyukimori.nenepixel.core.application.document.transition.ApplicationTestValues.red
import io.github.hideyukimori.nenepixel.core.application.editor.RuntimeSourceToken
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class PaletteEditSessionTest {
    private val definition = definition(paletteIndex(0), red, green)
    private val base = RuntimeSourceToken(1L, defaultDocumentId, HistoryPosition.initial)

    @Test
    fun `begin holds the document definition by reference with an empty draft history`() {
        val session = PaletteEditSession.begin(base, definition)

        assertSame(base, session.base)
        assertSame(definition, session.draft)
        assertTrue(session.timeline.isEmpty())
        assertEquals(0, session.cursor)
    }

    @Test
    fun `base and session equality use every base element`() {
        val same = RuntimeSourceToken(1L, defaultDocumentId, HistoryPosition.initial)
        val otherDocument = RuntimeSourceToken(1L, otherDocumentId, HistoryPosition.initial)
        val otherGeneration = RuntimeSourceToken(2L, defaultDocumentId, HistoryPosition.initial)
        val otherPosition = RuntimeSourceToken(1L, defaultDocumentId, HistoryPosition.create(1L))

        assertEquals(base, same)
        assertEquals(base.hashCode(), same.hashCode())
        assertNotEquals(base, otherDocument)
        assertNotEquals(base, otherGeneration)
        assertNotEquals(base, otherPosition)
        assertEquals(PaletteEditSession.begin(base, definition), PaletteEditSession.begin(same, definition))
        assertEquals(
            PaletteEditSession.begin(base, definition).hashCode(),
            PaletteEditSession.begin(same, definition).hashCode(),
        )
        assertNotEquals(PaletteEditSession.begin(base, definition), PaletteEditSession.begin(otherPosition, definition))
    }
}
