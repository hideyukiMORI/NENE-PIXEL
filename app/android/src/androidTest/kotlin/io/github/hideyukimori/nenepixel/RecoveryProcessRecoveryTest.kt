package io.github.hideyukimori.nenepixel

import android.os.Process
import android.util.AtomicFile
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import io.github.hideyukimori.nenepixel.adapters.persistence.AndroidRecoveryRecordAdapter
import io.github.hideyukimori.nenepixel.core.application.document.history.HistoryAvailability
import io.github.hideyukimori.nenepixel.core.application.editor.DocumentDirtyState
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryInspection
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentImportSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import java.io.File

/** A separate instrumentation process, with no VM placement callback: production factory only. */
internal class RecoveryProcessRecoveryTest {
    private val compose = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(Timeout.seconds(60)).around(compose)

    @Test
    fun normalFactoryOffersAndAdoptsExpectedDocument() {
        val admission = RecoveryProcessAdmission()
        val priorPid = admission.priorPid()
        assertNotEquals(priorPid, Process.myPid())
        val journal = RecoveryProcessJournal(admission)
        val ready = journal.readReady(priorPid)
        assertFalse(File(admission.directory, "phase-timeout.json").exists())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitOffer()
            assertCandidate(admission)
            compose.onNodeWithTag("editor_recover_unsaved").performClick()
            compose.waitForIdle()
            scenario.onActivity { activity -> assertAdopted(activity, admission) }
            compose.onNodeWithTag("editor_recovery_offer").assertDoesNotExist()
            assertEquals(admission.case.expectedGeneration, ready.getLong("expectedGeneration"))
            journal.recovered(priorPid)
        }
    }

    private fun awaitOffer() {
        compose.waitUntil(RecoveryProcessAdmission.GATE_MILLIS) {
            compose.onAllNodes(hasTestTag("editor_recovery_offer")).fetchSemanticsNodes().size == 1
        }
    }

    private fun assertCandidate(admission: RecoveryProcessAdmission) {
        val probe =
            AndroidRecoveryRecordAdapter.create(AtomicFile(admission.recoveryPath), TestCoroutineDispatchers().io)
        val candidate = runBlocking { probe.inspect() } as RecoveryInspection.Candidate
        assertEquals(admission.case.expectedGeneration, candidate.generation.value)
        assertEquals(DocumentImportSource.Current(admission.case.expectedDocument()), candidate.source)
    }

    private fun assertAdopted(activity: MainActivity, admission: RecoveryProcessAdmission) {
        val model =
            ViewModelProvider(
                activity,
                EditorRuntimeViewModel.factory(activity.application),
            )[EditorRuntimeViewModel::class.java]
        val state = model.runtime.state
        assertEquals(admission.case.expectedDocument(), state.documentState)
        assertEquals(DocumentDirtyState.Dirty, state.dirtyState)
        assertEquals(HistoryAvailability.None, state.historyAvailability)
        assertEquals(2, state.documentState.frames.size)
        assertEquals(3, state.documentState.totalLayerCount)
    }
}
