package io.github.hideyukimori.nenepixel

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.hideyukimori.nenepixel.adapters.persistence.AndroidRecoveryRecordAdapter
import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.command.SetFrameDurationCommand
import io.github.hideyukimori.nenepixel.core.application.editor.DocumentIdSource
import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveLastOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.EditorPersistenceWorkflow
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceLastOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceOperationPhase
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceRequestResult
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGeneration
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryGenerationResult
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryInitializationResult
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryInspection
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentImportSource
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameDuration
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import io.github.hideyukimori.nenepixel.presentation.compose.editor.EditorController
import io.github.hideyukimori.nenepixel.presentation.compose.editor.EditorOperationAvailability
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import java.io.File

/** Actual Android writer and retained VM; only external content ports are synthetic (#251). */
internal class RecoveryProcessFixture(
    val admission: RecoveryProcessAdmission,
) {
    private val document = RecoveryFaultDocument.create()
    private val dispatchers = TestCoroutineDispatchers()
    val file = RecoveryCancellationAtomicFile(admission.recoveryPath)
    val adapter = AndroidRecoveryRecordAdapter.create(file, dispatchers.io.limitedParallelism(1))
    val runtime = EditorRuntime.create(document.size, document.definition, DocumentIdSource { document.id })
    val ports = RecoveryFaultPorts(document, adapter)
    val workflow = EditorPersistenceWorkflow.create(runtime, ports.ports, dispatchers.parallel)
    private val controller = EditorController.create(runtime)
    val callbacks =
        RecoveryProcessCallbacks(::attach) { RecoveryProcessStopObservation(file.startCalls, workflow.autosave.value) }
    val application = admission.context.applicationContext as Application
    lateinit var model: EditorRuntimeViewModel
        private set
    lateinit var first: RecoveryFaultCapture
        private set
    lateinit var second: RecoveryFaultCapture
        private set
    lateinit var firstBytes: ByteArray
        private set

    suspend fun prepare() {
        admission.prepare()
        assertEquals(RecoveryInitializationResult.Ready, workflow.initializeRecovery())
        val loaded = workflow.load() as PersistenceRequestResult.Completed
        assertEquals(PersistenceLastOutcome.Loaded, loaded.outcome)
        assertEquals(document, runtime.state.documentState)
        assertEquals(RecoveryInspection.Retired(generation(1L)), adapter.inspect())
        first = capture(0, 110)
        assertEquals(RecoveryProcessCase.BeforePublication.expectedDocument(), first.document)
        workflow.publishLatestCapture()
        assertPublished(first, 2L)
        firstBytes = file.baseFile.readBytes()
        File(admission.directory, "c1-record.bin").also {
            check(it.createNewFile())
            it.writeBytes(firstBytes)
        }
    }

    fun captureSecond() {
        second = capture(1, 260)
        assertEquals(RecoveryProcessCase.VerifiedCompletion.expectedDocument(), second.document)
        controller.synchronizeWithRuntime()
    }

    fun assertActivityModel(activity: MainActivity) {
        val actual =
            ViewModelProvider(activity, EditorRuntimeViewModel.factory(application))[EditorRuntimeViewModel::class.java]
        assertSame(model, actual)
        assertSame(runtime, actual.runtime)
        assertSame(controller, actual.controller)
        assertEquals(1, callbacks.attachmentCount)
    }

    fun assertBeforePublication() {
        assertTrue(workflow.operation.value.phase is PersistenceOperationPhase.Exporting)
        assertEquals(EditorOperationAvailability.Busy, model.operationAvailability.value)
        assertEquals(second.token, workflow.autosave.value.pendingStateToken)
        assertNull(workflow.autosave.value.publishingStateToken)
        assertEquals(first.token, workflow.autosave.value.publishedStateToken)
        assertEquals(second.document, runtime.state.documentState)
        assertEquals(2, file.startCalls)
        assertEquals(2, file.finishCalls)
        assertArrayEquals(firstBytes, file.baseFile.readBytes())
        assertTrue(ports.export.entered.isCompleted)
    }

    fun assertPublicationHadNotStartedBeforeStop() {
        assertTrue(requireNotNull(model.viewModelScope.coroutineContext[Job]).isActive)
        val observation = requireNotNull(callbacks.beforeStop)
        assertEquals(2, observation.startCalls)
        assertNull(observation.autosave.publishingStateToken)
        assertEquals(first.token, observation.autosave.publishedStateToken)
        assertEquals(second.token, observation.autosave.pendingStateToken)
    }

    fun assertPartialWrite() {
        assertEquals(second.token, workflow.autosave.value.publishingStateToken)
        assertEquals(second.token, workflow.autosave.value.pendingStateToken)
        assertEquals(first.token, workflow.autosave.value.publishedStateToken)
        assertEquals(second.document, runtime.state.documentState)
        assertEquals(3, file.startCalls)
        assertEquals(2, file.finishCalls)
        assertEquals(0, file.failCalls)
        assertArrayEquals(firstBytes, file.baseFile.readBytes())
        assertArrayEquals(firstBytes, file.heldBase(3).readBytes())
        val expected = file.request(3).readBytes()
        assertTrue(expected.size > RecoveryCancellationAtomicFile.PREFIX_BYTES)
        assertArrayEquals(
            expected.copyOfRange(0, RecoveryCancellationAtomicFile.PREFIX_BYTES),
            file.temporaryFile.readBytes(),
        )
        assertArrayEquals(file.heldPartial(3).readBytes(), file.temporaryFile.readBytes())
    }

    suspend fun awaitSecondPublished() {
        withTimeout(RecoveryProcessAdmission.GATE_MILLIS) {
            workflow.autosave.first { it.publishedStateToken == second.token && !it.publishing }
        }
        assertPublished(second, 3L)
        assertArrayEquals(file.request(3).readBytes(), file.baseFile.readBytes())
    }

    suspend fun release() {
        ports.export.release()
        file.removeFault()
        withTimeout(RecoveryProcessAdmission.GATE_MILLIS) { workflow.autosave.first { !it.publishing } }
    }

    private fun attach(activity: MainActivity) {
        controller.synchronizeWithRuntime()
        val factory =
            viewModelFactory {
                initializer { EditorRuntimeViewModel(runtime, controller, ProjectPickerBroker(), workflow) }
            }
        model = ViewModelProvider(activity, factory)[EditorRuntimeViewModel::class.java]
    }

    private suspend fun assertPublished(
        capture: RecoveryFaultCapture,
        expectedGeneration: Long,
    ) {
        assertEquals(capture.token, workflow.autosave.value.publishedStateToken)
        assertNull(workflow.autosave.value.pendingStateToken)
        assertNull(workflow.autosave.value.publishingStateToken)
        assertEquals(
            expectedGeneration,
            (workflow.autosave.value.lastOutcome as AutosaveLastOutcome.Published).generation.value,
        )
        assertEquals(
            RecoveryInspection.Candidate(
                generation(expectedGeneration),
                DocumentImportSource.Current(capture.document),
            ),
            adapter.inspect(),
        )
    }

    private fun capture(
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

    private fun generation(value: Long): RecoveryGeneration =
        when (val result = RecoveryGeneration.create(value)) {
            is RecoveryGenerationResult.Created -> result.generation
            RecoveryGenerationResult.Rejected -> error("Invalid synthetic generation")
        }
}
