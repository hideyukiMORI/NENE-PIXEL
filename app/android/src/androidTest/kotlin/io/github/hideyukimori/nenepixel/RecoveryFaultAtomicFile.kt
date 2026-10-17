package io.github.hideyukimori.nenepixel

import android.util.AtomicFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/** Ordinary operations use Android; only the named method boundary is faulted (#247). */
internal class RecoveryFaultAtomicFile(
    file: File,
) : AtomicFile(file),
    AutoCloseable {
    @Volatile
    var fault: RecoveryFaultPhase = RecoveryFaultPhase.NONE

    @Volatile
    private var readBackArmed = false

    @Volatile
    private var ownedStream: FileOutputStream? = null

    private val starts = AtomicInteger()
    private val reads = AtomicInteger()
    private val finishes = AtomicInteger()
    private val rollbacks = AtomicInteger()
    val startCalls: Int get() = starts.get()
    val readCalls: Int get() = reads.get()
    val finishCalls: Int get() = finishes.get()
    val failCalls: Int get() = rollbacks.get()

    override fun startWrite(): FileOutputStream {
        starts.incrementAndGet()
        val stream = super.startWrite()
        val result = if (fault == RecoveryFaultPhase.WRITE) RecoveryFaultWriteStream(stream) else stream
        ownedStream = result
        return result
    }

    override fun finishWrite(stream: FileOutputStream?) {
        finishes.incrementAndGet()
        if (fault == RecoveryFaultPhase.FINISH_BEFORE_COMMIT) {
            // Deliberate pre-commit method failure, not a claim about physical rename behavior.
            stream?.close()
            ownedStream = null
            throw IOException("#247 injected finish-before-commit")
        }
        super.finishWrite(stream)
        ownedStream = null
        baseFile.copyTo(finishSnapshot(finishCalls))
        readBackArmed = fault == RecoveryFaultPhase.READ_BACK_AFTER_COMMIT
    }

    override fun failWrite(stream: FileOutputStream?) {
        rollbacks.incrementAndGet()
        super.failWrite(stream)
        ownedStream = null
    }

    override fun openRead(): FileInputStream {
        reads.incrementAndGet()
        if (readBackArmed) {
            readBackArmed = false
            throw IOException("#247 injected read-back after real finish")
        }
        return super.openRead()
    }

    fun preserve(stage: String): String {
        val evidence = File(baseFile.parentFile, "evidence")
        check(evidence.isDirectory || evidence.mkdir())
        val snapshots =
            listOf("", ".new", ".bak").mapNotNull { suffix ->
                val source = File(baseFile.path + suffix)
                if (source.isFile) {
                    val destination = source.copyTo(File(evidence, "$stage-record${suffix.ifEmpty { ".base" }}"))
                    val bytes = destination.readBytes()
                    val hash =
                        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
                            "%02x".format(Locale.ROOT, it)
                        }
                    "evidence/${destination.name}:bytes=${bytes.size}:sha256=$hash"
                } else {
                    null
                }
            }
        return "starts=$startCalls reads=$readCalls finishes=$finishCalls rollbacks=$failCalls $snapshots"
    }

    /** Immutable bytes after the real finish returns; validity is checked through the real reader. */
    fun finishSnapshot(ordinal: Int): File = File(baseFile.parentFile, "finish-$ordinal.base")

    override fun close() {
        ownedStream?.close()
        ownedStream = null
    }
}

internal enum class RecoveryFaultPhase {
    NONE,
    WRITE,
    FINISH_BEFORE_COMMIT,
    READ_BACK_AFTER_COMMIT,
}

/** Shares the real Android stream's descriptor; neither creates nor commits another file. */
private class RecoveryFaultWriteStream(
    private val delegate: FileOutputStream,
) : FileOutputStream(delegate.fd) {
    override fun write(value: Int) {
        fail()
    }

    override fun write(bytes: ByteArray) {
        fail()
    }

    override fun write(
        bytes: ByteArray,
        offset: Int,
        length: Int,
    ) {
        fail()
    }

    override fun close() {
        try {
            delegate.close()
        } finally {
            super.close()
        }
    }

    private fun fail(): Nothing = throw IOException("#247 injected write before real sync/finish")
}
