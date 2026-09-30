package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerName
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.presentation.compose.R
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class LayerRowEntryTest {
    @Test
    fun `a visible row offers to hide and shows the open eye`() {
        val entry = entry(LayerVisibility.Visible, current = false)

        assertEquals(LayerVisibility.Hidden, entry.toggleTarget)
        assertEquals(R.string.layer_hide, entry.toggleDescription)
        assertEquals(EditorIcon.Visible, entry.visibilityIcon)
    }

    @Test
    fun `a hidden row offers to show and shows the closed eye`() {
        val entry = entry(LayerVisibility.Hidden, current = false)

        assertEquals(LayerVisibility.Visible, entry.toggleTarget)
        assertEquals(R.string.layer_show, entry.toggleDescription)
        assertEquals(EditorIcon.Hidden, entry.visibilityIcon)
    }

    @Test
    fun `only the active layer row speaks the current state`() {
        assertEquals(R.string.layer_row_state_current, entry(LayerVisibility.Visible, current = true).stateDescription)
        assertEquals(R.string.layer_row_state_current, entry(LayerVisibility.Hidden, current = true).stateDescription)
        assertNull(entry(LayerVisibility.Visible, current = false).stateDescription)
    }

    @Test
    fun `a middle row moves one step either way`() {
        val entry = placed(position = 1, layerCount = 3)

        assertFalse(entry.frontMost)
        assertFalse(entry.backMost)
        assertFalse(entry.only)
        assertEquals(2, entry.moveUpPosition)
        assertEquals(0, entry.moveDownPosition)
    }

    @Test
    fun `the top row is front-most and the bottom row is back-most`() {
        val front = placed(position = 2, layerCount = 3)
        val back = placed(position = 0, layerCount = 3)

        assertTrue(front.frontMost)
        assertFalse(front.backMost)
        assertFalse(back.frontMost)
        assertTrue(back.backMost)
    }

    @Test
    fun `the only layer is front-most, back-most and alone`() {
        val entry = placed(position = 0, layerCount = 1)

        assertTrue(entry.frontMost)
        assertTrue(entry.backMost)
        assertTrue(entry.only)
    }

    private fun entry(
        visibility: LayerVisibility,
        current: Boolean,
    ): LayerRowEntry {
        val row = LayerRowModel(LayerId.first(), LayerName.empty, visibility)
        return LayerRowEntry(row, current, position = 0, layerCount = 1)
    }

    private fun placed(
        position: Int,
        layerCount: Int,
    ): LayerRowEntry =
        LayerRowEntry(
            LayerRowModel(LayerId.first(), LayerName.empty, LayerVisibility.Visible),
            current = false,
            position = position,
            layerCount = layerCount,
        )
}
