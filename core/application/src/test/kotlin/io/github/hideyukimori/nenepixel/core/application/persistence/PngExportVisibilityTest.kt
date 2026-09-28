package io.github.hideyukimori.nenepixel.core.application.persistence

import io.github.hideyukimori.nenepixel.core.application.document.command.AddLayerCommand
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.command.SetLayerVisibilityCommand
import io.github.hideyukimori.nenepixel.core.application.document.transition.LayerStructureTestValues.layerId
import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.application.editor.PngExportStart
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** ADR 0030: a document with no visible layer is refused before the adapter chooses a destination. */
internal class PngExportVisibilityTest {
    @Test
    fun `export with every layer hidden returns NoVisibleLayer without touching the port or persistence state`() =
        runBlocking {
            val fixture = initializedFixture()
            add(fixture.runtime, above = 1)
            hide(fixture.runtime, 1)
            hide(fixture.runtime, 2)
            val runtime = fixture.runtime.state
            val operation = fixture.workflow.operation.value
            val autosave = fixture.workflow.autosave.value

            assertEquals(PersistenceRequestResult.NoVisibleLayer, fixture.workflow.exportPng())

            assertTrue(fixture.exporter.documents.isEmpty())
            assertEquals(runtime, fixture.runtime.state)
            assertEquals(operation, fixture.workflow.operation.value)
            assertEquals(autosave, fixture.workflow.autosave.value)
            assertEquals(PngExportStart.NoVisibleLayer, fixture.runtime.pngExportOperations.begin())
            assertEquals(operation, fixture.workflow.operation.value)
        }

    @Test
    fun `export with one visible layer still writes through the port`() =
        runBlocking {
            val fixture = initializedFixture()
            add(fixture.runtime, above = 1)
            hide(fixture.runtime, 1)
            val document = fixture.runtime.state.documentState

            assertCompleted(PersistenceLastOutcome.PngExported, fixture.workflow.exportPng())

            assertEquals(listOf(document), fixture.exporter.documents)
        }

    private fun add(
        runtime: EditorRuntime,
        above: Int,
    ) {
        assertInstanceOf(
            CommandResult.Applied::class.java,
            runtime.execute(AddLayerCommand.create(runtime.captureSource(), layerId(above))),
        )
    }

    private fun hide(
        runtime: EditorRuntime,
        id: Int,
    ) {
        assertInstanceOf(
            CommandResult.Applied::class.java,
            runtime.execute(
                SetLayerVisibilityCommand.create(runtime.captureSource(), layerId(id), LayerVisibility.Hidden),
            ),
        )
    }
}
