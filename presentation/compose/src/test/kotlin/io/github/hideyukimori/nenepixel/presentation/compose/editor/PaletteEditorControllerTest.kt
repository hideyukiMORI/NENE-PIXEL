package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceActionRejection
import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteDraftOperation
import io.github.hideyukimori.nenepixel.core.application.workspace.palette.PaletteDraftRejection
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.fixture
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.green
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.red
import io.github.hideyukimori.nenepixel.presentation.compose.requiredValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class PaletteEditorControllerTest {
    @Test
    fun `begin edit and apply replace the document palette and close the session`() {
        val fixture = fixture()
        val palette = fixture.controller.callbacks.palette
        val opened = palette.onBegin()
        assertNotNull(opened.paletteEditSession)
        val edited = palette.onEdit(PaletteDraftOperation.SetSlotColor(slot(0), green))
        assertEquals(
            green,
            edited.paletteEditSession
                ?.draft
                ?.palette
                ?.entryAt(slot(0))
                ?.requiredValue()
                ?.color,
        )
        assertEquals(fixture.initialDocument.definition, edited.definition)
        val applied = palette.onApply()
        assertNull(applied.paletteEditSession)
        assertNull(applied.lastPaletteRejection)
        assertEquals(
            green,
            applied.palette
                .entryAt(slot(0))
                .requiredValue()
                .color,
        )
        assertSame(applied, fixture.controller.renderStates.value)
    }

    @Test
    fun `cancel closes the session and keeps the document palette`() {
        val fixture = fixture()
        val palette = fixture.controller.callbacks.palette
        palette.onBegin()
        palette.onEdit(PaletteDraftOperation.SetSlotColor(slot(0), green))
        val cancelled = palette.onCancel()
        assertNull(cancelled.paletteEditSession)
        assertEquals(fixture.initialDocument.definition, cancelled.definition)
        assertEquals(
            red,
            cancelled.palette
                .entryAt(slot(0))
                .requiredValue()
                .color,
        )
    }

    @Test
    fun `a draft refusal is published and the next success clears it`() {
        val palette = fixture().controller.callbacks.palette
        val opened = palette.onBegin()
        assertFalse(opened.paletteEditSession?.canUndoDraft ?: true)
        val refused = palette.onUndo().lastPaletteRejection
        assertTrue(refused is WorkspaceActionRejection.PaletteDraftRejected)
        assertEquals(
            PaletteDraftRejection.NoUndoAvailable,
            (refused as WorkspaceActionRejection.PaletteDraftRejected).reason,
        )
        val edited = palette.onEdit(PaletteDraftOperation.SetDefault(slot(1)))
        assertNull(edited.lastPaletteRejection)
        assertTrue(edited.paletteEditSession?.canUndoDraft ?: false)
        val undone = palette.onUndo()
        assertTrue(undone.paletteEditSession?.canRedoDraft ?: false)
        assertFalse(undone.paletteEditSession?.canUndoDraft ?: true)
    }

    @Test
    fun `draft actions without a session are refused as no palette session`() {
        val palette = fixture().controller.callbacks.palette
        assertEquals(WorkspaceActionRejection.NoPaletteSession, palette.onUndo().lastPaletteRejection)
        assertEquals(WorkspaceActionRejection.NoPaletteSession, palette.onApply().lastPaletteRejection)
        assertNull(palette.onApply().paletteEditSession)
    }

    @Test
    fun `a second begin is refused and keeps the open session`() {
        val palette = fixture().controller.callbacks.palette
        val session = palette.onBegin().paletteEditSession
        val again = palette.onBegin()
        assertEquals(WorkspaceActionRejection.PaletteSessionAlreadyActive, again.lastPaletteRejection)
        assertEquals(session, again.paletteEditSession)
    }

    @Test
    fun `hex text round trips a palette color`() {
        assertEquals("#FF0000FF", PaletteHexColor.format(red))
        assertEquals(green, PaletteHexColor.parse(PaletteHexColor.format(green)))
        assertEquals(red, PaletteHexColor.parse("ff0000ff"))
    }

    @Test
    fun `malformed hex and channel text is refused`() {
        listOf("", "#FF0000", "#FF0000FF00", "#GG0000FF", "FF 0000FF").forEach { text ->
            assertNull(PaletteHexColor.parse(text), text)
        }
        listOf("", "256", "-1", "1a", "0255").forEach { text ->
            assertNull(PaletteHexColor.parseChannel(text), text)
        }
        assertEquals(255, PaletteHexColor.parseChannel("255")?.value?.toInt())
    }

    private fun slot(value: Int): PaletteIndex = PaletteIndex.create(value).requiredValue()
}
