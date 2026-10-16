package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.application.document.command.CommandResult
import io.github.hideyukimori.nenepixel.core.application.document.command.SetFrameDurationCommand
import io.github.hideyukimori.nenepixel.core.application.editor.DocumentIdSource
import io.github.hideyukimori.nenepixel.core.application.editor.EditorRuntime
import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveRequestResult
import io.github.hideyukimori.nenepixel.core.application.persistence.AutosaveStateToken
import io.github.hideyukimori.nenepixel.core.application.persistence.EditorPersistenceWorkflow
import io.github.hideyukimori.nenepixel.core.application.persistence.LegacySourceCopyOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonExportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonExportPort
import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonImportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PaletteJsonImportPort
import io.github.hideyukimori.nenepixel.core.application.persistence.PalettePersistencePorts
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceLastOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistencePorts
import io.github.hideyukimori.nenepixel.core.application.persistence.PersistenceRequestResult
import io.github.hideyukimori.nenepixel.core.application.persistence.PngExportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PngExportPort
import io.github.hideyukimori.nenepixel.core.application.persistence.PngImportOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.PngImportPort
import io.github.hideyukimori.nenepixel.core.application.persistence.PngPersistencePorts
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectLoadOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectPersistencePorts
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectSaveOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectStoragePort
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryInitializationResult
import io.github.hideyukimori.nenepixel.core.application.persistence.RecoveryInspection
import io.github.hideyukimori.nenepixel.core.application.persistence.ReferenceImageOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ReferenceImagePort
import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayMemoryOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayMemoryPort
import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayPersistencePorts
import io.github.hideyukimori.nenepixel.core.application.persistence.UnderlayRecollection
import io.github.hideyukimori.nenepixel.core.application.workspace.underlay.RememberedUnderlay
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentId
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentImportSource
import io.github.hideyukimori.nenepixel.core.domain.document.DocumentState
import io.github.hideyukimori.nenepixel.core.domain.document.LegacyRgbaSource
import io.github.hideyukimori.nenepixel.core.domain.frame.FrameDuration
import io.github.hideyukimori.nenepixel.core.domain.validation.DomainValueResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Real adapter and workflow; only external storage capabilities and atomic access are controlled. */
internal class RecoveryWorkflowFixture {
    private val document = PersistenceTestValues.twoFramesDocument()
    private val dispatchers = TestCoroutineDispatchers()
    val runtime = EditorRuntime.create(document.size, document.definition, DocumentIdSource { document.id })
    val file = RecoveryWorkflowMemoryFile()
    val adapter = AndroidRecoveryRecordAdapter.create(file, dispatchers.io)
    val workflow = EditorPersistenceWorkflow.create(runtime, ports(), dispatchers.inline)

    suspend fun initialize() {
        assertEquals(RecoveryInitializationResult.Ready, workflow.initializeRecovery())
        val loaded = assertInstanceOf(PersistenceRequestResult.Completed::class.java, workflow.load())
        assertEquals(PersistenceLastOutcome.Loaded, loaded.outcome)
        assertEquals(document, runtime.state.documentState)
        assertEquals(RecoveryInspection.Retired(PersistenceTestValues.generation(1L)), adapter.inspect())
    }

    suspend fun verifiedFirstCapture(): RecoveryWorkflowCapture {
        initialize()
        val first = captureDuration(0, 110)
        publish(first, 2L)
        assertNull(workflow.autosave.value.pendingStateToken)
        return first
    }

    fun captureDuration(
        frameIndex: Int,
        milliseconds: Int,
    ): RecoveryWorkflowCapture {
        val frame = runtime.state.documentState.frames[frameIndex]
        val duration =
            when (val created = FrameDuration.create(milliseconds)) {
                is DomainValueResult.Created -> created.value
                is DomainValueResult.Rejected -> error("Invalid test duration: ${created.rejection}")
            }
        val result = runtime.execute(SetFrameDurationCommand.create(runtime.captureSource(), frame.id, duration))
        assertInstanceOf(CommandResult.Applied::class.java, result)
        return RecoveryWorkflowCapture(
            runtime.state.documentState,
            requireNotNull(workflow.autosave.value.pendingStateToken),
        )
    }

    suspend fun publish(
        capture: RecoveryWorkflowCapture,
        generation: Long,
    ) {
        val result = assertInstanceOf(AutosaveRequestResult.Published::class.java, workflow.publishLatestCapture())
        assertEquals(PersistenceTestValues.generation(generation), result.generation)
        assertEquals(capture.token, workflow.autosave.value.publishedStateToken)
        assertNull(workflow.autosave.value.publishingStateToken)
        assertCandidate(capture.document, generation)
    }

    suspend fun assertCandidate(
        expected: DocumentState,
        generation: Long,
    ) {
        assertEquals(
            RecoveryInspection.Candidate(
                PersistenceTestValues.generation(generation),
                DocumentImportSource.Current(expected),
            ),
            adapter.inspect(),
        )
    }

    private fun ports(): PersistencePorts =
        PersistencePorts(
            ProjectPersistencePorts(RecoveryWorkflowLoadPort(document), adapter),
            PngPersistencePorts(
                PngExportPort { PngExportOutcome.Cancelled },
                PngImportPort { PngImportOutcome.Cancelled },
            ),
            PalettePersistencePorts(
                PaletteJsonExportPort { PaletteJsonExportOutcome.Cancelled },
                PaletteJsonImportPort { PaletteJsonImportOutcome.Cancelled },
            ),
            UnderlayPersistencePorts(
                ReferenceImagePort { ReferenceImageOutcome.Cancelled },
                RecoveryWorkflowUnderlayPort,
            ),
        )
}

internal data class RecoveryWorkflowCapture(
    val document: DocumentState,
    val token: AutosaveStateToken,
)

private class RecoveryWorkflowLoadPort(
    private val document: DocumentState,
) : ProjectStoragePort {
    override suspend fun load(): ProjectLoadOutcome = ProjectLoadOutcome.Loaded(DocumentImportSource.Current(document))

    override suspend fun save(document: DocumentState): ProjectSaveOutcome = ProjectSaveOutcome.Cancelled

    override suspend fun copyLegacySource(source: LegacyRgbaSource): LegacySourceCopyOutcome =
        LegacySourceCopyOutcome.Cancelled
}

private object RecoveryWorkflowUnderlayPort : UnderlayMemoryPort {
    override suspend fun recall(document: DocumentId): UnderlayRecollection = UnderlayRecollection.Absent

    override suspend fun remember(
        document: DocumentId,
        underlay: RememberedUnderlay,
    ): UnderlayMemoryOutcome = UnderlayMemoryOutcome.Stored

    override suspend fun forget(document: DocumentId): UnderlayMemoryOutcome = UnderlayMemoryOutcome.Stored
}

internal enum class RecoveryWorkflowFault {
    NONE,
    WRITE,
    WRITE_CANCELLATION,
    SYNC,
    SYNC_AND_ROLLBACK,
    FINISH,
    READ_BACK,
}

/** This models controlled transaction outcomes, not the Android AtomicFile implementation. */
internal class RecoveryWorkflowMemoryFile : RecoveryAtomicFileAccess {
    @Volatile
    private var storedBytes: ByteArray? = null

    @Volatile
    private var readBackArmed: Boolean = false

    @Volatile
    var fault: RecoveryWorkflowFault = RecoveryWorkflowFault.NONE
        set(value) {
            field = value
            readBackArmed = false
        }

    @Volatile
    var writeGate: RecoveryWorkflowWriteGate? = null

    private val starts = AtomicInteger()
    private val rollbacks = AtomicInteger()
    private val finishes = AtomicInteger()
    val startCalls: Int get() = starts.get()
    val failCalls: Int get() = rollbacks.get()
    val finishCalls: Int get() = finishes.get()
    val bytes: ByteArray get() = requireNotNull(storedBytes).copyOf()

    override fun openRead(): InputStream {
        if (readBackArmed && fault == RecoveryWorkflowFault.READ_BACK) throw IOException("injected read-back")
        return ByteArrayInputStream(storedBytes?.copyOf() ?: throw FileNotFoundException("missing fixture record"))
    }

    override fun startWrite(): RecoveryWriteSession {
        starts.incrementAndGet()
        return Session()
    }

    private inner class Session : RecoveryWriteSession {
        private val staged = ByteArrayOutputStream()
        override val output: OutputStream =
            object : OutputStream() {
                override fun write(value: Int) {
                    beforeWrite()
                    staged.write(value)
                }

                override fun write(
                    source: ByteArray,
                    offset: Int,
                    length: Int,
                ) {
                    beforeWrite()
                    staged.write(source, offset, length)
                }
            }

        private fun beforeWrite() {
            writeGate?.arriveAndAwaitRelease()
            when (fault) {
                RecoveryWorkflowFault.WRITE -> throw IOException("injected write")

                RecoveryWorkflowFault.WRITE_CANCELLATION -> throw CancellationException("injected write cancellation")

                RecoveryWorkflowFault.NONE,
                RecoveryWorkflowFault.SYNC,
                RecoveryWorkflowFault.SYNC_AND_ROLLBACK,
                RecoveryWorkflowFault.FINISH,
                RecoveryWorkflowFault.READ_BACK,
                -> Unit
            }
        }

        override fun sync() {
            if (fault == RecoveryWorkflowFault.SYNC || fault == RecoveryWorkflowFault.SYNC_AND_ROLLBACK) {
                throw IOException("injected sync")
            }
        }

        override fun finish() {
            finishes.incrementAndGet()
            if (fault == RecoveryWorkflowFault.FINISH) throw IOException("injected finish before commit")
            storedBytes = staged.toByteArray()
            readBackArmed = fault == RecoveryWorkflowFault.READ_BACK
        }

        override fun fail() {
            rollbacks.incrementAndGet()
            if (fault == RecoveryWorkflowFault.SYNC_AND_ROLLBACK) throw IOException("injected rollback")
        }
    }
}

internal class RecoveryWorkflowWriteGate {
    private val entered = CompletableDeferred<Unit>()
    private val released = CountDownLatch(1)

    suspend fun awaitEntered() {
        withTimeout(WAIT_MILLIS) { entered.await() }
    }

    fun arriveAndAwaitRelease() {
        entered.complete(Unit)
        check(released.await(WAIT_MILLIS, TimeUnit.MILLISECONDS)) { "Fixture write gate timed out" }
    }

    fun release() {
        released.countDown()
    }

    private companion object {
        const val WAIT_MILLIS: Long = 5_000L
    }
}
