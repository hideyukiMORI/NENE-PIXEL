package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.editor.NewDocumentRequest
import io.github.hideyukimori.nenepixel.core.application.editor.NewDocumentResult
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportState
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportValueResult
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportZoom
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.fixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

internal class NewDocumentEditorControllerTest {
    @Test
    fun `presentation and direct test adapters produce the same typed creation result`() {
        val direct = fixture()
        val presented = fixture()

        val directResult =
            assertInstanceOf(
                NewDocumentResult.Created::class.java,
                direct.runtime.createNewDocument(NewDocumentRequest.create("3", "2")),
            )
        val presentedResult =
            assertInstanceOf(
                NewDocumentSubmission.Created::class.java,
                presented.controller.callbacks.onCreateNewDocument("3", "2"),
            )

        assertEquals(directResult.state.documentState.snapshot, presentedResult.renderState.snapshot)
        assertEquals(directResult.state.workspaceState.viewport, presentedResult.renderState.viewport)
        assertEquals(directResult.state.historyAvailability, presented.runtime.state.historyAvailability)
        assertEquals(directResult.state.dirtyState, presented.runtime.state.dirtyState)
    }

    @Test
    fun `typed rejection is visible and preserves document workspace and viewport owners`() {
        val fixture = fixture()
        val changedViewport =
            ViewportState.create(
                created(ViewportZoom.create(2.0)),
                fixture.runtime.state.workspaceState.viewport.center,
            )
        fixture.runtime.reduce(WorkspaceAction.SetViewport(changedViewport))
        val before = fixture.runtime.state

        val result = fixture.controller.callbacks.onCreateNewDocument("257", "4")
        val rejected = assertInstanceOf(NewDocumentSubmission.Rejected::class.java, result)
        val after = fixture.runtime.state

        assertEquals("Width must be between 1 and 256.", rejected.userMessage)
        assertSame(before.documentState, after.documentState)
        assertSame(before.workspaceState, after.workspaceState)
        assertEquals(changedViewport, after.workspaceState.viewport)
        assertEquals(before.documentState.snapshot, rejected.renderState.snapshot)
    }

    private fun created(result: ViewportValueResult<ViewportZoom>): ViewportZoom =
        when (result) {
            is ViewportValueResult.Created -> result.value
            is ViewportValueResult.Rejected -> fail("Viewport test value was rejected: ${result.rejection}")
        }
}
