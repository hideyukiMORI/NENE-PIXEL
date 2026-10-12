package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerName
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.fixture
import io.github.hideyukimori.nenepixel.presentation.compose.editor.SingleFrameTestValues.layersOf
import io.github.hideyukimori.nenepixel.presentation.compose.requiredValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class EditorLayerCallbacksTest {
    @Test
    fun `adding puts a new layer above the active one and makes it active`() {
        val fixture = fixture()
        val layers = fixture.controller.callbacks.layers

        val added = layers.onAdd()

        assertNull(added.layerNotice)
        assertEquals(
            listOf(layerId(2), LayerId.first()),
            layerRowsOf(
                added.document,
                added.document.frames
                    .single()
                    .id,
            ).map { it.id },
        )
        assertEquals(layerId(2), added.activeLayerId)
        assertSame(added, fixture.controller.renderState)
    }

    @Test
    fun `selecting a layer publishes it as the active layer`() {
        val fixture = fixture()
        val layers = fixture.controller.callbacks.layers
        layers.onAdd()

        val selected = layers.onSelect(LayerId.first())

        assertNull(selected.layerNotice)
        assertEquals(LayerId.first(), selected.activeLayerId)
        assertSame(selected, fixture.controller.renderState)
    }

    @Test
    fun `duplicate targets its row instead of the active layer and publishes the newly selected exact copy`() {
        val fixture = fixture()
        val layers = fixture.controller.callbacks.layers
        layers.onAdd()
        val name = LayerName.create("原画").requiredValue()
        layers.onRename(LayerId.first(), name)
        layers.onSetVisibility(LayerId.first(), LayerVisibility.Hidden)
        val source =
            layersOf(fixture.controller.renderState.document)
                .first()
        assertEquals(layerId(2), fixture.controller.renderState.activeLayerId)
        val duplicated = layers.onDuplicate(source.id)

        assertNull(duplicated.layerNotice)
        assertEquals(
            listOf(LayerId.first(), layerId(3), layerId(2)),
            layersOf(duplicated.document)
                .map { it.id },
        )
        assertEquals(layerId(3), duplicated.activeLayerId)
        val copy =
            layersOf(duplicated.document)[1]
        assertEquals(source.name, copy.name)
        assertEquals(source.visibility, copy.visibility)
        assertSame(source.snapshot, copy.snapshot)
        assertSame(duplicated, fixture.controller.renderState)
        fixture.controller.callbacks.onUndo()
        assertEquals(
            listOf(LayerId.first(), layerId(2)),
            layersOf(fixture.controller.renderState.document)
                .map { it.id },
        )
        fixture.controller.callbacks.onRedo()
        assertEquals(duplicated.document, fixture.controller.renderState.document)
        assertEquals(layerId(3), fixture.controller.renderState.activeLayerId)
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

        assertNull(moved.layerNotice)
        assertEquals(
            listOf(
                LayerRowModel(LayerId.first(), name, LayerVisibility.Hidden),
                LayerRowModel(layerId(2), LayerName.empty, LayerVisibility.Visible),
            ),
            layerRowsOf(
                fixture.controller.renderState.document,
                fixture.controller.renderState.document.frames
                    .single()
                    .id,
            ),
        )
    }

    @Test
    fun `deleting the last layer returns the refusal instead of dropping it`() {
        val fixture = fixture()
        val layers = fixture.controller.callbacks.layers

        val refused = layers.onDelete(LayerId.first())

        assertEquals(LayerNotice.Kind.Failed, refused.layerNotice?.kind)
        assertSame(fixture.initialDocument, refused.document)
        // An equal render state leaves the StateFlow's instance in place, so a refusal compares by value.
        assertEquals(refused, fixture.controller.renderState)
    }

    private fun layerId(value: Int): LayerId = LayerId.create(value).requiredValue()
}
