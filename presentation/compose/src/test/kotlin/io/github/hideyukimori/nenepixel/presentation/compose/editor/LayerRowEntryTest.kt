package io.github.hideyukimori.nenepixel.presentation.compose.editor

import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerName
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.presentation.compose.R
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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

    private fun entry(
        visibility: LayerVisibility,
        current: Boolean,
    ): LayerRowEntry = LayerRowEntry(LayerRowModel(LayerId.first(), LayerName.empty, visibility), current)
}
