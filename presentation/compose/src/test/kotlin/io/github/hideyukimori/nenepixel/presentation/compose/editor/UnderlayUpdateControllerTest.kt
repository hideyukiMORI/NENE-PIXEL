package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImage
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceImageResult
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayInteraction
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayOpacity
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.fixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/** Issue #171 C3d: `onUpdate` derives the next underlay from the runtime's current one when it is called. */
internal class UnderlayUpdateControllerTest {
    @Test
    fun `updating an underlay publishes the derived value`() {
        val fixture = fixture()
        val callbacks = fixture.controller.callbacks.underlay
        val placed = underlayFor(fixture.controller)
        callbacks.onSet(placed)

        val result = callbacks.onUpdate { underlay -> underlay.withOpacity(UnderlayOpacity.MIN) }

        assertEquals(placed.withOpacity(UnderlayOpacity.MIN), result.underlay)
        assertEquals(result.underlay, fixture.runtime.state.workspaceState.underlay)
        assertSame(result, fixture.controller.renderStates.value)
        assertSame(fixture.initialDocument, fixture.runtime.state.documentState)
    }

    @Test
    fun `updating without an underlay changes nothing`() {
        val fixture = fixture()
        val callbacks = fixture.controller.callbacks.underlay
        val before = fixture.controller.renderState
        var derived = false

        val result =
            callbacks.onUpdate { underlay ->
                derived = true
                underlay.adjusting()
            }

        assertEquals(false, derived)
        assertEquals(before, result)
        assertNull(result.underlay)
        assertNull(fixture.runtime.state.workspaceState.underlay)
        assertSame(fixture.initialDocument, fixture.runtime.state.documentState)
    }

    @Test
    fun `leaving the mode keeps a placement moved through another route`() {
        val fixture = fixture()
        val callbacks = fixture.controller.callbacks.underlay
        val adjusting = underlayFor(fixture.controller).adjusting()
        callbacks.onSet(adjusting)
        val moved = adjusting.withPlacement(-1.0, 0.5, 1.0)
        assertNotEquals(adjusting.placement, moved.placement)
        fixture.runtime.reduce(WorkspaceAction.SetReferenceUnderlay(moved))

        val result = callbacks.onUpdate { underlay -> underlay.rested() }

        assertEquals(moved.placement, result.underlay?.placement)
        assertEquals(UnderlayInteraction.Resting, result.underlay?.interaction)
        assertEquals(moved.rested(), fixture.runtime.state.workspaceState.underlay)
    }

    private fun underlayFor(controller: EditorController): ReferenceUnderlay {
        val created = ReferenceImage.create(2, 2, IntArray(4) { OPAQUE_BLACK })
        val image = (created as ReferenceImageResult.Created).image
        return ReferenceUnderlay.placed(image, controller.renderState.document.size)
    }

    private companion object {
        const val OPAQUE_BLACK: Int = 0x000000FF
    }
}
