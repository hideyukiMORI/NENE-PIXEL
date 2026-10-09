package io.github.hideyukimori.nenepixel.measurement

import android.os.Bundle
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hideyukimori.nenepixel.MainActivity
import io.github.hideyukimori.nenepixel.TestCoroutineDispatchers
import io.github.hideyukimori.nenepixel.adapters.persistence.AndroidProjectStorageAdapter
import io.github.hideyukimori.nenepixel.adapters.persistence.DocumentOutputFormat
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import kotlinx.coroutines.runBlocking
import org.junit.rules.Timeout
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.File

internal class P4LayerSafRun(
    private val compose: ComposeTestRule,
    private val admission: P4LayerRunAdmission,
) {
    private val dispatchers = TestCoroutineDispatchers()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val environment = P2AndroidMeasurementEnvironment.fromRunnerArguments()
    private val prefix = "p4-layer-saf-${admission.preflightSha256.take(12)}"
    private val names = "i89-145-${admission.preflightSha256.take(12)}-saf"
    private val output = P4LayerSafOutput.reserve(instrumentation.targetContext.filesDir, prefix)
    private val journal = P4LayerSafJournal()
    private val destinations = ArrayList<P4GrantedDocument>(25)
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var editor: P4LayerEditorSession
    private lateinit var document: DocumentState
    private lateinit var fixture: P4GrantedDocument
    private lateinit var initialPhysical: P2AndroidPhysicalCheckpoint

    fun collect() {
        val primary =
            try {
                bounded(300, ::prepare)
                bounded(60, ::measure)
                null
            } catch (failure: Throwable) {
                failure
            }
        val rows = journal.freeze()
        val failure =
            try {
                var result = primary
                bounded(60) { result = finish(primary, rows) }
                result
            } catch (reportFailure: Throwable) {
                combine(primary, reportFailure)
            }
        failure?.let { throw it }
    }

    private fun prepare() {
        check(environment.profileId == "NENE-P2-ALLDOCUBE-IPL80MP-A16-API36")
        check(!environment.emulatorDetection.isEmulator && !environment.auxiliaryEmulatorArgumentPresent)
        requireFreshWork()
        initialPhysical = physical("layer-saf-start").also { it.assertInitialValidity() }
        val activity = ActivityScenario.launch(MainActivity::class.java)
        scenario = activity
        editor = P4LayerEditorSession(compose, activity)
        editor.retrieveActualModel()
        fixture = editor.documents.stage("$names-source.nenepixel", P4LayerFixture.MAXIMUM)
        output.recordSetup("source", 0, identity(fixture))
        editor.loadMaximum(fixture.name)
        activity.onActivity { document = editor.model.runtime.state.documentState }
        prepareDestinations()
        editor.awaitQuiescent()
        val line = P4LayerSafIdentity.line(admission, fixture, environment.profileId)
        output.publishIdentity(line)
        instrumentation.sendStatus(3, Bundle().apply { putString("p4LayerSafIdentity", line) })
    }

    private fun prepareDestinations() {
        repeat(25) { ordinal ->
            val kind = if (ordinal < 5) "warmup" else "sample"
            val index = if (ordinal < 5) ordinal else ordinal - 5
            val destination =
                editor.documents.createEmpty(
                    "$names-$kind-${index + 1}.nenepixel",
                    DocumentOutputFormat.PROJECT,
                )
            check(destination.granteeUid == fixture.granteeUid && destination.providerUid == fixture.providerUid)
            destinations.add(destination)
            output.recordSetup(kind, index, identity(destination))
        }
    }

    private fun measure() {
        val picker = P4LayerSafPicker(destinations, journal)
        val adapter =
            AndroidProjectStorageAdapter.create(
                instrumentation.targetContext.contentResolver,
                picker,
                dispatchers.io,
            )
        runBlocking {
            destinations.forEachIndexed { ordinal, destination ->
                check(journal.isOpen() && picker.consumedCount() == ordinal)
                val start = System.nanoTime()
                val outcome = adapter.save(document)
                val elapsed = System.nanoTime() - start
                val sample =
                    P4LayerSafSample(
                        ordinal,
                        elapsed,
                        identity(destination),
                        P4LayerSafOutcome(
                            outcome,
                            picker.consumedCount() == ordinal + 1,
                        ),
                    )
                check(journal.append(sample))
                check(sample.accepted()) { "SAF sample invalid: ${sample.elapsedNanos}, $outcome" }
            }
        }
        journal.summarize()
    }

    private fun finish(
        primary: Throwable?,
        rows: List<String>,
    ): Throwable? {
        var failure = primary
        if (failure == null) {
            failure =
                attempt(failure) {
                    check(journal.isComplete())
                    destinations.forEach { editor.documents.verifyBytes(it, P4LayerFixture.MAXIMUM) }
                    physical("layer-saf-end").assertCompatibleWith(initialPhysical)
                }
        }
        failure = attempt(failure) { output.report(rows, failure == null && journal.isComplete()) }
        return attempt(failure) { scenario?.close() }
    }

    private fun identity(destination: P4GrantedDocument): P4LayerSafDestination =
        P4LayerSafDestination(destination.uri.toString(), destination.granteeUid, destination.providerUid)

    private fun requireFreshWork() {
        for (name in listOf(
            "nene-pixel-recovery-v1",
            "nene-pixel-recovery-v1.new",
            "nene-pixel-recovery-v1.bak",
            "reference-underlays",
        )) {
            check(!File(instrumentation.targetContext.noBackupFilesDir, name).exists())
        }
    }

    private fun physical(name: String): P2AndroidPhysicalCheckpoint =
        P2AndroidPhysicalCheckpointCapture.capture(
            environment.targetContext,
            P2AndroidPhysicalCheckpointCapture.defaultDisplay(environment.targetContext),
            name,
            sampleIndex = 0,
        )
}

private fun bounded(
    seconds: Long,
    operation: () -> Unit,
) {
    val statement =
        object : Statement() {
            override fun evaluate() = operation()
        }
    val description = Description.createTestDescription(P4LayerSafSaveMeasurementTest::class.java, "layer-saf")
    Timeout.seconds(seconds).apply(statement, description).evaluate()
}

private fun attempt(
    primary: Throwable?,
    operation: () -> Unit,
): Throwable? =
    try {
        operation()
        primary
    } catch (failure: Throwable) {
        combine(primary, failure)
    }

private fun combine(
    primary: Throwable?,
    failure: Throwable,
): Throwable = if (primary == null) failure else primary.also { if (it !== failure) it.addSuppressed(failure) }
