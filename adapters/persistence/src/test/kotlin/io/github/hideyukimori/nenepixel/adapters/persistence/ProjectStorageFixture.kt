package io.github.hideyukimori.nenepixel.adapters.persistence

import kotlinx.coroutines.CancellationException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

internal data object TestLocation : ProjectLocation

internal class FixedProjectPicker(
    private val result: InternalPickerResult,
) : ProjectPickerAccess {
    override suspend fun createDocument(request: DocumentCreationRequest): InternalPickerResult = result

    override suspend fun openDocument(request: DocumentOpenRequest): InternalPickerResult = result
}

/** How [MemoryProjectContent] departs from plain in-memory storage: supplied streams and read-back corruption. */
internal class ProjectContentBehavior(
    val inputFactory: (() -> InputStream)? = null,
    val outputFactory: (() -> OutputStream)? = null,
    val readBackMutation: (ByteArray) -> Unit = {},
)

internal class MemoryProjectContent(
    initialBytes: ByteArray? = null,
    private val knownByteCount: Long? = null,
    private val behavior: ProjectContentBehavior = ProjectContentBehavior(),
) : ProjectContentAccess {
    private val stored = ByteArrayOutputStream()

    init {
        initialBytes?.let(stored::write)
    }

    var deleteResult: Int = 1
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

    fun storedBytes(): ByteArray = stored.toByteArray()

    override fun knownByteCount(location: ProjectLocation): Long? = knownByteCount

    override fun openInput(location: ProjectLocation): InputStream {
        openInputCalls += 1
        val supplied = behavior.inputFactory?.invoke()
        if (supplied != null) return supplied
        val bytes = stored.toByteArray()
        behavior.readBackMutation(bytes)
        return object : ByteArrayInputStream(bytes) {
            override fun close() {
                inputClosed = true
                super.close()
            }
        }
    }

    override fun openOutput(location: ProjectLocation): OutputStream {
        openOutputCalls += 1
        val supplied = behavior.outputFactory?.invoke()
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
        return deleteResult
    }
}

internal class CancellingOutputStream : OutputStream() {
    var closed: Boolean = false
        private set

    override fun write(value: Int): Unit = throw CancellationException("cancel write")

    override fun close() {
        closed = true
    }
}

internal class CancellingInputStream : InputStream() {
    var closed: Boolean = false
        private set

    override fun read(): Int = throw CancellationException("cancel read")

    override fun close() {
        closed = true
    }
}
