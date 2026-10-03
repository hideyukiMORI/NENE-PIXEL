package io.github.hideyukimori.nenepixel.adapters.persistence

import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

/**
 * The underlay memory records as files of [directory], each written through [AtomicFile] (ADR 0034).
 *
 * [names] also answers the leftovers of an interrupted [AtomicFile] write; the store deletes them, which is
 * safe because the adapter serializes every store call, so no write is running while it tidies.
 */
internal class AndroidUnderlayRecordFiles(
    private val directory: File,
) : UnderlayRecordFiles {
    override fun names(): List<String> = directory.list()?.toList().orEmpty()

    override fun read(
        name: String,
        maxLength: Int,
    ): ByteArray? {
        val input =
            try {
                atomicFile(name).openRead()
            } catch (_: FileNotFoundException) {
                // A failed open is the only absent-record signal AtomicFile exposes.
                null
            }
        return input?.use { opened -> readAtMost(opened, maxLength + 1, length(name)) }
    }

    override fun write(
        name: String,
        bytes: ByteArray,
    ) {
        if (!directory.mkdirs() && !directory.isDirectory) {
            throw IOException("cannot create the underlay memory directory")
        }
        val atomicFile = atomicFile(name)
        val stream = atomicFile.startWrite()
        try {
            stream.write(bytes)
            atomicFile.finishWrite(stream)
        } catch (failure: IOException) {
            atomicFile.failWrite(stream)
            throw failure
        }
    }

    override fun delete(name: String) {
        atomicFile(name).delete()
    }

    override fun length(name: String): Long = File(directory, name).length()

    override fun usedAt(name: String): Long = File(directory, name).lastModified()

    override fun markUsed(name: String) {
        // A refused timestamp only makes this work look older to the eviction; nothing else depends on it.
        File(directory, name).setLastModified(System.currentTimeMillis())
    }

    private fun atomicFile(name: String): AtomicFile = AtomicFile(File(directory, name))

    /**
     * Reads at most [limit] bytes. The buffer starts at the [expected] file length, capped at [limit], and grows
     * only when the stream has more bytes than that, so a file that changes length while it is read still answers
     * at most [limit] bytes.
     */
    private fun readAtMost(
        input: InputStream,
        limit: Int,
        expected: Long,
    ): ByteArray {
        var buffer = ByteArray(expected.coerceIn(0L, limit.toLong()).toInt())
        var count = fill(input, buffer, 0)
        var next = nextBeyond(input, buffer, count, limit)
        while (next >= 0) {
            buffer = buffer.copyOf(minOf(limit, maxOf(count * 2, GROWTH_BYTE_COUNT)))
            buffer[count] = next.toByte()
            count = fill(input, buffer, count + 1)
            next = nextBeyond(input, buffer, count, limit)
        }
        return if (count == buffer.size) buffer else buffer.copyOf(count)
    }

    /** Reads into [buffer] from [from] until it is full or the stream ends; answers the bytes now in it. */
    private fun fill(
        input: InputStream,
        buffer: ByteArray,
        from: Int,
    ): Int {
        var count = from
        var read = 0
        while (count < buffer.size && read >= 0) {
            read = input.read(buffer, count, buffer.size - count)
            count += maxOf(read, 0)
        }
        return count
    }

    /** Reads one more byte when [buffer] is full and below [limit]; answers -1 otherwise or at the end. */
    private fun nextBeyond(
        input: InputStream,
        buffer: ByteArray,
        count: Int,
        limit: Int,
    ): Int = if (count == buffer.size && count < limit) input.read() else -1

    private companion object {
        const val GROWTH_BYTE_COUNT: Int = 8_192
    }
}
