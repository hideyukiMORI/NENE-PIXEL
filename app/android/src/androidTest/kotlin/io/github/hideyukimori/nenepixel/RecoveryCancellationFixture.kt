package io.github.hideyukimori.nenepixel

import android.os.Build
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hideyukimori.nenepixel.adapters.persistence.AndroidRecoveryRecordAdapter
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.command.SetFrameDurationCommand
import io.github.hideyukimori.nenepixel.core.application.editor.DocumentIdSource
import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveLastOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveRequestResult
import io.github.hideyukimori.nenepixel.core.application.persistence.EditorPersistenceWorkflow
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceLastOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationPhase
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceRequestResult
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGeneration
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGenerationResult
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryInitializationResult
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryInspection
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryStatus
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentImportSource
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameDuration
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/** Reuses #247's document and external ports; its admitted paths and partial-write stream are new. */
internal class RecoveryCancellationFixture(
    case: String,
) {
    private val document = RecoveryFaultDocument.create()
    private val dispatchers = TestCoroutineDispatchers()
    val directory = createDirectory(case)
    val file = RecoveryCancellationAtomicFile(File(directory, "synthetic-record"))
    val adapter = AndroidRecoveryRecordAdapter.create(file, dispatchers.io)
    val runtime = EditorRuntime.create(document.size, document.definition, DocumentIdSource { document.id })
    private val ports = RecoveryFaultPorts(document, adapter)
    val workflow = EditorPersistenceWorkflow.create(runtime, ports.ports, dispatchers.parallel)
    var caller: Deferred<AutosaveRequestResult>? = null
        private set
    lateinit var first: RecoveryFaultCapture
        private set
    lateinit var firstBytes: ByteArray
        private set

    suspend fun prepare() {
        assertEquals(RecoveryInitializationResult.Ready, workflow.initializeRecovery())
        val loaded = workflow.load() as PersistenceRequestResult.Completed
        assertEquals(PersistenceLastOutcome.Loaded, loaded.outcome)
        assertEquals(document, runtime.state.documentState)
        assertEquals(RecoveryInspection.Retired(generation(1L)), adapter.inspect())
        first = capture(0, 110)
        publish(first, 2L)
        firstBytes = file.baseFile.readBytes()
        record("c1-verified")
    }

    fun capture(
        frame: Int,
        milliseconds: Int,
    ): RecoveryFaultCapture {
        val duration =
            when (val value = FrameDuration.create(milliseconds)) {
                is DomainValueResult.Created -> value.value
                is DomainValueResult.Rejected -> error("Invalid synthetic duration")
            }
        val command =
            SetFrameDurationCommand.create(
                runtime.captureSource(),
                runtime.state.documentState.frames[frame]
                    .id,
                duration,
            )
        assertTrue(runtime.execute(command) is CommandResult.Applied)
        return RecoveryFaultCapture(
            runtime.state.documentState,
            requireNotNull(workflow.autosave.value.pendingStateToken),
        )
    }

    fun launchCaller(scope: CoroutineScope): Deferred<AutosaveRequestResult> {
        check(caller == null)
        return scope.async { workflow.publishLatestCapture() }.also { caller = it }
    }

    suspend fun assertHeld(
        publishing: RecoveryFaultCapture,
        pending: RecoveryFaultCapture,
    ) {
        val state = workflow.autosave.value
        assertEquals(publishing.token, state.publishingStateToken)
        assertEquals(pending.token, state.pendingStateToken)
        assertEquals(first.token, state.publishedStateToken)
        assertEquals(pending.document, runtime.state.documentState)
        assertFalse(requireNotNull(caller).isCompleted)
        assertEquals(3, file.startCalls)
        assertEquals(2, file.finishCalls)
        assertEquals(2, file.closeCalls)
        assertEquals(0, file.failCalls)
        assertEquals(0, file.throwCalls)
        assertArrayEquals(firstBytes, file.baseFile.readBytes())
        assertArrayEquals(firstBytes, file.heldBase(3).readBytes())
        val expected = file.request(3).readBytes()
        val partial = file.heldPartial(3).readBytes()
        assertTrue(partial.isNotEmpty() && partial.size < expected.size)
        assertArrayEquals(expected.copyOfRange(0, RecoveryCancellationAtomicFile.PREFIX_BYTES), partial)
        assertArrayEquals(partial, file.temporaryFile.readBytes())
        assertEquals(AutosaveRequestResult.Deferred, workflow.publishLatestCapture())
        assertEquals(3, file.startCalls)
    }

    suspend fun publish(
        capture: RecoveryFaultCapture,
        expectedGeneration: Long,
    ) {
        val result = withTimeout(WAIT_MILLIS) { workflow.publishLatestCapture() } as AutosaveRequestResult.Published
        assertEquals(expectedGeneration, result.generation.value)
        assertPublished(capture, expectedGeneration)
    }

    suspend fun assertPublished(
        capture: RecoveryFaultCapture,
        expectedGeneration: Long,
        pending: RecoveryFaultCapture? = null,
    ) {
        val state = workflow.autosave.value
        assertEquals(capture.token, state.publishedStateToken)
        assertEquals(pending?.token, state.pendingStateToken)
        assertNull(state.publishingStateToken)
        assertEquals(expectedGeneration, (state.lastOutcome as AutosaveLastOutcome.Published).generation.value)
        assertEquals((pending ?: capture).document, runtime.state.documentState)
        assertReleased()
        assertCandidate(capture, expectedGeneration)
        assertArrayEquals(file.request(file.startCalls).readBytes(), file.baseFile.readBytes())
        assertFalse(file.temporaryFile.exists())
        assertEquals(file.startCalls, file.closeCalls)
        assertEquals(file.startCalls, file.finishCalls + file.failCalls)
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

    suspend fun assertRolledBack(pending: RecoveryFaultCapture) {
        assertEquals(3, file.startCalls)
        assertEquals(1, file.throwCalls)
        assertEquals(1, file.failCalls)
        assertEquals(2, file.finishCalls)
        assertEquals(3, file.closeCalls)
        assertArrayEquals(firstBytes, file.baseFile.readBytes())
        assertFalse(file.temporaryFile.exists())
        assertEquals(pending.document, runtime.state.documentState)
        assertEquals(pending.token, workflow.autosave.value.pendingStateToken)
        assertEquals(first.token, workflow.autosave.value.publishedStateToken)
        assertEquals(AutosaveLastOutcome.Deferred, workflow.autosave.value.lastOutcome)
        assertReleased()
        assertCandidate(first, 2L)
    }

    fun assertReleased() {
        assertNull(workflow.autosave.value.publishingStateToken)
        assertEquals(PersistenceOperationPhase.Idle, workflow.operation.value.phase)
        assertEquals(RecoveryStatus.Clear, workflow.operation.value.recoveryStatus)
    }

    fun record(
        stage: String,
        detail: String = "",
    ) {
        val snapshots =
            listOf("", ".new").mapNotNull { suffix ->
                val source = File(file.baseFile.path + suffix)
                if (!source.isFile) {
                    null
                } else {
                    val saved = source.copyTo(File(directory, "$stage-record${suffix.ifEmpty { ".base" }}"))
                    val hash =
                        MessageDigest.getInstance("SHA-256").digest(saved.readBytes()).joinToString("") {
                            "%02x".format(Locale.ROOT, it)
                        }
                    "${saved.name}:bytes=${saved.length()}:sha256=$hash"
                }
            }
        val line =
            "RECOVERY_CANCELLATION $stage starts=${file.startCalls} finishes=${file.finishCalls} " +
                "rollbacks=${file.failCalls} throws=${file.throwCalls} closes=${file.closeCalls} " +
                "autosave=${workflow.autosave.value} operation=${workflow.operation.value} snapshots=$snapshots $detail"
        val output = File(directory, "$stage.txt")
        check(output.createNewFile())
        output.writeText("$line\n${runtime.state.documentState}\n")
        InstrumentationRegistry.getInstrumentation().sendStatus(
            2,
            Bundle().apply { putString("recoveryCancellationStage", line) },
        )
    }

    suspend fun close() {
        withContext(NonCancellable) {
            file.removeFault()
            ports.export.release()
            try {
                withTimeout(WAIT_MILLIS) { caller?.cancelAndJoin() }
            } finally {
                // Never close the shared descriptor while an owned writer could still use it.
                if (caller?.isCompleted != false) file.close()
            }
        }
    }

    private fun generation(value: Long): RecoveryGeneration =
        when (val result = RecoveryGeneration.create(value)) {
            is RecoveryGenerationResult.Created -> result.generation
            RecoveryGenerationResult.Rejected -> error("Invalid synthetic generation")
        }

    private fun createDirectory(case: String): File {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        check(arguments.getString("recoveryCancellationAdmission") == "isolated-api35-v1")
        check(Build.VERSION.SDK_INT == 35)
        check(instrumentation.targetContext.packageName == "io.github.hideyukimori.nenepixel")
        val run = requireNotNull(arguments.getString("recoveryCancellationRun"))
        check(Regex("[a-z0-9][a-z0-9-]{2,63}").matches(run))
        val root = File(instrumentation.targetContext.noBackupFilesDir, "issue-249-$run")
        check(root.isDirectory || root.mkdir())
        return File(root, case).also { check(it.mkdir()) { "Cancellation fixture already exists" } }
    }

    private companion object {
        const val WAIT_MILLIS: Long = 10_000L
    }
}
