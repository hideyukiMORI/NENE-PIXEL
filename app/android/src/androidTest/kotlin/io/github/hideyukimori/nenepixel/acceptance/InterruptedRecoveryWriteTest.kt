package io.github.hideyukimori.nenepixel.acceptance

import android.util.AtomicFile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Host starts this in a fresh process without an Activity/runtime, then force-stops it. */
internal class InterruptedRecoveryWriteTest {
    @Test
    fun leaveUnfinishedTemporaryWriteBesideVerifiedCandidate() {
        val fixture = AcceptanceFixture()
        fixture.requireIsolation()
        fixture.recordProcess("interrupt", "load")
        val base = File(fixture.context.noBackupFilesDir, "nene-pixel-recovery-v1")
        val expected = fixture.local("candidate.bin").readBytes()
        assertArrayEquals(expected, base.readBytes())
        // Intentionally omit finishWrite/failWrite: the interruption leaves the framework .new.
        AtomicFile(base).startWrite().use { it.write(byteArrayOf(78, 69, 78, 69)) }
        assertTrue(File(base.path + ".new").exists())
        assertArrayEquals(expected, base.readBytes())
    }
}
