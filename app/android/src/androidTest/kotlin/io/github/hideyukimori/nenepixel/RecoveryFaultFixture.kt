package io.github.hideyukimori.nenepixel

import android.os.Build
import android.os.Bundle
import android.util.AtomicFile
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hideyukimori.nenepixel.adapters.persistence.AndroidRecoveryRecordAdapter
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.command.SetFrameDurationCommand
import io.github.hideyukimori.nenepixel.core.application.editor.DocumentIdSource
import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveLastOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveRequestResult
import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveStateToken
import io.github.hideyukimori.nenepixel.core.application.persistence.EditorPersistenceWorkflow
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceLastOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationPhase
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceRequestResult
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGeneration
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGenerationResult
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryInitializationResult
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryInspection
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRetirementFailure
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryRollbackOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryStatus
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentImportSource
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameDuration
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import kotlinx.coroutines.delay
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.io.File

/** Public adapter factory + real runtime/workflow. Private storage is uniquely admitted per run. */
internal class RecoveryFaultFixture(
    case: String,
) : AutoCloseable {
    private val document = RecoveryFaultDocument.create()
    private val dispatchers = TestCoroutineDispatchers()
    val directory = createDirectory(case)
    val file = RecoveryFaultAtomicFile(File(directory, "synthetic-record"))
    val adapter = AndroidRecoveryRecordAdapter.create(file, dispatchers.io)
    val runtime = EditorRuntime.create(document.size, document.definition, DocumentIdSource { document.id })
    val ports = RecoveryFaultPorts(document, adapter)
    val workflow = EditorPersistenceWorkflow.create(runtime, ports.ports, dispatchers.parallel)

    lateinit var first: RecoveryFaultCapture
        private set
    lateinit var second: RecoveryFaultCapture
        private set
    lateinit var firstBytes: ByteArray
        private set

    suspend fun prepare() {
        assertEquals(RecoveryInitializationResult.Ready, workflow.initializeRecovery())
        val loaded = workflow.load() as PersistenceRequestResult.Completed
        assertEquals(PersistenceLastOutcome.Loaded, loaded.outcome)
        assertEquals(document, runtime.state.documentState)
        assertEquals(RecoveryInspection.Retired(generation(1L)), adapter.inspect())
        first = captureDuration(0, 110)
        assertTrue(workflow.publishLatestCapture() is AutosaveRequestResult.Published)
        assertPublished(first, 2L)
        firstBytes = file.baseFile.readBytes()
        record("c1-verified")
    }

    fun captureSecond(fault: RecoveryFaultPhase): RecoveryFaultCapture {
        file.fault = fault
        second = captureDuration(1, 260)
        return second
    }

    suspend fun assertPublished(
        capture: RecoveryFaultCapture,
        expectedGeneration: Long,
    ) {
        val state = workflow.autosave.value
        assertEquals(capture.token, state.publishedStateToken)
        assertNull(state.pendingStateToken)
        assertNull(state.publishingStateToken)
        assertEquals(expectedGeneration, (state.lastOutcome as AutosaveLastOutcome.Published).generation.value)
        assertCandidate(capture, expectedGeneration)
    }

    suspend fun assertCandidate(
        capture: RecoveryFaultCapture,
        expectedGeneration: Long,
    ) {
        assertEquals(
            RecoveryInspection.Candidate(
                generation(expectedGeneration),
                DocumentImportSource.Current(capture.document),
            ),
            adapter.inspect(),
        )
    }

    suspend fun assertRetiredFinish(
        ordinal: Int,
        expectedGeneration: Long,
    ) {
        val snapshot = AndroidRecoveryRecordAdapter.create(AtomicFile(file.finishSnapshot(ordinal)), dispatchers.io)
        assertEquals(RecoveryInspection.Retired(generation(expectedGeneration)), snapshot.inspect())
    }

    fun assertFailed() {
        val outcome = workflow.autosave.value.lastOutcome as AutosaveLastOutcome.Failed
        assertEquals(RecoveryRetirementFailure.WRITE, outcome.failure)
        assertEquals(RecoveryRollbackOutcome.COMPLETED, outcome.rollback)
        assertArrayEquals(firstBytes, file.baseFile.readBytes())
        assertEquals(first.token, workflow.autosave.value.publishedStateToken)
        assertEquals(RecoveryStatus.Clear, workflow.operation.value.recoveryStatus)
        assertEquals(1, file.failCalls)
        assertPending()
    }

    fun assertUncertain(failure: RecoveryRetirementFailure) {
        val outcome = workflow.autosave.value.lastOutcome as AutosaveLastOutcome.Uncertain
        assertEquals(failure, outcome.failure)
        assertEquals(RecoveryRollbackOutcome.NOT_NEEDED, outcome.rollback)
        assertTrue(workflow.operation.value.recoveryStatus is RecoveryStatus.Unknown)
        assertNull(workflow.autosave.value.publishedStateToken)
        assertEquals(0, file.failCalls)
        assertPending()
    }

    /** Negative I/O observation only; publication completion always uses a projection/record oracle. */
    suspend fun observeNoAdditionalWrite() {
        val starts = file.startCalls
        delay(OBSERVATION_MILLIS)
        assertEquals(starts, file.startCalls)
        assertPending()
    }

    fun record(
        stage: String,
        visibleStatus: String = "",
    ) {
        val autosave = workflow.autosave.value
        val pendingIsSecond = ::second.isInitialized && autosave.pendingStateToken == second.token
        val line =
            "RECOVERY_FAULT $stage ${file.preserve(stage)} operation=${workflow.operation.value} " +
                "autosave=$autosave pendingIsC2=$pendingIsSecond " +
                "publishedIsC1=${::first.isInitialized && autosave.publishedStateToken == first.token} " +
                "publishedIsC2=${::second.isInitialized && autosave.publishedStateToken == second.token} " +
                "visibleStatus=$visibleStatus"
        val output = File(directory, "$stage.txt")
        check(output.createNewFile())
        output.writeText("$line\n${runtime.state.documentState}\n")
        InstrumentationRegistry.getInstrumentation().sendStatus(
            2,
            Bundle().apply { putString("recoveryFaultStage", line) },
        )
    }

    override fun close() {
        ports.export.release()
        file.close()
    }

    private fun assertPending() {
        assertEquals(second.document, runtime.state.documentState)
        assertEquals(second.token, workflow.autosave.value.pendingStateToken)
        assertNull(workflow.autosave.value.publishingStateToken)
        assertEquals(PersistenceOperationPhase.Idle, workflow.operation.value.phase)
    }

    private fun captureDuration(
        frameIndex: Int,
        milliseconds: Int,
    ): RecoveryFaultCapture {
        val duration =
            when (val result = FrameDuration.create(milliseconds)) {
                is DomainValueResult.Created -> result.value
                is DomainValueResult.Rejected -> error("Invalid synthetic duration")
            }
        val command =
            SetFrameDurationCommand.create(
                runtime.captureSource(),
                runtime.state.documentState.frames[frameIndex]
                    .id,
                duration,
            )
        assertTrue(runtime.execute(command) is CommandResult.Applied)
        return RecoveryFaultCapture(
            runtime.state.documentState,
            requireNotNull(workflow.autosave.value.pendingStateToken),
        )
    }

    private fun generation(value: Long) =
        when (val created = RecoveryGeneration.create(value)) {
            is RecoveryGenerationResult.Created -> created.generation
            RecoveryGenerationResult.Rejected -> error("Invalid synthetic generation")
        }

    private fun createDirectory(case: String): File {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        check(arguments.getString("recoveryFaultAdmission") == "isolated-api35-v1")
        check(Build.VERSION.SDK_INT == 35)
        val run = requireNotNull(arguments.getString("recoveryFaultRun"))
        check(Regex("[a-z0-9][a-z0-9-]{2,63}").matches(run))
        check(instrumentation.targetContext.packageName == "io.github.hideyukimori.nenepixel")
        val root = File(instrumentation.targetContext.noBackupFilesDir, "issue-247-$run")
        check(root.isDirectory || root.mkdir())
        return File(root, case).also { check(it.mkdir()) { "Recovery fault case directory already exists" } }
    }

    private companion object {
        const val OBSERVATION_MILLIS: Long = 250L
    }
}

internal data class RecoveryFaultCapture(
    val document: DocumentState,
    val token: AutosaveStateToken,
)
