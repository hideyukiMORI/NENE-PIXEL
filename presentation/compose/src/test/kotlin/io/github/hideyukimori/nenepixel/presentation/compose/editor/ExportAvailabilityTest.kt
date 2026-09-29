package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.command.SetLayerVisibilityCommand
import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The export button is enabled only while the rendered document has a visible layer (ADR 0030). */
internal class ExportAvailabilityTest {
    @Test
    fun `a visible layer keeps export available`() {
        val fixture = PresentationTestValues.fixture()

        val document = fixture.runtime.state.documentState

        assertTrue(document.hasVisibleLayer())
    }

    @Test
    fun `hiding every layer makes export unavailable`() {
        val fixture = PresentationTestValues.fixture()
        setVisibility(fixture.runtime, LayerVisibility.Hidden)
        fixture.controller.synchronizeWithRuntime()

        val runtimeDocument = fixture.runtime.state.documentState
        val renderedDocument = fixture.controller.renderState.document

        assertFalse(runtimeDocument.hasVisibleLayer())
        assertFalse(renderedDocument.hasVisibleLayer())
    }

    private fun setVisibility(
        runtime: EditorRuntime,
        visibility: LayerVisibility,
    ) {
        val command = SetLayerVisibilityCommand.create(runtime.captureSource(), LayerId.first(), visibility)
        assertInstanceOf(CommandResult.Applied::class.java, runtime.execute(command))
    }
}
