package io.github.hideyukimori.nenepixel.presentation.compose.editor

import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayMemoryProjection.Settled
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.ReferenceUnderlay
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.RememberedUnderlay
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayInteraction
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayOpacity
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.UnderlayVisibility
import io.github.hideyukimori.nenepixel.presentation.compose.EditorFixture
import io.github.hideyukimori.nenepixel.presentation.compose.PresentationTestValues
import io.github.hideyukimori.nenepixel.presentation.compose.editor.UnderlayDisplayFixture.HEIGHT
import io.github.hideyukimori.nenepixel.presentation.compose.editor.UnderlayDisplayFixture.WIDTH
import io.github.hideyukimori.nenepixel.presentation.compose.editor.UnderlayDisplayFixture.greenImage
import io.github.hideyukimori.nenepixel.presentation.compose.editor.UnderlayDisplayFixture.readCanvas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

/**
 * Issue #172 (ADR 0034, Enforcement impact): an underlay set in one runtime is shown again, with its placement,
 * opacity and visibility, after the same work is loaded into a new runtime. Each test shows editor A; a reload
 * replaces it, inside the same composition, with editor B, a fresh runtime that shares the in-memory underlay store
 * and the saved work. The test asks for every publish and recall in place of the app's scheduler.
 */
internal class UnderlayMemoryRestoreTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val port = InMemoryUnderlayMemoryPort()
    private val memory = TestUnderlayMemory(port)
    private val storage = SavedWorkStoragePort()
    private val shown = mutableStateOf<EditorFixture?>(null)

    @Test
    fun reloadingTheSameWorkInANewRuntimeRestoresItsUnderlay() {
        val placed = placeAndPublish(start()) { underlay -> underlay }
        saveWork()
        val second = loadInNewRuntime()

        val restored = underlay(second)
        assertSame(placed.image, restored.image)
        assertEquals(placed.placement, restored.placement)
        assertEquals(placed.opacity, restored.opacity)
        assertEquals(UnderlayVisibility.Shown, restored.visibility)
        assertEquals(UnderlayInteraction.Resting, restored.interaction)
        val reading = readCanvas(composeRule, second)
        val expected = TransparencyExpectation.shown(HALF_GREEN, reading.backdropAt(SAMPLE_X, SAMPLE_Y))
        val actual = reading.shownAt(SAMPLE_X, SAMPLE_Y)
        TransparencyExpectation.assertArgbNear("restored", expected, actual, BLEND_TOLERANCE)
    }

    @Test
    fun anUnderlayRememberedHiddenComesBackHidden() {
        val placed = placeAndPublish(start()) { underlay -> underlay.toggledVisibility() }
        saveWork()
        val second = loadInNewRuntime()

        val restored = underlay(second)
        assertEquals(UnderlayVisibility.Hidden, restored.visibility)
        assertEquals(placed.placement, restored.placement)
        assertEquals(placed.opacity, restored.opacity)
        val reading = readCanvas(composeRule, second)
        val backdrop = reading.backdropAt(SAMPLE_X, SAMPLE_Y)
        TransparencyExpectation.assertArgbNear("hidden", backdrop, reading.shownAt(SAMPLE_X, SAMPLE_Y), 0)
        LayerMenuFixture.openPanel(composeRule)
        composeRule.waitUntil(TIMEOUT_MILLIS) { UnderlayAdjustFixture.exists(composeRule, VISIBILITY_TAG) }
        assertFalse(UnderlayAdjustFixture.exists(composeRule, PICK_TAG))
    }

    @Test
    fun removingTheUnderlayForgetsItsRecord() {
        val first = start()
        placeAndPublish(first) { underlay -> underlay }
        val callbacks = first.controller.callbacks.underlay
        composeRule.runOnIdle { callbacks.onClear() }
        request(first) { publish() }
        assertNull(port.stored(first.controller.documentState.id))
        saveWork()
        val second = loadInNewRuntime()

        assertNull(second.controller.renderState.underlay)
    }

    @Test
    fun aWorkThatWasNeverRememberedShowsNoUnderlay() {
        val first = start()
        val remembered = first.controller.documentState.id
        placeAndPublish(first) { underlay -> underlay }
        clickFileAction(NEW_DOCUMENT_TAG)
        composeRule.onNodeWithTag(CREATE_TAG).performClick()
        composeRule.waitUntil(TIMEOUT_MILLIS) { first.controller.documentState.id != remembered }
        val document = composeRule.runOnIdle { first.controller.documentState }
        request(first) { recall() }

        assertNull(first.controller.renderState.underlay)
        assertSame(document, first.controller.documentState)
        assertNotEquals(remembered, document.id)
        assertNotNull(port.stored(remembered))
    }

    /** Shows editor A; a later [loadInNewRuntime] replaces it with editor B in the same composition. */
    private fun start(): EditorFixture {
        val first = editor()
        shown.value = first
        composeRule.setContent {
            val editor = shown.value
            if (editor != null) {
                key(editor) {
                    TestNenePixelEditor(editor.controller, projectStorage = storage, underlayMemory = memory)
                }
            }
        }
        composeRule.waitForIdle()
        return first
    }

    /** Shows a new runtime, loads the saved work into it and recalls its underlay. */
    private fun loadInNewRuntime(): EditorFixture {
        val second = editor()
        composeRule.runOnIdle { shown.value = second }
        composeRule.waitForIdle()
        clickFileAction(LOAD_TAG)
        request(second) { recall() }
        assertEquals(storage.saved?.id, second.controller.documentState.id)
        return second
    }

    /**
     * Places a 2 x 2 green underlay, moves and scales it, sets half opacity, applies [finish] and publishes. Returns
     * the published underlay, which the store then holds.
     */
    private fun placeAndPublish(
        editor: EditorFixture,
        finish: (ReferenceUnderlay) -> ReferenceUnderlay,
    ): ReferenceUnderlay {
        val callbacks = editor.controller.callbacks.underlay
        val image = greenImage(IMAGE_EDGE, IMAGE_EDGE)
        composeRule.runOnIdle {
            callbacks.onSet(ReferenceUnderlay.placed(image, editor.controller.renderState.document.size))
        }
        composeRule.runOnIdle { callbacks.onSet(underlay(editor).withPlacement(MOVED, MOVED, MOVED_SCALE)) }
        composeRule.runOnIdle { callbacks.onSet(underlay(editor).withOpacity(UnderlayOpacity.create(HALF))) }
        composeRule.runOnIdle { callbacks.onSet(finish(underlay(editor))) }
        request(editor) { publish() }
        val placed = underlay(editor)
        assertEquals(RememberedUnderlay.of(placed), port.stored(editor.controller.documentState.id))
        return placed
    }

    /**
     * Waits until [editor]'s underlay memory has something due, runs [action] as the scheduler would, waits until it
     * is settled, and then until the main thread has brought the screen to the runtime.
     */
    private fun request(
        editor: EditorFixture,
        action: TestUnderlayMemory.() -> Unit,
    ) {
        val projection = editor.runtime.underlayMemory
        composeRule.waitUntil(TIMEOUT_MILLIS) { projection.value != Settled }
        composeRule.runOnIdle { memory.action() }
        composeRule.waitUntil(TIMEOUT_MILLIS) { projection.value == Settled }
        composeRule.runOnIdle { }
    }

    private fun saveWork() {
        clickFileAction(SAVE_AS_TAG)
        composeRule.waitUntil(TIMEOUT_MILLIS) { storage.saved != null }
    }

    /** Opens the file surface and presses the action [tag] once it is enabled. */
    private fun clickFileAction(tag: String) {
        composeRule.onNodeWithTag(FILE_TAG).performClick()
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasTestTag(tag) and isEnabled()).fetchSemanticsNodes().size == 1
        }
        composeRule.onNodeWithTag(tag).performClick()
    }

    private companion object {
        const val FILE_TAG: String = "editor_file"
        const val SAVE_AS_TAG: String = "editor_save_as"
        const val LOAD_TAG: String = "editor_load"
        const val NEW_DOCUMENT_TAG: String = "editor_new_document"
        const val CREATE_TAG: String = "editor_create"
        const val VISIBILITY_TAG: String = "editor_underlay_visibility"
        const val PICK_TAG: String = "editor_underlay_pick"
        const val TIMEOUT_MILLIS: Long = 5_000
        const val IMAGE_EDGE: Int = 2
        const val MOVED: Double = 1.0
        const val MOVED_SCALE: Double = 1.0
        const val HALF: Int = 128
        const val HALF_GREEN: Int = 0x8000FF00.toInt()
        const val BLEND_TOLERANCE: Int = 2

        /** Covered by the moved 2 x 2 image, which spans document pixels (1, 1) to (2, 2). */
        const val SAMPLE_X: Int = 2
        const val SAMPLE_Y: Int = 1

        /** A clean 4 x 3 work holding red and green; every pixel is Empty, so the underlay shows through. */
        fun editor(): EditorFixture =
            PresentationTestValues.fixture(
                PresentationTestValues.canvas(WIDTH, HEIGHT),
                listOf(PresentationTestValues.red, PresentationTestValues.green),
            )

        fun underlay(editor: EditorFixture): ReferenceUnderlay = requireNotNull(editor.controller.renderState.underlay)
    }
}
