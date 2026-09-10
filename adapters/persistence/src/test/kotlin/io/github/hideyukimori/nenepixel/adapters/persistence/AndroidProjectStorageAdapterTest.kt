package io.github.hideyukimori.nenepixel.adapters.persistence

import io.github.hideyukimori.nenepixel.core.application.persistence.PartialOutputCleanup
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectLoadOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectSaveOutcome
import io.github.hideyukimori.nenepixel.core.application.persistence.ProjectStorageFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

internal class AndroidProjectStorageAdapterTest {
    @Test
    fun `save writes closes and validates the same fresh output`() =
        runBlocking {
            val content = MemoryProjectContent()
            val adapter = adapter(content, InternalPickerResult.Selected(TestLocation))

            val result = adapter.save(PersistenceTestValues.minimalDocument)

            assertSame(ProjectSaveOutcome.Saved, result)
            assertTrue(content.outputClosed)
            assertTrue(content.inputClosed)
            assertEquals(1, content.openOutputCalls)
            assertEquals(1, content.openInputCalls)
            assertEquals(0, content.deleteCalls)
        }

    @Test
    fun `read back mismatch keeps primary failure and reports cleanup`() =
        runBlocking {
            val content = MemoryProjectContent(readBackMutation = { it[0] = (it[0].toInt() xor 1).toByte() })
            val adapter = adapter(content, InternalPickerResult.Selected(TestLocation))

            val result = adapter.save(PersistenceTestValues.minimalDocument)

            assertEquals(
                ProjectSaveOutcome.Failed(
                    ProjectStorageFailure.ReadBackMismatch,
                    PartialOutputCleanup.DELETED,
                ),
                result,
            )
            assertEquals(1, content.deleteCalls)
        }

    @Test
    fun `known oversize rejects before opening`() =
        runBlocking {
            val content = MemoryProjectContent(knownByteCount = 262_187L)
            val adapter = adapter(content, InternalPickerResult.Selected(TestLocation))

            val result = adapter.load()

            assertEquals(ProjectLoadOutcome.Failed(ProjectStorageFailure.ResourceLimitExceeded), result)
            assertEquals(0, content.openInputCalls)
        }

    @Test
    fun `write cancellation closes and deletes before rethrow`() {
        val output = CancellingOutputStream()
        val content = MemoryProjectContent(outputFactory = { output })
        val adapter = adapter(content, InternalPickerResult.Selected(TestLocation))

        assertThrows(CancellationException::class.java) {
            runBlocking { adapter.save(PersistenceTestValues.minimalDocument) }
        }
        assertTrue(output.closed)
        assertEquals(1, content.deleteCalls)
    }

    @Test
    fun `read cancellation closes before rethrow`() {
        val input = CancellingInputStream()
        val content = MemoryProjectContent(inputFactory = { input })
        val adapter = adapter(content, InternalPickerResult.Selected(TestLocation))

        assertThrows(CancellationException::class.java) {
            runBlocking { adapter.load() }
        }
        assertTrue(input.closed)
    }

    @Test
    fun `picker failure does not create output`() =
        runBlocking {
            val content = MemoryProjectContent()
            val pickerFailure =
                ProjectStorageFailure.PermissionDenied(
                    io.github.hideyukimori.nenepixel.core.application.persistence.ProjectTransportPhase.PICKER_RESULT,
                )
            val adapter = adapter(content, InternalPickerResult.Failed(pickerFailure))

            val result = adapter.save(PersistenceTestValues.minimalDocument)

            assertEquals(ProjectSaveOutcome.Failed(pickerFailure, PartialOutputCleanup.NOT_NEEDED), result)
            assertEquals(0, content.openOutputCalls)
        }

    private fun adapter(
        content: ProjectContentAccess,
        pickerResult: InternalPickerResult,
    ) = AndroidProjectStorageAdapter.create(
        content,
        FixedProjectPicker(pickerResult),
        Dispatchers.Unconfined,
    )
}

private data object TestLocation : ProjectLocation

private class FixedProjectPicker(
    private val result: InternalPickerResult,
) : ProjectPickerAccess {
    override suspend fun createDocument(suggestedName: String): InternalPickerResult = result

    override suspend fun openDocument(): InternalPickerResult = result
}

private class MemoryProjectContent(
    private val knownByteCount: Long? = null,
    private val inputFactory: (() -> InputStream)? = null,
    private val outputFactory: (() -> OutputStream)? = null,
    private val readBackMutation: (ByteArray) -> Unit = {},
) : ProjectContentAccess {
    private val stored = ByteArrayOutputStream()
    var openInputCalls: Int = 0
        private set
    var openOutputCalls: Int = 0
        private set
    var deleteCalls: Int = 0
        private set
    var inputClosed: Boolean = false
        private set
    var outputClosed: Boolean = false
        private set

    override fun knownByteCount(location: ProjectLocation): Long? = knownByteCount

    override fun openInput(location: ProjectLocation): InputStream {
        openInputCalls += 1
        val supplied = inputFactory?.invoke()
        if (supplied != null) return supplied
        val bytes = stored.toByteArray()
        readBackMutation(bytes)
        return object : ByteArrayInputStream(bytes) {
            override fun close() {
                inputClosed = true
                super.close()
            }
        }
    }

    override fun openOutput(location: ProjectLocation): OutputStream {
        openOutputCalls += 1
        val supplied = outputFactory?.invoke()
        if (supplied != null) return supplied
        return object : OutputStream() {
            override fun write(value: Int) {
                stored.write(value)
            }

            override fun write(
                bytes: ByteArray,
                offset: Int,
                length: Int,
            ) {
                stored.write(bytes, offset, length)
            }

            override fun close() {
                outputClosed = true
            }
        }
    }

    override fun delete(location: ProjectLocation): Int {
        deleteCalls += 1
        return 1
    }
}

private class CancellingOutputStream : OutputStream() {
    var closed: Boolean = false
        private set

    override fun write(value: Int): Unit = throw CancellationException("cancel write")

    override fun close() {
        closed = true
    }
}

private class CancellingInputStream : InputStream() {
    var closed: Boolean = false
        private set

    override fun read(): Int = throw CancellationException("cancel read")

    override fun close() {
        closed = true
    }
}
