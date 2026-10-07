package io.github.hideyukimori.nenepixel.measurement

import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hideyukimori.nenepixel.MainActivity
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
internal class P4LayerEditorRetentionMeasurementTest {
    private val compose = createEmptyComposeRule()

    @get:Rule
    val rules: TestRule = RuleChain.outerRule(Timeout.seconds(300)).around(compose)

    @Test
    fun measuresFiveCheckpointsOnTheActualEditor() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("p4LayerCollect") == "editor-retention-v1")
        val admission = P4LayerRunAdmission.read()
        val run = checkNotNull(arguments.getString("p4LayerMemoryRunIndex")?.takeIf(Regex("[1-5]")::matches)).toInt()
        val environment = P2AndroidMeasurementEnvironment.fromRunnerArguments()
        requireFreshPrivateWork()
        val journal = P4LayerMemoryJournal(admission, run, environment)
        journal.start()
        val initialPhysical = physical(environment, "layer-editor-start").also { it.assertInitialValidity() }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val editor = P4LayerEditorSession(compose, scenario)
            editor.retrieveActualModel()
            val fixture = editor.documents.stage(journal.fixtureName, P4LayerFixture.MAXIMUM)
            editor.newEmptyDocument()
            journal.capture(editor)
            editor.loadMaximum(fixture.name)
            journal.capture(editor)
            editor.holdLongPreview()
            journal.capture(editor)
            val published = editor.commit()
            journal.capture(editor)
            editor.undoRedoCycles(published)
            journal.capture(editor)
            physical(environment, "layer-editor-end").assertCompatibleWith(initialPhysical)
            journal.finish(fixture)
        }
    }

    private fun requireFreshPrivateWork() {
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.noBackupFilesDir
        for (name in listOf(
            "nene-pixel-recovery-v1",
            "nene-pixel-recovery-v1.new",
            "nene-pixel-recovery-v1.bak",
            "reference-underlays",
        )) {
            check(
                !File(directory, name).exists(),
            ) { "Phase host must preserve/quarantine prior work before this run: $name" }
        }
    }

    private fun physical(
        environment: P2AndroidMeasurementEnvironment,
        name: String,
    ): P2AndroidPhysicalCheckpoint =
        P2AndroidPhysicalCheckpointCapture.capture(
            environment.targetContext,
            P2AndroidPhysicalCheckpointCapture.defaultDisplay(environment.targetContext),
            name,
            sampleIndex = 0,
        )
}
