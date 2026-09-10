package io.github.hideyukimori.nenepixel.adapters.persistence

import android.util.AtomicFile
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

internal interface RecoveryAtomicFileAccess {
    fun openRead(): InputStream

    fun startWrite(): RecoveryWriteSession
}

internal interface RecoveryWriteSession {
    val output: OutputStream

    fun sync()

    fun finish()

    fun fail()
}

internal class AndroidRecoveryAtomicFileAccess(
    private val atomicFile: AtomicFile,
) : RecoveryAtomicFileAccess {
    override fun openRead(): InputStream = atomicFile.openRead()

    override fun startWrite(): RecoveryWriteSession = AndroidRecoveryWriteSession(atomicFile, atomicFile.startWrite())
}

private class AndroidRecoveryWriteSession(
    private val atomicFile: AtomicFile,
    private val stream: FileOutputStream,
) : RecoveryWriteSession {
    override val output: OutputStream
        get() = stream

    override fun sync() {
        stream.fd.sync()
    }

    override fun finish() {
        atomicFile.finishWrite(stream)
    }

    override fun fail() {
        atomicFile.failWrite(stream)
    }
}
