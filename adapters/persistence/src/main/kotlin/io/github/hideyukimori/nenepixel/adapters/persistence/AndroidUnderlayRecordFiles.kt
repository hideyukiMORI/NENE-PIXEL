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
        return input?.use { opened -> readAtMost(opened, maxLength + 1) }
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

    private fun readAtMost(
        input: InputStream,
        limit: Int,
    ): ByteArray {
        val buffer = ByteArray(limit)
        var count = 0
        var read = 0
        while (count < limit && read >= 0) {
            read = input.read(buffer, count, limit - count)
            count += maxOf(read, 0)
        }
        return if (count == limit) buffer else buffer.copyOf(count)
    }
}
