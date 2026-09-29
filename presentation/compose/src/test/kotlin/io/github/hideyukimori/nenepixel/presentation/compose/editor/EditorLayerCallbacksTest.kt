package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceReductionResult
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerName
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.fixture
import io.github.hideyukimori.nenepixel.presentation.compose.requiredValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class EditorLayerCallbacksTest {
    @Test
    fun `adding puts a new layer above the active one and makes it active`() {
        val fixture = fixture()
        val layers = fixture.controller.callbacks.layers

        val added = layers.onAdd()

        val executed = assertInstanceOf(EditorLayerOutcome.Executed::class.java, added)
        assertInstanceOf(CommandResult.Applied::class.java, executed.result)
        assertEquals(listOf(layerId(2), LayerId.first()), layerRowsOf(added.renderState.document).map { it.id })
        assertEquals(layerId(2), added.renderState.activeLayerId)
        assertSame(added.renderState, fixture.controller.renderState)
    }

    @Test
    fun `selecting a layer publishes it as the active layer`() {
        val fixture = fixture()
        val layers = fixture.controller.callbacks.layers
        layers.onAdd()

        val selected = layers.onSelect(LayerId.first())

        val reduced = assertInstanceOf(EditorLayerOutcome.Reduced::class.java, selected)
        assertInstanceOf(WorkspaceReductionResult.Reduced::class.java, reduced.result)
        assertEquals(LayerId.first(), selected.renderState.activeLayerId)
        assertSame(selected.renderState, fixture.controller.renderState)
    }

    @Test
    fun `visibility, rename and move are applied and published`() {
        val fixture = fixture()
        val layers = fixture.controller.callbacks.layers
        layers.onAdd()
        val name = LayerName.create("Ink").requiredValue()

        layers.onSetVisibility(LayerId.first(), LayerVisibility.Hidden)
        layers.onRename(LayerId.first(), name)
        val moved = layers.onMove(LayerId.first(), 1)

        val executed = assertInstanceOf(EditorLayerOutcome.Executed::class.java, moved)
        assertInstanceOf(CommandResult.Applied::class.java, executed.result)
        assertEquals(
            listOf(
                LayerRowModel(LayerId.first(), name, LayerVisibility.Hidden),
                LayerRowModel(layerId(2), LayerName.empty, LayerVisibility.Visible),
            ),
            layerRowsOf(fixture.controller.renderState.document),
        )
    }

    @Test
    fun `deleting the last layer returns the refusal instead of dropping it`() {
        val fixture = fixture()
        val layers = fixture.controller.callbacks.layers

        val refused = layers.onDelete(LayerId.first())

        val executed = assertInstanceOf(EditorLayerOutcome.Executed::class.java, refused)
        assertInstanceOf(CommandResult.Rejected::class.java, executed.result)
        assertSame(fixture.initialDocument, refused.renderState.document)
        // An equal render state leaves the StateFlow's instance in place, so a refusal compares by value.
        assertEquals(refused.renderState, fixture.controller.renderState)
    }

    private fun layerId(value: Int): LayerId = LayerId.create(value).requiredValue()
}
