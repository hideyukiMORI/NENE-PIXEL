package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo

/** Issue #168: existing palette acceptance follows the secondary disclosure and fixed footer. */
internal object PaletteEditorTestNavigation {
    fun node(
        rule: ComposeContentTestRule,
        identity: String,
    ): SemanticsNodeInteraction {
        if (identity in SECONDARY && rule.onAllNodesWithTag(identity).fetchSemanticsNodes().isEmpty()) {
            rule.onNodeWithTag("editor_palette_editor_operations").performScrollTo().performClick()
        }
        if (identity.startsWith("editor_palette_editor_slot_")) {
            rule.onNodeWithTag("editor_palette_colors").performScrollTo()
        }
        val node = rule.onNodeWithTag(identity)
        return if (identity in FOOTER) node else node.performScrollTo()
    }

    private val FOOTER =
        setOf(
            "editor_palette_editor_undo",
            "editor_palette_editor_redo",
            "editor_palette_editor_apply",
            "editor_palette_editor_cancel",
        )
    private val SECONDARY =
        setOf(
            "editor_palette_editor_set_default",
            "editor_palette_editor_append",
            "editor_palette_editor_remove",
            "editor_palette_editor_move_up",
            "editor_palette_editor_move_down",
            "editor_import_palette_json",
            "editor_export_palette_json",
        )
}
