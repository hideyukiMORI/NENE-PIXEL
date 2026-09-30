package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.hideyukimori.nenepixel.core.application.editor.DocumentIdSource
import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.domain.color.ColorChannel
import io.github.hideyukimori.nenepixel.core.domain.color.PixelColor
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasHeight
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasSize
import io.github.hideyukimori.nenepixel.core.domain.geometry.CanvasWidth
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerId
import io.github.hideyukimori.nenepixel.core.domain.palette.Palette
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteDefinition
import io.github.hideyukimori.nenepixel.core.domain.palette.PaletteIndex
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult

/**
 * An editor with a given number of layers, and the layer panel's menu and add-row identities, for the #144 U5
 * tests. The editor is 600dp square, so the panel holds three rows and the add row without scrolling.
 */
internal object LayerMenuFixture {
    const val RENAME_TAG: String = "editor_layer_rename"
    const val CANCEL_TAG: String = "editor_cancel"
    const val MOVE_UP_TAG: String = "editor_layer_move_up"
    const val MOVE_DOWN_TAG: String = "editor_layer_move_down"
    const val DELETE_TAG: String = "editor_layer_delete"
    const val ADD_TAG: String = "editor_layer_add"
    const val LIMIT_TAG: String = "editor_layer_limit"
    const val MAX_LAYERS: Int = 16

    /**
     * Shows the editor and adds layers until there are [layers]; the last one added is the active, front layer.
     * [onBackDispatcher] receives the test activity's back dispatcher, for the tests that press Back.
     */
    fun show(
        rule: ComposeContentTestRule,
        layers: Int,
        onBackDispatcher: (OnBackPressedDispatcher?) -> Unit = {},
    ): EditorController {
        val controller = controller()
        rule.setContent {
            onBackDispatcher(LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher)
            Box(Modifier.requiredSize(EDGE, EDGE).consumeWindowInsets(WindowInsets.safeDrawing)) {
                TestNenePixelEditor(controller, Modifier.requiredSize(EDGE, EDGE))
            }
        }
        rule.waitForIdle()
        rule.runOnIdle { repeat(layers - 1) { controller.callbacks.layers.onAdd() } }
        rule.waitForIdle()
        return controller
    }

    fun frontToBack(controller: EditorController): List<LayerId> =
        controller.renderState.document.layers
            .asReversed()
            .map { layer -> layer.id }

    fun openPanel(rule: ComposeContentTestRule) = click(rule, CHIP_TAG)

    fun openMenu(
        rule: ComposeContentTestRule,
        id: LayerId,
    ) = click(rule, MORE_TAG_PREFIX + id.value)

    /** Opens the panel and [id]'s menu, then chooses the item tagged [tag]. */
    fun choose(
        rule: ComposeContentTestRule,
        id: LayerId,
        tag: String,
    ) {
        openPanel(rule)
        openMenu(rule, id)
        click(rule, tag)
    }

    fun click(
        rule: ComposeContentTestRule,
        tag: String,
    ) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    fun row(
        rule: ComposeContentTestRule,
        id: LayerId,
    ): SemanticsNodeInteraction = rule.onNodeWithTag(ROW_TAG_PREFIX + id.value)

    fun undo(
        rule: ComposeContentTestRule,
        controller: EditorController,
    ) {
        rule.runOnIdle { controller.callbacks.onUndo() }
        rule.waitForIdle()
    }

    private fun controller(): EditorController {
        val size =
            CanvasSize.create(
                CanvasWidth.create(DOCUMENT_WIDTH).requiredValue(),
                CanvasHeight.create(DOCUMENT_HEIGHT).requiredValue(),
            )
        val palette = Palette.create(listOf(opaque(CHANNEL_MAX, 0), opaque(0, CHANNEL_MAX))).requiredValue()
        val definition = PaletteDefinition.create(palette, PaletteIndex.create(0).requiredValue()).requiredValue()
        return EditorController.create(EditorRuntime.create(size, definition, FixedMenuDocumentIdSource))
    }

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
            is DomainValueResult.Rejected -> error("Invalid layer-menu fixture: $rejection")
        }

    private val EDGE: Dp = 600.dp
    private const val CHIP_TAG: String = "editor_layer_chip"
    private const val ROW_TAG_PREFIX: String = "editor_layer_row_"
    private const val MORE_TAG_PREFIX: String = "editor_layer_more_"
    private const val DOCUMENT_WIDTH: Int = 64
    private const val DOCUMENT_HEIGHT: Int = 48
    private const val CHANNEL_MAX: Int = 255
}

private object FixedMenuDocumentIdSource : DocumentIdSource {
    override fun nextDocumentId(): DocumentId =
        when (val result = DocumentId.create("6".repeat(DOCUMENT_ID_LENGTH))) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Invalid layer-menu fixture document ID: ${result.rejection}")
        }

    private const val DOCUMENT_ID_LENGTH: Int = 32
}
