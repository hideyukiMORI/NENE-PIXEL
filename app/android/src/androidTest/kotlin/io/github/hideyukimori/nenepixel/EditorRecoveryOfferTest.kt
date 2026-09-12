package io.github.hideyukimori.nenepixel

import android.content.Context
import android.os.SystemClock
import android.util.AtomicFile
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.percentOffset
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hideyukimori.nenepixel.adapters.persistence.AndroidRecoveryRecordAdapter
import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryAvailability
import io.github.hideyukimori.nenepixel.core.application.editor.DocumentDirtyState
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryInspection
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRecordPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Device coverage of the ADR 0018 lifecycle flush and of the startup recovery offer. The activity is
 * launched by hand so that one session can be stopped and closed before the next one starts with a
 * new `ViewModel`, which is what makes the startup Candidate observable at all.
 */
internal class EditorRecoveryOfferTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Before
    fun clearRecoveryRecordBefore() {
        recoveryFile().delete()
    }

    @After
    fun clearRecoveryRecordAfter() {
        recoveryFile().delete()
    }

    @Test
    fun lifecycleStopPublishesTheLatestCaptureAsARecoveryCandidate() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeRule.waitForIdle()
            drawCanvasCenter()
            val drawn = documentPixels(scenario)

            scenario.moveToState(Lifecycle.State.CREATED)

            val candidate = awaitCandidate()
            assertEquals(1L, candidate.generation.value)
            assertEquals(1L, candidate.document.revision.value)
            assertArrayEquals(drawn, candidate.document.snapshot.copyPackedRgba8888())
        }
    }

    @Test
    fun startupOfferAdoptsTheCandidateAsUnsavedWorkWithoutHistory() {
        var drawn = IntArray(0)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeRule.waitForIdle()
            drawCanvasCenter()
            drawn = documentPixels(scenario)
            scenario.moveToState(Lifecycle.State.CREATED)
            awaitCandidate()
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeRule.waitForIdle()
            composeRule.waitUntil(OFFER_TIMEOUT_MILLIS) {
                composeRule
                    .onAllNodes(hasContentDescription(RECOVERY_OFFER_DESCRIPTION))
                    .fetchSemanticsNodes()
                    .size == 1
            }
            assertNotEquals(drawn.toList(), documentPixels(scenario).toList())

            composeRule.onNodeWithContentDescription(RECOVER_DESCRIPTION).performClick()
            composeRule.waitForIdle()

            scenario.onActivity { activity ->
                val state = activity.editorModel().runtime.state
                assertEquals(1L, state.documentState.revision.value)
                assertArrayEquals(drawn, state.documentState.snapshot.copyPackedRgba8888())
                assertEquals(HistoryAvailability.None, state.historyAvailability)
                assertEquals(DocumentDirtyState.Dirty, state.dirtyState)
            }
            composeRule.onNodeWithContentDescription(RECOVERY_OFFER_DESCRIPTION).assertDoesNotExist()
        }
    }

    private fun drawCanvasCenter() {
        composeRule
            .onNodeWithContentDescription(CANVAS_DESCRIPTION)
            .performTouchInput {
                down(position = percentOffset(CANVAS_CENTER_PERCENT, CANVAS_CENTER_PERCENT))
                up()
            }
        composeRule.waitForIdle()
    }

    private fun documentPixels(scenario: ActivityScenario<MainActivity>): IntArray {
        lateinit var pixels: IntArray
        scenario.onActivity { activity ->
            pixels =
                activity
                    .editorModel()
                    .runtime.state.documentState.snapshot
                    .copyPackedRgba8888()
        }
        return pixels
    }

    private fun awaitCandidate(): RecoveryInspection.Candidate {
        val probe = probe()
        val deadline = SystemClock.uptimeMillis() + OFFER_TIMEOUT_MILLIS
        var last: RecoveryInspection = RecoveryInspection.Missing
        while (SystemClock.uptimeMillis() < deadline) {
            last = runBlocking { probe.inspect() }
            if (last is RecoveryInspection.Candidate) {
                return last
            }
            SystemClock.sleep(POLL_MILLIS)
        }
        error("The lifecycle flush did not publish a recovery Candidate; last inspection was $last")
    }

    private fun probe(): RecoveryRecordPort =
        AndroidRecoveryRecordAdapter.create(AtomicFile(recoveryPath()), Dispatchers.IO.limitedParallelism(1))

    private fun recoveryFile(): AtomicFile = AtomicFile(recoveryPath())

    private fun recoveryPath(): File = File(targetContext().noBackupFilesDir, RECOVERY_FILE_NAME)

    private fun targetContext(): Context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun MainActivity.editorModel(): EditorRuntimeViewModel =
        ViewModelProvider(this, EditorRuntimeViewModel.factory(application))[EditorRuntimeViewModel::class.java]

    private companion object {
        const val RECOVERY_FILE_NAME: String = "nene-pixel-recovery-v1"
        const val CANVAS_DESCRIPTION: String = "16 by 16 pixel canvas"
        const val RECOVERY_OFFER_DESCRIPTION: String = "Recovery offer"
        const val RECOVER_DESCRIPTION: String = "Recover unsaved work"
        const val CANVAS_CENTER_PERCENT: Float = 0.5f
        const val OFFER_TIMEOUT_MILLIS: Long = 10_000L
        const val POLL_MILLIS: Long = 100L
    }
}
