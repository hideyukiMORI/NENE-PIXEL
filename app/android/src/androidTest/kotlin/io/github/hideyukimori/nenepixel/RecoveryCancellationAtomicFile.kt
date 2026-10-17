package io.github.hideyukimori.nenepixel

import android.util.AtomicFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Normal Android I/O, held only after a real prefix is written to the temporary file (#249). */
internal class RecoveryCancellationAtomicFile(
    file: File,
) : AtomicFile(file),
    AutoCloseable {
    private val starts = AtomicInteger()
    private val finishes = AtomicInteger()
    private val rollbacks = AtomicInteger()
    private val throws = AtomicInteger()
    private val closes = AtomicInteger()

    @Volatile
    private var ownedStream: FileOutputStream? = null

    @Volatile
    private var gate: RecoveryCancellationGate? = null

    val portCancellation = CancellationException("#249 port cancellation after actual partial write")
    val startCalls: Int get() = starts.get()
    val finishCalls: Int get() = finishes.get()
    val failCalls: Int get() = rollbacks.get()
    val throwCalls: Int get() = throws.get()
    val closeCalls: Int get() = closes.get()
    val temporaryFile: File get() = File(baseFile.path + ".new")

    fun hold(cancelFromPort: Boolean): RecoveryCancellationGate {
        check(gate == null)
        return RecoveryCancellationGate(cancelFromPort).also { gate = it }
    }

    fun removeFault() {
        gate?.release()
        gate = null
    }

    override fun startWrite(): FileOutputStream {
        val ordinal = starts.incrementAndGet()
        val delegate = super.startWrite()
        return RecoveryCancellationStream(delegate, ordinal, gate).also { ownedStream = it }
    }

    override fun finishWrite(stream: FileOutputStream?) {
        finishes.incrementAndGet()
        super.finishWrite(stream)
        ownedStream = null
    }

    override fun failWrite(stream: FileOutputStream?) {
        rollbacks.incrementAndGet()
        super.failWrite(stream)
        ownedStream = null
    }

    override fun openRead(): FileInputStream = super.openRead()

    fun request(ordinal: Int): File = File(baseFile.parentFile, "write-$ordinal-request.bin")

    fun heldBase(ordinal: Int): File = File(baseFile.parentFile, "write-$ordinal-held.base")

    fun heldPartial(ordinal: Int): File = File(baseFile.parentFile, "write-$ordinal-held.new")

    override fun close() {
        gate?.release()
        ownedStream?.close()
        ownedStream = null
    }

    /** The wrapper and original stream share the real descriptor; one guarded close owns both. */
    private inner class RecoveryCancellationStream(
        private val delegate: FileOutputStream,
        private val ordinal: Int,
        private val held: RecoveryCancellationGate?,
    ) : FileOutputStream(delegate.fd) {
        private val written = AtomicBoolean()
        private val closed = AtomicBoolean()

        override fun write(value: Int) = write(byteArrayOf(value.toByte()))

        override fun write(bytes: ByteArray) = write(bytes, 0, bytes.size)

        override fun write(
            bytes: ByteArray,
            offset: Int,
            length: Int,
        ) {
            check(written.compareAndSet(false, true)) { "Writer must supply one complete encoded record" }
            val expected = bytes.copyOfRange(offset, offset + length)
            val output = request(ordinal)
            check(output.createNewFile())
            output.writeBytes(expected)
            if (held == null) {
                delegate.write(bytes, offset, length)
                return
            }
            check(length > PREFIX_BYTES) { "A partial-write fixture needs a longer complete record" }
            delegate.write(bytes, offset, PREFIX_BYTES)
            baseFile.copyTo(heldBase(ordinal))
            temporaryFile.copyTo(heldPartial(ordinal))
            held.arriveAndAwaitRelease()
            if (held.cancelFromPort) {
                throws.incrementAndGet()
                throw portCancellation
            }
            delegate.write(bytes, offset + PREFIX_BYTES, length - PREFIX_BYTES)
        }

        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            closes.incrementAndGet()
            try {
                delegate.close()
            } finally {
                super.close()
            }
        }
    }

    companion object {
        const val PREFIX_BYTES: Int = 16
    }
}

internal class RecoveryCancellationGate(
    val cancelFromPort: Boolean,
) {
    private val entered = CompletableDeferred<Unit>()
    private val released = CountDownLatch(1)

    suspend fun awaitEntered() {
        withTimeout(WAIT_MILLIS) { entered.await() }
    }

    fun arriveAndAwaitRelease() {
        entered.complete(Unit)
        if (!released.await(WAIT_MILLIS, TimeUnit.MILLISECONDS)) {
            throw AssertionError("Partial-write gate timed out before explicit release")
        }
    }

    fun release() {
        released.countDown()
    }

    private companion object {
        const val WAIT_MILLIS: Long = 10_000L
    }
}
