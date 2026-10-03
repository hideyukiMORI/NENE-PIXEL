package io.github.hideyukimori.nenepixel.adapters.persistence

import java.io.IOException

/**
 * [UnderlayRecordFiles] in memory for the store tests: a clock that advances by one on every write and
 * mark, writes that fail for the names in [failingWrites], and a record of every operation by name.
 */
internal class InMemoryUnderlayRecordFiles : UnderlayRecordFiles {
    private val contents = mutableMapOf<String, ByteArray>()
    private val times = mutableMapOf<String, Long>()
    private val recordedOperations = mutableListOf<String>()
    private var clock = 0L

    /** Names whose `write` throws [IOException] and keeps the previous content. */
    var failingWrites: Set<String> = emptySet()

    /** Every operation as `"<operation> <name>"`, oldest first. */
    val operations: List<String>
        get() = recordedOperations.toList()

    override fun names(): List<String> {
        recordedOperations += "names"
        return contents.keys.toList()
    }

    override fun read(
        name: String,
        maxLength: Int,
    ): ByteArray? {
        recordedOperations += "read $name"
        return contents[name]?.let { bytes -> bytes.copyOf(minOf(bytes.size, maxLength + 1)) }
    }

    override fun write(
        name: String,
        bytes: ByteArray,
    ) {
        recordedOperations += "write $name"
        if (name in failingWrites) {
            throw IOException("write of $name failed")
        }
        contents[name] = bytes.copyOf()
        times[name] = tick()
    }

    override fun delete(name: String) {
        recordedOperations += "delete $name"
        contents.remove(name)
        times.remove(name)
    }

    override fun length(name: String): Long = contents[name]?.size?.toLong() ?: 0L

    override fun usedAt(name: String): Long = times[name] ?: 0L

    override fun markUsed(name: String) {
        recordedOperations += "markUsed $name"
        if (name in contents) {
            times[name] = tick()
        }
    }

    /** Places [bytes] as [name] without recording an operation. */
    fun put(
        name: String,
        bytes: ByteArray,
    ) {
        contents[name] = bytes.copyOf()
        times[name] = tick()
    }

    /** Sets the last use of [name] without recording an operation. */
    fun setUsedAt(
        name: String,
        time: Long,
    ) {
        times[name] = time
    }

    /** The current content of [name], or null when there is no such file; not recorded. */
    fun contentOf(name: String): ByteArray? = contents[name]?.copyOf()

    private fun tick(): Long {
        clock += 1
        return clock
    }
}
