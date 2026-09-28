package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import io.github.hideyukimori.nenepixel.core.application.editor.DocumentIdSource
import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
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
import org.junit.Assert.assertEquals

/**
 * A 4x3 document with an eight-slot palette for the quick-select placement and recomposition tests (#108 S2c).
 * The editor fills the test window, so the layout follows the device's real size and orientation.
 */
internal object QuickSelectFixture {
    const val CANVAS_TAG: String = "editor_canvas_4_3"
    const val CONTROL_TAG: String = "editor_quick_select"
    const val FAN_TAG: String = "editor_quick_select_fan"
    const val SLOT_COUNT: Int = 8
    val DOCK_TAGS: List<String> =
        listOf("editor_pencil_tool", "editor_eraser_tool", "editor_undo", "editor_redo", "editor_open_palette")

    fun controller(): EditorController {
        val size =
            CanvasSize.create(
                CanvasWidth.create(DOCUMENT_WIDTH).requiredValue(),
                CanvasHeight.create(DOCUMENT_HEIGHT).requiredValue(),
            )
        val colors = (0 until SLOT_COUNT).map { slot -> opaque(slot * CHANNEL_STEP, CHANNEL_MAX - slot * CHANNEL_STEP) }
        val palette = Palette.create(colors).requiredValue()
        val definition = PaletteDefinition.create(palette, index(0)).requiredValue()
        return EditorController.create(EditorRuntime.create(size, definition, FixedFixtureDocumentIdSource))
    }

    fun show(
        rule: ComposeContentTestRule,
        controller: EditorController,
    ) {
        rule.setContent { TestNenePixelEditor(controller, Modifier.fillMaxSize()) }
        rule.waitForIdle()
    }

    /** Paints one stroke with each of [slots] at the canvas centre, so recent holds them newest first. */
    fun paint(
        rule: ComposeContentTestRule,
        controller: EditorController,
        slots: List<Int>,
    ) {
        slots.forEach { slot ->
            rule.runOnIdle { controller.callbacks.onSelectPaletteEntry(index(slot)) }
            rule.onNodeWithTag(CANVAS_TAG).performTouchInput { click(center) }
            rule.waitForIdle()
        }
        assertEquals(slots.reversed().map(::index), controller.renderState.quickSelection.recent)
    }

    fun index(value: Int): PaletteIndex = PaletteIndex.create(value).requiredValue()

    private fun opaque(
        red: Int,
        blue: Int,
    ): PixelColor =
        PixelColor.create(
            ColorChannel.create(red).requiredValue(),
            ColorChannel.create(0).requiredValue(),
            ColorChannel.create(blue).requiredValue(),
            ColorChannel.create(CHANNEL_MAX).requiredValue(),
        )

    private fun <T> DomainValueResult<T>.requiredValue(): T =
        when (this) {
            is DomainValueResult.Created -> value
            is DomainValueResult.Rejected -> error("Invalid quick-select fixture: $rejection")
        }

    private const val DOCUMENT_WIDTH: Int = 4
    private const val DOCUMENT_HEIGHT: Int = 3
    private const val CHANNEL_MAX: Int = 255
    private const val CHANNEL_STEP: Int = 32
}

private object FixedFixtureDocumentIdSource : DocumentIdSource {
    override fun nextDocumentId(): DocumentId =
        when (val result = DocumentId.create("4".repeat(DOCUMENT_ID_LENGTH))) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Invalid quick-select fixture document ID: ${result.rejection}")
        }

    private const val DOCUMENT_ID_LENGTH: Int = 32
}
