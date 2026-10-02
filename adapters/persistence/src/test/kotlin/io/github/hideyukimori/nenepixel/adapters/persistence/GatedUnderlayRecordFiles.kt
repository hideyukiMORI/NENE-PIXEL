package io.github.hideyukimori.nenepixel.adapters.persistence

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * [UnderlayRecordFiles] in memory whose first operation waits for [release]; [entered] opens when that first
 * operation has started. Operations are recorded from any thread.
 */
internal class GatedUnderlayRecordFiles : UnderlayRecordFiles {
    private val delegate = InMemoryUnderlayRecordFiles()
    private val recorded: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val gate = CountDownLatch(1)
    val entered: CountDownLatch = CountDownLatch(1)

    /** Every operation as `"<operation> <name>"`, oldest first. */
    val operations: List<String>
        get() = synchronized(recorded) { recorded.toList() }

    fun release() {
        gate.countDown()
    }

    override fun names(): List<String> {
        pass("names")
        return delegate.names()
    }

    override fun read(
        name: String,
        maxLength: Int,
    ): ByteArray? {
        pass("read $name")
        return delegate.read(name, maxLength)
    }

    override fun write(
        name: String,
        bytes: ByteArray,
    ) {
        pass("write $name")
        delegate.write(name, bytes)
    }

    override fun delete(name: String) {
        pass("delete $name")
        delegate.delete(name)
    }

    override fun length(name: String): Long = delegate.length(name)

    override fun usedAt(name: String): Long = delegate.usedAt(name)

    override fun markUsed(name: String) {
        pass("markUsed $name")
        delegate.markUsed(name)
    }

    private fun pass(operation: String) {
        val first = synchronized(recorded) { recorded.isEmpty().also { recorded += operation } }
        if (first) {
            entered.countDown()
            gate.await(GATE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }
    }

    private companion object {
        const val GATE_TIMEOUT_SECONDS: Long = 10L
    }
}
