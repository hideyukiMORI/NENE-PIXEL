package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImage
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImageResult
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayOpacity
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.fixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class UnderlayControllerTest {
    @Test
    fun `setting an underlay publishes it without a document or history change`() {
        val fixture = fixture()
        val callbacks = fixture.controller.callbacks.underlay
        val before = fixture.controller.renderState
        assertNull(before.underlay)
        val underlay = underlayFor(fixture.controller)
        val result = callbacks.onSet(underlay)
        assertEquals(underlay, result.underlay)
        assertEquals(underlay, fixture.runtime.state.workspaceState.underlay)
        assertSame(result, fixture.controller.renderStates.value)
        assertUntouched(before, result)
        assertSame(fixture.initialDocument, fixture.runtime.state.documentState)
    }

    @Test
    fun `a derived underlay replaces the shown one`() {
        val fixture = fixture()
        val callbacks = fixture.controller.callbacks.underlay
        val placed = underlayFor(fixture.controller)
        callbacks.onSet(placed)
        val faded = placed.withOpacity(UnderlayOpacity.MIN)
        val result = callbacks.onSet(faded)
        assertEquals(faded, result.underlay)
        assertEquals(UnderlayOpacity.MIN, result.underlay?.opacity)
    }

    @Test
    fun `clearing the underlay removes it without a document or history change`() {
        val fixture = fixture()
        val callbacks = fixture.controller.callbacks.underlay
        val before = fixture.controller.renderState
        callbacks.onSet(underlayFor(fixture.controller))
        val result = callbacks.onClear()
        assertNull(result.underlay)
        assertNull(fixture.runtime.state.workspaceState.underlay)
        assertSame(result, fixture.controller.renderStates.value)
        assertEquals(before, result)
        assertUntouched(before, result)
        assertSame(fixture.initialDocument, fixture.runtime.state.documentState)
    }

    @Test
    fun `setting the same underlay again keeps the render state`() {
        val fixture = fixture()
        val callbacks = fixture.controller.callbacks.underlay
        val underlay = underlayFor(fixture.controller)
        val first = callbacks.onSet(underlay)
        val second = callbacks.onSet(underlay)
        assertEquals(first, second)
        assertEquals(underlay, second.underlay)
        assertEquals(second, fixture.controller.renderStates.value)
    }

    @Test
    fun `clearing without an underlay keeps the render state`() {
        val fixture = fixture()
        val callbacks = fixture.controller.callbacks.underlay
        val before = fixture.controller.renderState
        val result = callbacks.onClear()
        assertEquals(before, result)
        assertNull(result.underlay)
    }

    private fun underlayFor(controller: EditorController): ReferenceUnderlay {
        val created = ReferenceImage.create(2, 2, IntArray(4) { OPAQUE_BLACK })
        val image = (created as ReferenceImageResult.Created).image
        return ReferenceUnderlay.placed(image, controller.renderState.document.size)
    }

    private fun assertUntouched(
        before: EditorRenderState,
        after: EditorRenderState,
    ) {
        assertSame(before.document, after.document)
        assertEquals(before.canUndo, after.canUndo)
        assertEquals(before.canRedo, after.canRedo)
        assertEquals(before.dirtyState, after.dirtyState)
        assertSame(before.viewport, after.viewport)
    }

    private companion object {
        const val OPAQUE_BLACK: Int = 0x000000FF
    }
}
