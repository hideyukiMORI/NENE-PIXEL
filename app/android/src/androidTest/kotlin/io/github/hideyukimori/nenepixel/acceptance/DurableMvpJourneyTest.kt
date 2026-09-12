package io.github.hideyukimori.nenepixel.acceptance

import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import io.github.hideyukimori.nenepixel.EditorRuntimeViewModel
import io.github.hideyukimori.nenepixel.MainActivity
import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryAvailability
import io.github.hideyukimori.nenepixel.core.application.editor.DocumentDirtyState
import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntimeState
import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveLastOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceLastOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationPhase
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportMappingResult
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportSurface
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportTransform
import io.github.hideyukimori.nenepixel.core.application.workspace.viewport.ViewportValueResult
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelPosition
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelX
import io.github.hideyukimori.nenepixel.core.domain.geometry.PixelY
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Each method is a separate instrumentation invocation, with host force-stop between stages. */
internal class DurableMvpJourneyTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()
    private val fixture = AcceptanceFixture()
    private val picker = AcceptanceDocumentsUi { composeRule.waitUntil(TIMEOUT, it) }
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var model: EditorRuntimeViewModel

    @Before
    fun requireIsolatedDevice() = fixture.requireIsolation()

    @Test
    fun createDrawAndSave() =
        withEditor {
            fixture.recordProcess("save")
            click("editor_file")
            click("editor_new_document")
            composeRule.onNodeWithTag("editor_document_width").performTextReplacement("8")
            composeRule.onNodeWithTag("editor_document_height").performTextReplacement("6")
            click("editor_create")
            awaitTag("editor_canvas_8_6")
            assertEquals(DocumentDirtyState.Clean, state().dirtyState)
            composeRule.onNodeWithTag(CANVAS).performTouchInput { swipe(point(1, 1), point(3, 1), 200L) }
            assertEquals(1L, state().documentState.revision.value)
            selectColor(5)
            touchPixel(5, 3)
            click("editor_eraser_tool")
            touchPixel(2, 1)
            val erased = state()
            assertPixels(recovered = false, revision = 3L)
            click("editor_undo")
            assertEquals(0xff0000ff.toInt(), state().documentState.snapshot.copyPackedRgba8888()[10])
            assertEquals(HistoryAvailability.UndoAndRedoAvailable, state().historyAvailability)
            click("editor_redo")
            assertEquals(erased.documentState, state().documentState)
            transformViewport()
            val transformed = state()
            assertEquals(erased.documentState, transformed.documentState)
            assertEquals(erased.historyAvailability, transformed.historyAvailability)
            assertNotEquals(erased.workspaceState.viewport.zoom, transformed.workspaceState.viewport.zoom)
            assertNotEquals(erased.workspaceState.viewport.center, transformed.workspaceState.viewport.center)
            save("saved.nenepixel")
            assertEquals(DocumentDirtyState.Clean, state().dirtyState)
            assertEquals(HistoryAvailability.UndoAvailable, state().historyAvailability)
            val bytes = fixture.readDocument("saved.nenepixel")
            fixture.verifyProject(bytes, state().documentState.id.value, 3L)
            fixture.writeNew("expected.nenepixel", bytes)
        }

    @Test
    fun restartLoadExportAndAutosave() =
        withEditor {
            fixture.recordProcess("load", "save")
            click("editor_file")
            click("editor_load")
            picker.open(fixture.name("saved.nenepixel"))
            awaitIdle()
            assertEquals(PersistenceLastOutcome.Loaded, model.persistenceOperations.value.lastOutcome)
            assertPixels(recovered = false, revision = 3L)
            assertEquals(fixture.savedId(), state().documentState.id.value)
            assertEquals(HistoryAvailability.None, state().historyAvailability)
            assertEquals(DocumentDirtyState.Clean, state().dirtyState)
            assertArrayEquals(fixture.local("expected.nenepixel").readBytes(), fixture.readDocument("saved.nenepixel"))
            val beforeExport = state()
            click("editor_file")
            click("editor_export_png")
            picker.create(fixture.name("export.png"))
            awaitIdle()
            assertEquals(PersistenceLastOutcome.PngExported, model.persistenceOperations.value.lastOutcome)
            assertEquals(beforeExport, state())
            verifyPng(fixture.readDocument("export.png"))
            selectColor(4)
            touchPixel(6, 4)
            assertPixels(recovered = true, revision = 4L)
            assertEquals(DocumentDirtyState.Dirty, state().dirtyState)
            composeRule.waitUntil(TIMEOUT) {
                model.autosaveStates.value.let {
                    it.pendingStateToken == null && !it.publishing && it.lastOutcome is AutosaveLastOutcome.Published
                }
            }
            fixture.writeNew(
                "candidate.bin",
                java.io.File(fixture.context.noBackupFilesDir, "nene-pixel-recovery-v1").readBytes(),
            )
        }

    @Test
    fun restartRecoverAndSaveAnotherFile() =
        withEditor {
            fixture.recordProcess("recover", "interrupt")
            awaitTag("editor_recovery_offer")
            assertEquals(
                16,
                state()
                    .documentState.size.width.value,
            )
            click("editor_recover_unsaved")
            awaitTag(CANVAS)
            assertPixels(recovered = true, revision = 4L)
            assertEquals(fixture.savedId(), state().documentState.id.value)
            assertEquals(DocumentDirtyState.Dirty, state().dirtyState)
            assertEquals(HistoryAvailability.None, state().historyAvailability)
            save("recovered.nenepixel")
            assertEquals(DocumentDirtyState.Clean, state().dirtyState)
            fixture.verifyProject(fixture.readDocument("recovered.nenepixel"), fixture.savedId(), 4L, recovered = true)
        }

    private fun withEditor(block: () -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use {
            scenario = it
            scenario.onActivity { activity ->
                model =
                    ViewModelProvider(
                        activity,
                        EditorRuntimeViewModel.factory(activity.application),
                    )[EditorRuntimeViewModel::class.java]
            }
            awaitIdle()
            block()
        }
    }

    private fun state(): EditorRuntimeState {
        composeRule.waitForIdle()
        lateinit var result: EditorRuntimeState
        scenario.onActivity { result = model.runtime.state }
        return result
    }

    private fun click(tag: String) {
        composeRule.onNodeWithTag(tag).assertIsEnabled().performClick()
    }

    private fun awaitTag(tag: String) {
        composeRule.waitUntil(TIMEOUT) { composeRule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().size == 1 }
    }

    private fun awaitIdle() {
        composeRule.waitUntil(TIMEOUT) { model.persistenceOperations.value.phase is PersistenceOperationPhase.Idle }
        composeRule.waitForIdle()
    }

    private fun save(suffix: String) {
        click("editor_file")
        click("editor_save_as")
        picker.create(fixture.name(suffix))
        awaitIdle()
        assertTrue(model.persistenceOperations.value.lastOutcome is PersistenceLastOutcome.Saved)
    }

    private fun selectColor(entry: Int) {
        click("editor_open_palette")
        click("editor_palette_entry_$entry")
    }

    private fun assertPixels(
        recovered: Boolean,
        revision: Long,
    ) {
        val document = state().documentState
        assertEquals(8, document.size.width.value)
        assertEquals(6, document.size.height.value)
        assertEquals(revision, document.revision.value)
        assertArrayEquals(fixture.expectedPixels(recovered), document.snapshot.copyPackedRgba8888())
    }

    private fun touchPixel(
        x: Int,
        y: Int,
    ) {
        composeRule.onNodeWithTag(CANVAS).performTouchInput {
            down(position = point(x, y))
            up()
        }
    }

    private fun transformViewport() {
        composeRule.onNodeWithTag(CANVAS).performTouchInput {
            down(0, point(2, 2))
            down(1, point(5, 3))
            moveTo(0, point(1, 1))
            moveTo(1, point(6, 4))
            moveTo(0, point(2, 1))
            moveTo(1, point(7, 4))
            up(0)
            up(1)
        }
    }

    private fun TouchInjectionScope.point(
        x: Int,
        y: Int,
    ): Offset {
        val current = model.controller.renderState
        val surface =
            (
                ViewportSurface.create(
                    width,
                    height,
                    composeRule.density.density.toDouble(),
                ) as ViewportValueResult.Created
            ).value
        val transform =
            (
                ViewportTransform.create(
                    current.snapshot.size,
                    surface,
                    current.viewport,
                ) as ViewportValueResult.Created
            ).value
        val position =
            PixelPosition.create(
                (PixelX.create(x) as DomainValueResult.Created).value,
                (PixelY.create(y) as DomainValueResult.Created).value,
            )
        val bounds = (transform.toSurfaceBounds(position) as ViewportMappingResult.Mapped).value
        return Offset(((bounds.left + bounds.right) / 2).toFloat(), ((bounds.top + bounds.bottom) / 2).toFloat())
    }

    private fun verifyPng(bytes: ByteArray) {
        val bitmap =
            checkNotNull(
                BitmapFactory.decodeByteArray(
                    bytes,
                    0,
                    bytes.size,
                    BitmapFactory.Options().apply {
                        inPremultiplied =
                            false
                    },
                ),
            )
        try {
            assertEquals(8, bitmap.width)
            assertEquals(6, bitmap.height)
            val argb = IntArray(48)
            bitmap.getPixels(argb, 0, 8, 0, 0, 8, 6)
            assertArrayEquals(fixture.expectedPixels(), argb.map { (it shl 8) or (it ushr 24) }.toIntArray())
        } finally {
            bitmap.recycle()
        }
    }

    private companion object {
        const val TIMEOUT = 60_000L
        const val CANVAS = "editor_canvas_8_6"
    }
}
