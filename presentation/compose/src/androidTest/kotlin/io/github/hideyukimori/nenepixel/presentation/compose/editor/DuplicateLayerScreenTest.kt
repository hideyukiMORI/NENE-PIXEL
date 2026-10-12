package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerName
import io.github.hideyukimori.nenepixel.core.domain.layer.LayerVisibility
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.presentation.compose.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

/** Issue #208: localized row-menu duplication, bounded capacity and the existing native history controls. */
internal class DuplicateLayerScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun duplicateTargetsAnInactiveHiddenRowClosesTheMenuAndUndoRedoRestoreIt() {
        val controller = LayerMenuFixture.show(composeRule, layers = 3)
        val sourceId =
            controller.renderState.document.layers
                .first()
                .id
        composeRule.runOnIdle {
            controller.callbacks.layers.onRename(sourceId, name("Ink"))
            controller.callbacks.layers.onSetVisibility(sourceId, LayerVisibility.Hidden)
        }
        val before = controller.renderState.document
        val source = before.layers.first()
        val active = controller.renderState.activeLayerId
        LayerMenuFixture.openPanel(composeRule)
        LayerMenuFixture.openMenu(composeRule, sourceId)
        composeRule.onNodeWithTag(DUPLICATE_TAG).assertIsEnabled().assertTextEquals(text(R.string.layer_duplicate))
        LayerMenuFixture.click(composeRule, DUPLICATE_TAG)

        val after = controller.renderState.document
        val copy = after.layers[1]
        assertEquals(listOf(sourceId, copy.id, before.layers[1].id, active), after.layers.map { it.id })
        assertEquals(copy.id, controller.renderState.activeLayerId)
        assertEquals(source.name, copy.name)
        assertEquals(LayerVisibility.Hidden, copy.visibility)
        assertSame(source.snapshot, copy.snapshot)
        LayerMenuFixture.row(composeRule, copy.id).assertExists()
        composeRule.onNodeWithTag(DUPLICATE_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag("editor_undo").performClick()
        assertEquals(before, controller.renderState.document)
        composeRule.onNodeWithTag("editor_redo").performClick()
        assertEquals(after, controller.renderState.document)
        assertEquals(copy.id, controller.renderState.activeLayerId)
    }

    @Test
    fun atSixteenLayersDuplicateIsDisabledAndDoesNotChangeTheDocumentOrSelection() {
        val controller = LayerMenuFixture.show(composeRule, layers = LayerMenuFixture.MAX_LAYERS)
        val before = controller.renderState.document
        val active = controller.renderState.activeLayerId
        LayerMenuFixture.openPanel(composeRule)
        LayerMenuFixture.openMenu(composeRule, active)
        composeRule.onNodeWithTag(DUPLICATE_TAG).assertIsNotEnabled().performTouchInput { click(center) }
        assertSame(before, controller.renderState.document)
        assertEquals(active, controller.renderState.activeLayerId)
        composeRule.onNodeWithTag(DUPLICATE_TAG).assertExists()
    }

    @Test
    fun theOnlyEmptyUnnamedLayerCanBeDuplicatedWithoutANameSuffix() {
        val controller = LayerMenuFixture.show(composeRule, layers = 1)
        val before = controller.renderState.document
        LayerMenuFixture.choose(composeRule, before.layers.single().id, DUPLICATE_TAG)
        val after = controller.renderState.document
        assertEquals(2, after.layers.size)
        assertEquals(LayerName.empty, after.layers.last().name)
        assertSame(before.layers.single().snapshot, after.layers.last().snapshot)
        assertEquals(after.layers.last().id, controller.renderState.activeLayerId)
        composeRule.onNodeWithTag("editor_undo").performClick()
        assertEquals(before, controller.renderState.document)
        composeRule.onNodeWithTag("editor_redo").performClick()
        assertEquals(after, controller.renderState.document)
    }

    private fun name(value: String): LayerName =
        when (val result = LayerName.create(value)) {
            is DomainValueResult.Created -> result.value
            is DomainValueResult.Rejected -> error("Invalid duplicate test name: ${result.rejection}")
        }

    private fun text(resource: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resource)

    private companion object {
        const val DUPLICATE_TAG: String = "editor_layer_duplicate"
    }
}
