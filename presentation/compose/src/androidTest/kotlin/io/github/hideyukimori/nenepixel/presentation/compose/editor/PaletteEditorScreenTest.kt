package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.core.application.editor.DocumentIdSource
import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonExportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonExportPort
import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonImportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonImportPort
import io.github.hideyukimori.nenepixel.core.application.workspace.EditorLayout
import io.github.hideyukimori.nenepixel.core.application.workspace.EditorTheme
import io.github.hideyukimori.nenepixel.core.domain.color.ColorChannel
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.presentation.compose.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** Issue #107 S8-1: the palette editor panel on a device, driven only through its test identities. */
internal class PaletteEditorScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule(ComponentActivity::class.java)

    private val windowSize = mutableStateOf(TABLETOP_SIZE)

    @Test
    fun openShowsTheDraftSlots() {
        val controller = controller()
        setEditorContent(controller)
        openPaletteEditor()
        composeRule.onNodeWithTag("editor_palette_editor_slot_1").assertIsDisplayed()
        assertEquals(controller.renderState.definition, draft(controller))
    }

    @Test
    fun hexInputAppliesTheNewRgbaToThePaletteEntry() {
        val controller = controller()
        setEditorContent(controller)
        openPaletteEditor()
        editorNode("editor_palette_editor_slot_2").performClick()
        enterHex(EDITED_HEX)
        editorNode("editor_palette_editor_apply").performClick()
        composeRule.onNodeWithTag("editor_palette_editor_apply").assertDoesNotExist()
        assertNull(controller.renderState.paletteEditSession)
        composeRule.onNodeWithTag("editor_open_palette").performClick()
        val description = composeRule.activity.getString(R.string.palette_entry, 2, 255, 0, 0, 128)
        composeRule.onNodeWithTag("editor_palette_entry_2").assertContentDescriptionEquals(description)
    }

    @Test
    fun appendAndRemoveChangeTheSlotCount() {
        val controller = controller()
        setEditorContent(controller)
        openPaletteEditor()
        editorNode("editor_palette_editor_append").performClick()
        assertEquals(PALETTE_COUNT + 1, draft(controller).palette.entryCount)
        composeRule.onNodeWithTag("editor_palette_colors").performScrollToIndex(PALETTE_COUNT)
        composeRule.onNodeWithTag("editor_palette_editor_slot_${PALETTE_COUNT + 1}").assertIsDisplayed().performClick()
        editorNode("editor_palette_editor_remove").performClick()
        assertEquals(PALETTE_COUNT, draft(controller).palette.entryCount)
    }

    @Test
    fun moveDownThenUpRestoresOrderAndDraftHistoryUndoesAndRedoes() {
        val controller = controller()
        setEditorContent(controller)
        openPaletteEditor()
        val original = draft(controller)
        editorNode("editor_palette_editor_slot_2").performClick()
        editorNode("editor_palette_editor_move_down").performClick()
        val moved = draft(controller)
        assertNotEquals(original, moved)
        editorNode("editor_palette_editor_move_up").performClick()
        assertEquals(original, draft(controller))
        editorNode("editor_palette_editor_undo").performClick()
        assertEquals(moved, draft(controller))
        editorNode("editor_palette_editor_redo").performClick()
        assertEquals(original, draft(controller))
    }

    @Test
    fun cancelLeavesTheDocumentPaletteUnchanged() {
        val controller = controller()
        setEditorContent(controller)
        val before = controller.renderState.definition
        val snapshot = controller.renderState.snapshot
        openPaletteEditor()
        editorNode("editor_palette_editor_slot_2").performClick()
        enterHex(EDITED_HEX)
        assertNotEquals(before, draft(controller))
        editorNode("editor_palette_editor_cancel").performClick()
        composeRule.onNodeWithTag("editor_palette_editor_cancel").assertDoesNotExist()
        assertNull(controller.renderState.paletteEditSession)
        assertEquals(before, controller.renderState.definition)
        assertEquals(snapshot, controller.renderState.snapshot)
    }

    @Test
    fun darkTabletopAndLightHandheldShowThePanelIdentities() {
        val controller = controller()
        setEditorContent(controller)
        openPaletteEditor()
        listOf(
            Triple(EditorTheme.Dark, EditorLayout.Tabletop, TABLETOP_SIZE),
            Triple(EditorTheme.Light, EditorLayout.Handheld, HANDHELD_SIZE),
        ).forEach { (theme, layout, size) ->
            composeRule.runOnIdle {
                windowSize.value = size
                controller.callbacks.onSetAppearance(
                    controller.renderState.appearance.copy(theme = theme, layout = layout),
                )
            }
            assertEquals(theme, controller.renderState.appearance.theme)
            assertEquals(layout, controller.renderState.appearance.layout)
            PANEL_IDENTITIES.forEach { editorNode(it).assertIsDisplayed() }
        }
    }

    @Test
    fun cancelledPaletteJsonExportShowsTheCancelledStatus() {
        val controller = controller()
        val export = PaletteJsonExportPort { PaletteJsonExportOutcome.Cancelled }
        setEditorContent(controller, TestPaletteJsonPorts(export = export))
        openPaletteEditor()
        editorNode("editor_export_palette_json").performClick()
        val cancelled = composeRule.activity.getString(R.string.operation_cancelled)
        composeRule.onNodeWithTag("editor_operation_status").assertTextEquals(cancelled)
    }

    @Test
    fun importedTwoSlotPaletteMapsByNearestAndApplies() {
        val controller = controller()
        val imported = definition(listOf(color(0, 0, 0, 255), color(255, 255, 255, 255)), 0)
        val import = PaletteJsonImportPort { PaletteJsonImportOutcome.Imported(imported) }
        setEditorContent(controller, TestPaletteJsonPorts(import = import))
        composeRule.onNodeWithTag("editor_open_palette").performClick()
        composeRule.onNodeWithTag("editor_import_palette_json").performClick()
        editorNode("editor_palette_import_title").assertIsDisplayed()
        editorNode("editor_palette_editor_mode_nearest").performClick()
        editorNode("editor_palette_editor_import_confirm").performClick()
        editorNode("editor_palette_editor_apply").performClick()
        assertNull(controller.renderState.paletteEditSession)
        assertEquals(2, controller.renderState.palette.entryCount)
        assertEquals(imported, controller.renderState.definition)
    }

    @Test
    fun recreatedActivityKeepsThePanelAndTheDraft() {
        val controller = controller()
        setEditorContent(controller)
        openPaletteEditor()
        editorNode("editor_palette_editor_slot_2").performClick()
        enterHex(EDITED_HEX)
        val edited = draft(controller)
        composeRule.activityRule.scenario.recreate()
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContentView(ComposeView(activity).apply { setContent { EditorContent(controller) } })
        }
        composeRule.onNodeWithTag("editor_palette_editor_slot_1").assertIsDisplayed()
        editorNode("editor_palette_editor_hex").assert(hasText(EDITED_HEX))
        assertEquals(edited, draft(controller))
    }

    @Test
    fun fullPaletteDisablesAppend() {
        val controller = controller(paletteCount = MAXIMUM_PALETTE_COUNT)
        setEditorContent(controller)
        openPaletteEditor()
        assertEquals(MAXIMUM_PALETTE_COUNT, draft(controller).palette.entryCount)
        editorNode("editor_palette_editor_append").assertIsNotEnabled()
    }

    private fun setEditorContent(
        controller: EditorController,
        paletteJson: TestPaletteJsonPorts = TestPaletteJsonPorts(),
    ) {
        composeRule.setContent { EditorContent(controller, paletteJson) }
    }

    @Composable
    private fun EditorContent(
        controller: EditorController,
        paletteJson: TestPaletteJsonPorts = TestPaletteJsonPorts(),
    ) {
        val size = windowSize.value
        Box(Modifier.requiredSize(size).consumeWindowInsets(WindowInsets.safeDrawing)) {
            TestNenePixelEditor(controller, Modifier.requiredSize(size), paletteJson = paletteJson)
        }
    }

    private fun openPaletteEditor() {
        composeRule.onNodeWithTag("editor_open_palette").performClick()
        composeRule.onNodeWithTag("editor_palette_editor_open").performClick()
        composeRule.onNodeWithTag("editor_palette_editor_cancel").assertExists()
    }

    private fun editorNode(identity: String): SemanticsNodeInteraction =
        composeRule.onNodeWithTag(identity).performScrollTo()

    private fun enterHex(hex: String) {
        editorNode("editor_palette_editor_hex").performTextReplacement(hex)
        composeRule.onNodeWithTag("editor_palette_editor_hex").performImeAction()
    }

    private fun draft(controller: EditorController): PaletteDefinition =
        requireNotNull(controller.renderState.paletteEditSession) { "No palette edit session" }.draft

    private fun controller(paletteCount: Int = PALETTE_COUNT): EditorController {
        val size =
            CanvasSize.create(
                CanvasWidth.create(DOCUMENT_WIDTH).requiredValue(),
                CanvasHeight.create(DOCUMENT_HEIGHT).requiredValue(),
            )
        val colors = List(paletteCount) { color(it, 0, 0, 255) }
        return EditorController.create(
            EditorRuntime.create(size, definition(colors, DEFAULT_INDEX), PaletteEditorDocumentIdSource),
        )
    }

    private fun definition(
        colors: List<PixelColor>,
        defaultIndex: Int,
    ): PaletteDefinition =
        PaletteDefinition
            .create(Palette.create(colors).requiredValue(), PaletteIndex.create(defaultIndex).requiredValue())
            .requiredValue()

    private fun color(
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int,
    ): PixelColor =
        PixelColor.create(
            ColorChannel.create(red).requiredValue(),
            ColorChannel.create(green).requiredValue(),
            ColorChannel.create(blue).requiredValue(),
            ColorChannel.create(alpha).requiredValue(),
        )

    private fun <T> DomainValueResult<T>.requiredValue(): T =
        when (this) {
            is DomainValueResult.Created -> value
            is DomainValueResult.Rejected -> error("Invalid palette editor fixture: $rejection")
        }

    private companion object {
        const val PALETTE_COUNT: Int = 9
        const val MAXIMUM_PALETTE_COUNT: Int = 256
        const val DEFAULT_INDEX: Int = 8
        const val DOCUMENT_WIDTH: Int = 3
        const val DOCUMENT_HEIGHT: Int = 2
        const val EDITED_HEX: String = "#FF000080"
        val TABLETOP_SIZE: DpSize = DpSize(720.dp, 600.dp)
        val HANDHELD_SIZE: DpSize = DpSize(600.dp, 400.dp)
        val PANEL_IDENTITIES: List<String> =
            listOf(
                "editor_palette_editor_slot_1",
                "editor_palette_editor_hex",
                "editor_palette_editor_append",
                "editor_palette_editor_undo",
                "editor_palette_editor_apply",
                "editor_palette_editor_cancel",
                "editor_export_palette_json",
            )
    }
}

private object PaletteEditorDocumentIdSource : DocumentIdSource {
    override fun nextDocumentId(): DocumentId =
        when (val result = DocumentId.create("2".repeat(DOCUMENT_ID_LENGTH))) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Invalid palette editor fixture document ID: ${result.rejection}")
        }

    private const val DOCUMENT_ID_LENGTH: Int = 32
}
