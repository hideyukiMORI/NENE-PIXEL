package io.github.hideyukimori.nenepixel

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceState
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.drawing.DrawingTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

internal class EditorRuntimeLifecycleTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun configurationRecreationRetainsTheOnlyDocumentAndWorkspaceOwners() {
        createDocument(width = "5", height = "3")
        composeRule.onNodeWithContentDescription("Eraser tool").performClick()
        composeRule.waitForIdle()
        lateinit var runtime: EditorRuntime
        lateinit var document: DocumentState
        lateinit var workspace: WorkspaceState
        lateinit var controller: io.github.hideyukimori.nenepixel.presentation.compose.editor.EditorController
        composeRule.activityRule.scenario.onActivity { activity ->
            val model = activity.editorModel()
            runtime = model.runtime
            controller = model.controller
            document = model.runtime.state.documentState
            workspace = model.runtime.state.workspaceState
        }

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        composeRule.activityRule.scenario.onActivity { activity ->
            val retained = activity.editorModel()
            assertSame(runtime, retained.runtime)
            assertSame(controller, retained.controller)
            assertSame(document, retained.runtime.state.documentState)
            assertSame(workspace, retained.runtime.state.workspaceState)
            assertEquals(DrawingTool.Eraser, retained.runtime.state.workspaceState.activeTool)
        }
        composeRule.onNodeWithContentDescription("5 by 3 pixel canvas").assertExists()
    }

    @Test
    fun emulatorNewDocumentCreationSmokeUsesValidatedMaximumBoundary() {
        createDocument(width = "256", height = "1")

        composeRule.onNodeWithContentDescription("256 by 1 pixel canvas").assertExists()
        composeRule.activityRule.scenario.onActivity { activity ->
            val state = activity.editorModel().runtime.state
            assertEquals(256, state.documentState.size.width.value)
            assertEquals(1, state.documentState.size.height.value)
            assertEquals(0L, state.documentState.revision.value)
            assertEquals(
                256,
                state.documentState.snapshot
                    .copyPackedRgba8888()
                    .size,
            )
        }
    }

    private fun createDocument(
        width: String,
        height: String,
    ) {
        composeRule.onNodeWithText("New document").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Create new document").assertExists()
        composeRule.onNodeWithContentDescription("Document width").performTextReplacement(width)
        composeRule.onNodeWithContentDescription("Document height").performTextReplacement(height)
        composeRule.onNodeWithText("Create").performClick()
        composeRule.waitForIdle()
    }

    private fun MainActivity.editorModel(): EditorRuntimeViewModel =
        ViewModelProvider(this, EditorRuntimeViewModel.factory)[EditorRuntimeViewModel::class.java]
}
