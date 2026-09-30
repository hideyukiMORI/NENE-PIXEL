package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.application.workspace.WorkspaceAction
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.presentation.compose.EditorFixture
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.fixture
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues.position
import io.github.hideyukimori.nenepixel.presentation.compose.requiredValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/** The adapter keeps a layer notice only while the document and the active layer still match it (#144 U6). */
internal class LayerNoticeValidityTest {
    @Test
    fun `a deletion notice lasts until the next revision`() {
        val fixture = fixture()
        val layers = fixture.controller.callbacks.layers
        layers.onAdd()

        val deleted = layers.onDelete(layerId(2))
        val undone = fixture.controller.callbacks.onUndo()

        assertEquals(LayerNotice.Kind.Deleted, deleted.layerNotice?.kind)
        assertEquals(deleted.document.revision, deleted.layerNotice?.revision)
        assertNull(undone.layerNotice)
    }

    @Test
    fun `another layer edit replaces the deletion notice with none`() {
        val fixture = fixture()
        val layers = fixture.controller.callbacks.layers
        layers.onAdd()
        layers.onDelete(layerId(2))

        val hidden = layers.onSetVisibility(LayerId.first(), LayerVisibility.Hidden)

        assertNull(hidden.layerNotice)
    }

    @Test
    fun `a hidden target notice names the layer and ends when another layer becomes active`() {
        val scene = Scene(fixture())
        scene.layers.onAdd()
        scene.layers.onSetVisibility(layerId(2), LayerVisibility.Hidden)

        val refused = scene.draw()
        scene.fixture.runtime.reduce(WorkspaceAction.SelectLayer(LayerId.first()))
        val moved = scene.adapter.renderState

        assertEquals(LayerNotice.Kind.HiddenTarget, refused.layerNotice?.kind)
        assertEquals(layerId(2), refused.layerNotice?.target)
        assertNull(moved.layerNotice)
    }

    @Test
    fun `the same refusal twice raises two notices`() {
        val scene = Scene(fixture())
        scene.layers.onSetVisibility(LayerId.first(), LayerVisibility.Hidden)

        val first = scene.draw().layerNotice
        val second = scene.draw().layerNotice

        assertEquals(LayerNotice.Kind.HiddenTarget, first?.kind)
        assertEquals(LayerNotice.Kind.HiddenTarget, second?.kind)
        assertNotEquals(first?.serial, second?.serial)
    }

    @Test
    fun `settling clears only the notice with the same serial`() {
        val scene = Scene(fixture())
        scene.layers.onSetVisibility(LayerId.first(), LayerVisibility.Hidden)
        val notice = scene.draw().layerNotice ?: error("Drawing on a hidden layer raised no notice.")

        val stale = scene.layers.onNoticeSettled(notice.serial - 1)
        val settled = scene.layers.onNoticeSettled(notice.serial)
        val again = scene.adapter.renderState

        assertSame(notice, stale.layerNotice)
        assertNull(settled.layerNotice)
        assertNull(again.layerNotice)
    }

    private fun layerId(value: Int): LayerId = LayerId.create(value).requiredValue()

    /** One adapter and its layer route over the fixture's runtime, publishing nothing. */
    private class Scene(
        val fixture: EditorFixture,
    ) {
        val adapter: EditorRuntimeAdapter = EditorRuntimeAdapter(fixture.runtime)
        val layers: EditorLayerCallbacks = EditorLayerCallbacks(fixture.runtime, adapter) { state -> state }

        fun draw(): EditorRenderState {
            val size = fixture.runtime.state.documentState.size
            return adapter.reduce(WorkspaceAction.BeginGesturePreview(size, position(0, 0))).renderState
        }
    }
}
